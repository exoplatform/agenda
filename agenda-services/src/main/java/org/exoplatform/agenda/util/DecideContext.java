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
package org.exoplatform.agenda.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The state of the decide step running on the current thread: the event rows
 * it locked, the events it read or wrote, and the side effects it queued for
 * after its transaction.
 * <p>
 * While a context is open, the event cache is neither read nor filled: a row
 * read inside the transaction is only recorded, and evicted once the
 * transaction ends. Broadcasts and notification dispatches are queued, and run
 * only once the transaction has committed, so that no listener and no other
 * thread sees the state of a transaction that is not committed yet.
 * <p>
 * Only the outermost decide step opens a context. The steps it reaches join
 * it, and the outermost step closes it in a {@code finally}: request and
 * listener threads are pooled.
 */
public final class DecideContext {

  private static final ThreadLocal<DecideContext> CURRENT        = new ThreadLocal<>();

  private final Set<Long>                         lockedEventIds = new HashSet<>();

  private final Set<Long>                         eventIds       = new LinkedHashSet<>();

  private final List<Runnable>                    effects        = new ArrayList<>();

  private DecideContext() {
  }

  /**
   * Opens a context on the current thread.
   *
   * @return the opened context
   * @throws IllegalStateException when a context is already open on the thread
   */
  public static DecideContext open() {
    if (CURRENT.get() != null) {
      throw new IllegalStateException("A decide context is already open on this thread");
    }
    DecideContext context = new DecideContext();
    CURRENT.set(context);
    return context;
  }

  /**
   * @return the context open on the current thread, or null
   */
  public static DecideContext current() {
    return CURRENT.get();
  }

  /**
   * Records an event read or written by the decide step running on the
   * current thread, if any, so that its cache entry is evicted once the step's
   * transaction ends.
   *
   * @param eventId event technical identifier
   */
  public static void recordEvent(long eventId) {
    DecideContext context = CURRENT.get();
    if (context != null) {
      context.eventIds.add(eventId);
    }
  }

  /**
   * Queues a side effect, a broadcast or a notification dispatch, for after the
   * transaction of the decide step running on the current thread.
   *
   * @param effect the side effect
   * @return true when it was queued, false when no decide step runs on the
   *         thread, in which case the caller runs it itself
   */
  public static boolean queue(Runnable effect) {
    DecideContext context = CURRENT.get();
    if (context == null) {
      return false;
    }
    context.effects.add(effect);
    return true;
  }

  /**
   * Queues one share of a side effect for after the transaction of the decide
   * step running on the current thread. The shares queued under equal keys
   * make one effect: it runs once, where the first of them was queued, on all
   * of them in the order they were queued. A share dropped with the nested
   * step that queued it takes no part in it.
   *
   * @param <S> the type of the shares
   * @param key what the shares are about
   * @param share this share
   * @param effect the effect of all the shares queued under the key
   * @return true when it was queued, false when no decide step runs on the
   *         thread, in which case the caller runs the effect of its share
   *         itself
   */
  public static <S> boolean queueMerged(Object key, S share, Consumer<List<S>> effect) {
    DecideContext context = CURRENT.get();
    if (context == null) {
      return false;
    }
    context.effects.add(new MergedShare<>(context, key, share, effect));
    return true;
  }

  /**
   * Removes this context from the current thread.
   */
  public void close() {
    if (CURRENT.get() == this) {
      CURRENT.remove();
    }
  }

  /**
   * Notes that the decide step locked an event row.
   *
   * @param eventId event technical identifier
   */
  public void addLockedEvent(long eventId) {
    lockedEventIds.add(eventId);
    eventIds.add(eventId);
  }

  /**
   * @param eventId event technical identifier
   * @return true when the outermost decide step locked that event row
   */
  public boolean isLocked(long eventId) {
    return lockedEventIds.contains(eventId);
  }

  /**
   * @return the events read or written, whose cache entries are evicted once
   *         the transaction ends
   */
  public Set<Long> getEventIds() {
    return Collections.unmodifiableSet(eventIds);
  }

  /**
   * @return the queued side effects, in the order they were queued
   */
  public List<Runnable> getEffects() {
    return Collections.unmodifiableList(effects);
  }

  /**
   * @return the number of side effects queued so far, the mark a nested step
   *         takes at its entry
   */
  public int getEffectCount() {
    return effects.size();
  }

  /**
   * Drops the side effects queued from a mark on: those of a nested step that
   * failed. The events it recorded stay, to be evicted.
   *
   * @param mark the number of side effects queued at the nested step's entry
   */
  public void dropEffectsFrom(int mark) {
    effects.subList(mark, effects.size()).clear();
  }
}
