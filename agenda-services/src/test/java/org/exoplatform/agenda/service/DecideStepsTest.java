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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;

/**
 * The parts of a decide step that need no transaction: a step nested in
 * another one, the refusal of a step called inside a transaction it did not
 * begin, and the publication once the transaction has ended. The outermost
 * step, which begins the transaction and takes the lock, is tested in the
 * container ({@code AgendaDecideStepTest}).
 */
class DecideStepsTest {

  private final AgendaEventStorage eventStorage = mock(AgendaEventStorage.class);

  private final DecideSteps        decideSteps  = new DecideSteps(eventStorage);

  /**
   * Leaves no context on the test thread.
   */
  @AfterEach
  void closeContext() {
    DecideContext context = DecideContext.current();
    if (context != null) {
      context.close();
    }
  }

  /**
   * A step on a date of a series locks the series' row, which every date of
   * the series shares, and checks under the lock that the date still belongs
   * to it.
   */
  @Test
  void aStepOnADateLocksItsSeries() {
    Event date = new Event();
    date.setId(5);
    date.setParentId(1);

    DecideTarget target = DecideSteps.targetOf(date);

    assertEquals(List.of(1L), target.lockedEventIds());
    assertEquals(5, target.eventId());
    assertEquals(1, target.parentId());
  }

  /**
   * A step on a series, or on an event of no series, locks the event's own
   * row, and checks under the lock that it still belongs to no series.
   */
  @Test
  void aStepOnAnEventOfNoSeriesLocksItsOwnRow() {
    Event event = new Event();
    event.setId(5);

    DecideTarget target = DecideSteps.targetOf(event);

    assertEquals(List.of(5L), target.lockedEventIds());
    assertEquals(5, target.eventId());
    assertEquals(0, target.parentId());
  }

  /**
   * A nested step never reaches a row its outermost step did not lock: a row
   * locked later in the same transaction would be read from a snapshot fixed
   * before its lock.
   */
  @Test
  void aNestedStepOnARowTheOutermostStepDidNotLockIsRefused() {
    DecideContext.open().addLockedEvent(1);
    AtomicBoolean ran = new AtomicBoolean();

    assertThrows(IllegalStateException.class, () -> decideSteps.decide(DecideSteps.targetOfSeries(2), () -> {
      ran.set(true);
      return null;
    }));
    assertFalse(ran.get(), "the nested step ran on a row nobody locked");
  }

  /**
   * A nested step on a date of the locked series joins the outermost step: it
   * takes no lock of its own, and clears nothing.
   */
  @Test
  void aNestedStepOnADateOfTheLockedSeriesJoinsTheOutermostStep() {
    DecideContext.open().addLockedEvent(1);
    Event date = new Event();
    date.setId(5);
    date.setParentId(1);

    assertEquals("decided", decideSteps.decide(DecideSteps.targetOf(date), () -> "decided"));
    verify(eventStorage, never()).lockEvents(any());
  }

  /**
   * An outermost step looks for the thread's transaction where its
   * {@code @ExoTransactional} would join it, through the portal container's
   * persistence service, also on a thread no container is bound to, whose
   * top container has no such service: called inside that transaction, it
   * is refused before it locks anything.
   */
  @Test
  void aStepCalledInsideATransactionOnAThreadWithNoContainerIsRefused() {
    EntityManagerService entityManagerService = mock(EntityManagerService.class);
    EntityManager entityManager = mock(EntityManager.class);
    EntityTransaction transaction = mock(EntityTransaction.class);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    Event event = new Event();
    event.setId(1);

    try (MockedStatic<ExoContainerContext> containerContext = mockStatic(ExoContainerContext.class)) {
      containerContext.when(ExoContainerContext::getCurrentContainerIfPresent).thenReturn(mock(ExoContainer.class));
      containerContext.when(() -> ExoContainerContext.getService(EntityManagerService.class)).thenReturn(entityManagerService);

      IllegalStateException refusal = assertThrows(IllegalStateException.class,
                                                   () -> decideSteps.decide(DecideSteps.targetOf(event), () -> "decided"));

      assertTrue(refusal.getMessage().contains("inside a transaction it did not begin"), refusal.getMessage());
    }
    verify(eventStorage, never()).lockEvents(any());
  }

