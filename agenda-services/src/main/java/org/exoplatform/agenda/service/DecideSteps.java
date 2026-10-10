/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.service;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Runs the decide steps of the agenda — the writes of an answer, of an
 * attendee list, of a date of a series — in three parts.
 * <ul>
 * <li><b>Resolve</b>, done by the caller outside any transaction: the reads and
 * checks that need no lock, through the event cache, and the
 * {@link DecideTarget} the step acts on. It writes nothing.</li>
 * <li><b>Decide</b>, {@link #decide}: one JPA transaction, whose first read
 * locks the target's rows — the series of a date, else the event itself — once
 * the persistence context is cleared. Every other read of the step follows the
 * lock, and every write joins the transaction. A decide step reached while
 * another one runs on the thread joins it, and takes no lock of its own.</li>
 * <li><b>Publish</b>, after the commit or the rollback: the cache evictions of
 * every event the step read or wrote, then, when it succeeded, the broadcasts
 * and the notification dispatches it queued, in order.</li>
 * </ul>
 * Its transaction is {@code @ExoTransactional}, which the Architects Lead
 * allows for the decide steps only: {@code @ContainerTransactional} opens no
 * JPA transaction, and a row lock lives in one.
 */
class DecideSteps {

  private static final Log         LOG = ExoLogger.getLogger(DecideSteps.class);

  private final AgendaEventStorage eventStorage;

  DecideSteps(AgendaEventStorage eventStorage) {
    this.eventStorage = eventStorage;
  }

  /**
   * @param event the event a decide step acts on, as resolved
   * @return its target: the series row for a date of a series, the event row
   *         otherwise; the event checked under the lock to still be what the
   *         caller resolved
   */
  static DecideTarget targetOf(Event event) {
    long parentId = Math.max(event.getParentId(), 0);
    return new DecideTarget(List.of(parentId > 0 ? parentId : event.getId()), event.getId(), parentId);
  }

  /**
   * @param seriesIds the series rows a decide step locks, each acted on as a
   *          whole
   * @return the target locking them, in ascending identifier order
   */
  static DecideTarget targetOfSeries(long... seriesIds) {
    return new DecideTarget(Arrays.stream(seriesIds).boxed().toList(), 0, 0);
  }

  /**
   * Runs a decide step on its target. Outermost, it refuses a call made inside
   * a transaction it did not begin, since its lock would not be that
   * transaction's first read; begins the transaction and locks the target as
   * its first read; and publishes once the transaction has ended. Nested in
   * another decide step, it joins it, checking that its target is among the
   * rows already locked.
   *
   * @param <T> the step's result type
   * @param <X1> a checked exception the step may throw
   * @param <X2> another checked exception the step may throw
   * @param target the rows the step locks and the event it acts on
   * @param body the step's reads and writes
   * @return the step's result
   * @throws X1 when the step throws it
   * @throws X2 when the step throws it
   * @throws ResolveAgainException when the target's rows are not what the
   *           caller resolved: the caller resolves again, once
   */
  <T, X1 extends Exception, X2 extends Exception> T decide(DecideTarget target, DecideBody<T, X1, X2> body) throws X1, X2 {
    DecideContext context = DecideContext.current();
    if (context != null) {
      return decideNested(context, target, body);
    }
    if (isTransactionActive()) {
      throw new IllegalStateException("A decide step was called inside a transaction it did not begin, where its lock "
          + "would not be the transaction's first read");
    }
    context = DecideContext.open();
    boolean decided = false;
    try {
      T result = decideInTransaction(context, target, body);
      decided = true;
      return result;
    } finally {
      context.close();
      publish(context, decided);
    }
  }

  /**
   * Runs a resolve and its decide step, and both again once when the decide
   * step finds that the rows resolved changed meanwhile. A second change
   * propagates as a {@link ResolveAgainException}.
   *
   * @param <T> the step's result type
   * @param <X1> a checked exception the step may throw
   * @param <X2> another checked exception the step may throw
   * @param step the resolve and its decide step
   * @return the step's result
   * @throws X1 when the step throws it
   * @throws X2 when the step throws it
   */
  <T, X1 extends Exception, X2 extends Exception> T resolvingAgainOnce(DecideBody<T, X1, X2> step) throws X1, X2 {
    try {
      return step.decide();
    } catch (ResolveAgainException e) {
      LOG.debug("Resolving a decide step again: {}", e.getMessage());
      return step.decide();
    }
  }

  @ExoTransactional
  private <T, X1 extends Exception, X2 extends Exception> T decideInTransaction(DecideContext context,
                                                                               DecideTarget target,
                                                                               DecideBody<T, X1, X2> body) throws X1, X2 {
    try {
      // Recorded first, so that the cache entries the caller resolved from are
      // evicted when the step ends, whether it acts or has them resolved again
      target.lockedEventIds().forEach(DecideContext::recordEvent);
      if (target.eventId() > 0) {
        DecideContext.recordEvent(target.eventId());
      }
      if (!eventStorage.lockEvents(target.lockedEventIds().stream().mapToLong(Long::longValue).toArray())) {
        throw new ResolveAgainException("An event of " + target.lockedEventIds() + " no longer exists");
      }
      target.lockedEventIds().forEach(context::addLockedEvent);
      if (target.eventId() > 0) {
        Event event = eventStorage.getEventById(target.eventId());
        if (event == null || Math.max(event.getParentId(), 0) != target.parentId()) {
          throw new ResolveAgainException("Event " + target.eventId() + " is no longer an event of " + target.parentId());
        }
      }
      T result = body.decide();
      if (isRollbackOnly()) {
        // A failure the body caught marked the transaction, which the aspect
        // rolls back without a word: its writes are lost, and so is the step
        throw new IllegalStateException("A failure caught during the decide step marked its transaction for rollback");
      }
      return result;
    } catch (Throwable e) { // NOSONAR the transaction rolls back on every failure, and the failure goes on
      markRollbackOnly();
      throw e;
    }
  }

  private <T, X1 extends Exception, X2 extends Exception> T decideNested(DecideContext context,
                                                                        DecideTarget target,
                                                                        DecideBody<T, X1, X2> body) throws X1, X2 {
    for (long eventId : target.lockedEventIds()) {
      if (!context.isLocked(eventId)) {
        throw new IllegalStateException("A nested decide step reached event " + eventId
            + ", which the outermost decide step did not lock");
      }
    }
    int mark = context.getEffectCount();
    try {
      return body.decide();
    } catch (Throwable e) { // NOSONAR the failed step's side effects are dropped, and the failure goes on
      context.dropEffectsFrom(mark);
      throw e;
    }
  }

  void publish(DecideContext context, boolean decided) {
    try {
      eventStorage.evictEvents(context.getEventIds());
    } catch (RuntimeException e) {
      LOG.warn("Error evicting events {} from the cache", context.getEventIds(), e);
    }
    if (decided) {
      for (Runnable effect : context.getEffects()) {
        try {
          effect.run();
        } catch (RuntimeException e) {
          LOG.warn("Error running a side effect of a decide step", e);
        }
      }
    }
  }

  private boolean isTransactionActive() {
    EntityManager entityManager = getEntityManager();
    return entityManager != null && entityManager.getTransaction().isActive();
  }

  private boolean isRollbackOnly() {
    EntityManager entityManager = getEntityManager();
    return entityManager != null && entityManager.getTransaction().isActive()
        && entityManager.getTransaction().getRollbackOnly();
  }

  private void markRollbackOnly() {
    EntityManager entityManager = getEntityManager();
    if (entityManager != null && entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().setRollbackOnly();
    }
  }

  /**
   * @return the thread's entity manager, found the way
   *         {@code ExoTransactionalAspect} finds it to begin the step's
   *         transaction, or null when there is none
   */
  private EntityManager getEntityManager() {
    EntityManagerService entityManagerService = ExoContainerContext.getService(EntityManagerService.class);
    if (entityManagerService == null) {
      PortalContainer container = PortalContainer.getInstanceIfPresent();
      entityManagerService = container == null ? null : container.getComponentInstanceOfType(EntityManagerService.class);
    }
    return entityManagerService == null ? null : entityManagerService.getEntityManager();
  }
}
