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
package org.exoplatform.agenda.storage.cached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.dao.CalendarDAO;
import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.dao.EventRecurrenceDAO;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.concurrent.ConcurrentFIFOExoCache;

/**
 * The eviction of the cached events of a calendar, against a real cache: a
 * selector that no longer matches the cached events would be a silent no-op.
 */
class CachedAgendaEventStorageTest {

  private static final long        CALENDAR_ID       = 1L;

  private static final long        OTHER_CALENDAR_ID = 3L;

  private static final long        EVENT_ID          = 10L;

  private static final long        OTHER_EVENT_ID    = 20L;

  private CachedAgendaEventStorage agendaEventStorage;

  private EventDAO                 eventDAO;

  @BeforeEach
  void setUp() {
    CacheService cacheService = mock(CacheService.class);
    eventDAO = mock(EventDAO.class);
    ConcurrentFIFOExoCache<Long, Object> eventCache = new ConcurrentFIFOExoCache<>(CachedAgendaEventStorage.EVENT_CACHE_NAME, 500);
    doReturn(eventCache).when(cacheService).getCacheInstance(CachedAgendaEventStorage.EVENT_CACHE_NAME);
    agendaEventStorage = new CachedAgendaEventStorage(cacheService,
                                                      mock(CalendarDAO.class),
                                                      eventDAO,
                                                      mock(EventRecurrenceDAO.class));
  }

  @Test
  void deletingTheEventsOfACalendarEvictsItsCachedEventsOnly() {
    when(eventDAO.find(EVENT_ID)).thenReturn(newEventEntity(EVENT_ID, CALENDAR_ID));
    when(eventDAO.find(OTHER_EVENT_ID)).thenReturn(newEventEntity(OTHER_EVENT_ID, OTHER_CALENDAR_ID));
    agendaEventStorage.getEventById(EVENT_ID);
    agendaEventStorage.getEventById(OTHER_EVENT_ID);
    agendaEventStorage.getEventById(EVENT_ID);
    agendaEventStorage.getEventById(OTHER_EVENT_ID);
    verify(eventDAO, times(1)).find(EVENT_ID);
    verify(eventDAO, times(1)).find(OTHER_EVENT_ID);

    agendaEventStorage.deleteCalendarEvents(CALENDAR_ID);

    // The event of the deleted calendar is read again from the database
    when(eventDAO.find(EVENT_ID)).thenReturn(null);
    assertNull(agendaEventStorage.getEventById(EVENT_ID));
    verify(eventDAO, times(2)).find(EVENT_ID);
    // The event of another calendar is still served from the cache
    assertEquals(OTHER_CALENDAR_ID, agendaEventStorage.getEventById(OTHER_EVENT_ID).getCalendarId());
    verify(eventDAO, times(1)).find(OTHER_EVENT_ID);
  }

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
