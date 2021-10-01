// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.storage.cached;

import java.util.List;

import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.commons.cache.future.FutureExoCache;
import org.exoplatform.commons.cache.future.Loader;
import org.exoplatform.services.cache.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class CachedAgendaEventStorage extends AgendaEventStorage {

  private static final Log                    LOG              = ExoLogger.getLogger(CachedAgendaEventStorage.class);

  public static final String                  EVENT_CACHE_NAME = "agenda.event";

  private FutureExoCache<Long, Event, Object> eventFutureCache = null;

  private ExoCache<Long, Event>               eventCache       = null;

  public CachedAgendaEventStorage(CacheService cacheService,
                                  CalendarDAO calendarDAO,
                                  EventDAO eventDAO,
                                  EventRecurrenceDAO eventRecurrenceDAO) {
    super(calendarDAO, eventDAO, eventRecurrenceDAO);

    eventCache = cacheService.getCacheInstance(EVENT_CACHE_NAME);
    // Future cache is used for clustered environment improvements (usage of
    // putLocal VS put)
    this.eventFutureCache = new FutureExoCache<>(new Loader<Long, Event, Object>() {
      @Override
      public Event retrieve(Object context, Long eventId) throws Exception {
        return CachedAgendaEventStorage.super.getEventById(eventId);
      }
    }, eventCache);
  }

  @Override
  public Event getEventById(long eventId) {
    Event event = this.eventFutureCache.get(null, eventId);
    return event == null ? null : event.clone();
  }

  @Override
  public Event updateEvent(Event event) {
    Event updatedEvent = super.updateEvent(event);
    this.eventFutureCache.remove(event.getId());
    return updatedEvent;
  }

  @Override
  public void deleteEventById(long eventId) {
    super.deleteEventById(eventId);
    this.eventFutureCache.remove(eventId);
  }

  @Override
  public void deleteCalendarEvents(long calendarId) {
    super.deleteCalendarEvents(calendarId);
    try {
      this.eventCache.select(new CachedObjectSelector<Long, Event>() {
        @Override
        public void onSelect(ExoCache<? extends Long, ? extends Event> cache,
                             Long eventId,
                             ObjectCacheInfo<? extends Event> ocinfo) throws Exception {
          cache.remove(eventId);
        }

        @Override
        public boolean select(Long key, ObjectCacheInfo<? extends Event> ocinfo) {
          return ocinfo.get().getCalendarId() == calendarId;
        }
      });
    } catch (Exception e) {
      LOG.warn("Error when clearing event cache", e);
    }
  }

  @Override
  public List<Long> deleteExceptionalOccurences(long parentRecurrentEventId) {
    List<Long> exceptionalEventIds = super.deleteExceptionalOccurences(parentRecurrentEventId);
    if (exceptionalEventIds != null) {
      for (Long exceptionalEventId : exceptionalEventIds) {
        this.eventFutureCache.remove(exceptionalEventId);
      }
    }
    return exceptionalEventIds;
  }

  public void clearCache() {
    this.eventFutureCache.clear();
  }
}