  /**
   * A nested step that fails, and whose failure its caller catches, loses its
   * own broadcasts and dispatches; those queued before it stay, and so do the
   * events it read, which are still evicted.
   */
  @Test
  void aFailedNestedStepDropsItsOwnSideEffectsOnly() {
    DecideContext context = DecideContext.open();
    context.addLockedEvent(1);
    Runnable before = mock(Runnable.class);
    DecideContext.queue(before);

    assertThrows(IllegalArgumentException.class, () -> decideSteps.decide(DecideSteps.targetOfSeries(1), () -> {
      DecideContext.queue(mock(Runnable.class));
      DecideContext.recordEvent(8);
      throw new IllegalArgumentException("failed after a write");
    }));

    assertEquals(List.of(before), context.getEffects());
    assertTrue(context.getEventIds().contains(8L), "an event the failed step read is no longer evicted");
  }

  /**
   * The shares queued under one key make one effect, run once where the first
   * of them was queued, on all of them in the order they were queued; the
   * shares of another key make another.
   */
  @Test
  void theSharesOfOneKeyMakeOneEffect() {
    DecideContext context = DecideContext.open();
    List<List<String>> runs = new ArrayList<>();
    DecideContext.queueMerged("first", "first-1", runs::add);
    DecideContext.queueMerged("second", "second-1", runs::add);
    DecideContext.queueMerged("first", "first-2", runs::add);

    context.getEffects().forEach(Runnable::run);

    assertEquals(List.of(List.of("first-1", "first-2"), List.of("second-1")), runs);
  }

  /**
   * A share queued by a nested step that fails, and whose failure its caller
   * catches, takes no part in the effect of its key.
   */
  @Test
  void aShareDroppedWithItsNestedStepTakesNoPartInTheEffect() {
    DecideContext context = DecideContext.open();
    context.addLockedEvent(1);
    List<List<String>> runs = new ArrayList<>();
    DecideContext.queueMerged("key", "kept", runs::add);

    assertThrows(IllegalArgumentException.class, () -> decideSteps.decide(DecideSteps.targetOfSeries(1), () -> {
      DecideContext.queueMerged("key", "dropped", runs::add);
      throw new IllegalArgumentException("failed after a promotion");
    }));
    context.getEffects().forEach(Runnable::run);

    assertEquals(List.of(List.of("kept")), runs);
  }

  /**
   * When the first share of a key is dropped with the nested step that queued
   * it, the effect runs once, from the first share kept.
   */
  @Test
  void aDroppedFirstShareLeavesTheEffectToTheShareKept() {
    DecideContext context = DecideContext.open();
    context.addLockedEvent(1);
    List<List<String>> runs = new ArrayList<>();

    assertThrows(IllegalArgumentException.class, () -> decideSteps.decide(DecideSteps.targetOfSeries(1), () -> {
      DecideContext.queueMerged("key", "dropped", runs::add);
      throw new IllegalArgumentException("failed after a promotion");
    }));
    DecideContext.queueMerged("key", "kept", runs::add);
    context.getEffects().forEach(Runnable::run);

    assertEquals(List.of(List.of("kept")), runs);
  }

  /**
   * The publication evicts every event the step read or wrote before it runs
   * any broadcast, so that a listener reading an event back reads it from the
   * database.
   */
  @Test
  void thePublicationEvictsBeforeItRunsTheSideEffects() {
    DecideContext context = DecideContext.open();
    context.addLockedEvent(1);
    Runnable effect = mock(Runnable.class);
    DecideContext.queue(effect);
    context.close();

    decideSteps.publish(context, true);

    InOrder inOrder = inOrder(eventStorage, effect);
    inOrder.verify(eventStorage).evictEvents(Set.of(1L));
    inOrder.verify(effect).run();
  }

  /**
   * A step that failed publishes its evictions, and none of its broadcasts or
   * dispatches.
   */
  @Test
  void aFailedStepPublishesItsEvictionsOnly() {
    DecideContext context = DecideContext.open();
    context.addLockedEvent(1);
    Runnable effect = mock(Runnable.class);
    DecideContext.queue(effect);
    context.close();

    decideSteps.publish(context, false);

    verify(eventStorage).evictEvents(Set.of(1L));
    verify(effect, never()).run();
  }

  /**
   * A side effect that fails does not keep the next ones from running.
   */
  @Test
  void aFailingSideEffectDoesNotStopTheNextOnes() {
    DecideContext context = DecideContext.open();
    Runnable failing = mock(Runnable.class);
    doThrow(new IllegalStateException("listener failure")).when(failing).run();
    Runnable next = mock(Runnable.class);
    DecideContext.queue(failing);
    DecideContext.queue(next);
    context.close();

    decideSteps.publish(context, true);

    verify(next).run();
  }
}
