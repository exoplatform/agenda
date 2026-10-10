/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.storage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.cached.CachedAgendaEventStorage;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.concurrent.ConcurrentFIFOExoCache;

/**
 * Tests the event-cache eviction of {@link CachedAgendaEventStorage} against a
 * real cache instance (never a mock): a drift between the cache key/selector
 * and the eviction would be a silent no-op that a mock-based test cannot
 * catch, and would let moved events keep serving their old calendar
 * membership from the cache.
 */
public class CachedAgendaEventStorageTest {

  private CachedAgendaEventStorage agendaEventStorage;

  private EventDAO                 eventDAO;

  /**
   * Builds the cached storage with mocked DAOs but a real
   * {@link ConcurrentFIFOExoCache}, so the tests exercise the true
   * cache-eviction path.
   *
   * @throws Exception when the cache service can't serve the cache instance
   */
  @Before
  public void setUp() throws Exception { // NOSONAR
    CacheService cacheService = mock(CacheService.class);
    CalendarDAO calendarDAO = mock(CalendarDAO.class);
    eventDAO = mock(EventDAO.class);
    EventRecurrenceDAO eventRecurrenceDAO = mock(EventRecurrenceDAO.class);
    when(cacheService.getCacheInstance(CachedAgendaEventStorage.EVENT_CACHE_NAME)).thenReturn(new ConcurrentFIFOExoCache<>(CachedAgendaEventStorage.EVENT_CACHE_NAME,
                                                                                                                           500));
    agendaEventStorage = new CachedAgendaEventStorage(cacheService, calendarDAO, eventDAO, eventRecurrenceDAO);
  }

  /**
   * Moving the events of a calendar must evict every cached event of that
   * calendar, so the next read reflects the new calendar membership instead
   * of a stale cached copy — while events of other calendars stay cached.
   */
  @Test
  public void testMoveCalendarEventsEvictsMovedEventsFromCache() {
    long movedEventId = 10L;
    long otherEventId = 20L;
    long fromCalendarId = 1L;
    long toCalendarId = 2L;
    long otherCalendarId = 3L;

    when(eventDAO.find(movedEventId)).thenReturn(newEventEntity(movedEventId, fromCalendarId));
    when(eventDAO.find(otherEventId)).thenReturn(newEventEntity(otherEventId, otherCalendarId));

    // Warm the cache with both events
    Event movedEvent = agendaEventStorage.getEventById(movedEventId);
    Event otherEvent = agendaEventStorage.getEventById(otherEventId);
    assertEquals(fromCalendarId, movedEvent.getCalendarId());
    assertEquals(otherCalendarId, otherEvent.getCalendarId());
    agendaEventStorage.getEventById(movedEventId);
    agendaEventStorage.getEventById(otherEventId);
    verify(eventDAO, times(1)).find(movedEventId);
    verify(eventDAO, times(1)).find(otherEventId);

    // Move: the database now holds the new membership
    when(eventDAO.moveCalendarEvents(fromCalendarId, toCalendarId)).thenReturn(Arrays.asList(movedEventId));
    when(eventDAO.find(movedEventId)).thenReturn(newEventEntity(movedEventId, toCalendarId));
    agendaEventStorage.moveCalendarEvents(fromCalendarId, toCalendarId);

    // The moved event must be re-read from the database with its new calendar
    Event movedEventAfterMove = agendaEventStorage.getEventById(movedEventId);
    assertEquals("The moved event must not serve its old calendar membership from the cache",
                 toCalendarId,
                 movedEventAfterMove.getCalendarId());
    verify(eventDAO, times(2)).find(movedEventId);

    // The event of the untouched calendar must still be served from the cache
    agendaEventStorage.getEventById(otherEventId);
    verify(eventDAO, times(1)).find(otherEventId);
  }

  /**
   * While a decide step runs on the thread, an event is read from the
   * database on every read, the cache is neither read nor filled, and the
   * event is recorded for its eviction once the step's transaction ends.
   */
  @Test
  public void testAnEventReadDuringADecideStepBypassesTheCache() {
    long eventId = 10L;
    when(eventDAO.find(eventId)).thenReturn(newEventEntity(eventId, 1L));
    agendaEventStorage.getEventById(eventId);
    verify(eventDAO, times(1)).find(eventId);

    DecideContext context = DecideContext.open();
    try {
      when(eventDAO.find(eventId)).thenReturn(newEventEntity(eventId, 2L));
      assertEquals("a read inside a decide step was served from the cache",
                   2L,
                   agendaEventStorage.getEventById(eventId).getCalendarId());
      agendaEventStorage.getEventById(eventId);
      verify(eventDAO, times(3)).find(eventId);
      assertTrue(context.getEventIds().contains(eventId));
    } finally {
      context.close();
    }

    assertEquals("a read inside a decide step replaced the cached entry",
                 1L,
                 agendaEventStorage.getEventById(eventId).getCalendarId());
    verify(eventDAO, times(3)).find(eventId);
  }

  /**
   * An event written while a decide step runs is recorded, for its eviction
   * again once the step's transaction has ended: another thread may have put
   * back the committed row the write replaces meanwhile.
   */
  @Test
  public void testAnEventWrittenDuringADecideStepIsRecordedForEviction() {
    long eventId = 10L;
    EventEntity eventEntity = newEventEntity(eventId, 1L);
    when(eventDAO.find(eventId)).thenReturn(eventEntity);
    when(eventDAO.update(any())).thenReturn(eventEntity);
    Event event = agendaEventStorage.getEventById(eventId);

    DecideContext context = DecideContext.open();
    try {
      agendaEventStorage.updateEvent(event);
      assertTrue(context.getEventIds().contains(eventId));
    } finally {
      context.close();
    }
  }

  /**
   * The eviction a decide step publishes once its transaction has ended
   * removes the cached entry, so that the next read loads the committed row.
   */
  @Test
  public void testTheEvictionsOfADecideStepRemoveTheCachedEntries() {
    long eventId = 10L;
    when(eventDAO.find(eventId)).thenReturn(newEventEntity(eventId, 1L));
    agendaEventStorage.getEventById(eventId);

    when(eventDAO.find(eventId)).thenReturn(newEventEntity(eventId, 2L));
    agendaEventStorage.evictEvents(Set.of(eventId));

    assertEquals(2L, agendaEventStorage.getEventById(eventId).getCalendarId());
    verify(eventDAO, times(2)).find(eventId);
  }

  /**
   * The eviction a decide step publishes also stops a load of the event in
   * flight when it runs from putting its result in the cache: that load read
   * the row before the step committed, and the next read loads the committed
   * row.
   *
   * @throws Exception when the loading thread fails
   */
  @Test
  public void testTheEvictionsOfADecideStepStopALoadInFlightFromCachingItsResult() throws Exception {
    long eventId = 10L;
    CountDownLatch loading = new CountDownLatch(1);
    CountDownLatch evicted = new CountDownLatch(1);
    when(eventDAO.find(eventId)).thenAnswer(invocation -> {
      loading.countDown();
      assertTrue("the eviction did not run", evicted.await(10, TimeUnit.SECONDS));
      return newEventEntity(eventId, 1L);
    }).thenReturn(newEventEntity(eventId, 2L));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Event> loadInFlight = executor.submit(() -> agendaEventStorage.getEventById(eventId));
      assertTrue("the load did not start", loading.await(10, TimeUnit.SECONDS));
      agendaEventStorage.evictEvents(Set.of(eventId));
      evicted.countDown();
      assertEquals(1L, loadInFlight.get(10, TimeUnit.SECONDS).getCalendarId());
    } finally {
      executor.shutdownNow();
    }

    assertEquals("a load in flight during the eviction cached the row it read before the step committed",
                 2L,
                 agendaEventStorage.getEventById(eventId).getCalendarId());
    verify(eventDAO, times(2)).find(eventId);
  }

  /**
   * Builds a minimal valid event entity belonging to the given calendar.
   *
   * @param eventId technical identifier of the event
   * @param calendarId technical identifier of the owning calendar
   * @return a minimal {@link EventEntity} mappable by the entity mapper
   */
  private EventEntity newEventEntity(long eventId, long calendarId) {
    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setId(calendarId);
    calendarEntity.setOwnerId(2L);

    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(eventId);
    eventEntity.setCalendar(calendarEntity);
    eventEntity.setCreatorId(2L);
    eventEntity.setAvailability(EventAvailability.FREE);
    eventEntity.setStatus(EventStatus.CONFIRMED);
    eventEntity.setStartDate(new Date(System.currentTimeMillis() - 3600000L));
    eventEntity.setEndDate(new Date());
    return eventEntity;
  }
}
