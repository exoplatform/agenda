/*
 * Copyright (C) 2020 eXo Platform SAS.
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
package org.exoplatform.agenda.storage.cached;

import java.util.Collection;
import java.util.List;

import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.DecideContext;
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

  /**
   * {@inheritDoc} While a decide step runs on the thread, the event is read
   * from the database, after the step's lock, and the cache is neither read
   * nor filled: the loader would store at once, for every thread of the node,
   * a row the step's transaction has not committed yet, and an entry another
   * thread put back after the step's own eviction would be stale. The event is
   * recorded instead, and evicted once the step's transaction ends.
   */
  @Override
  public Event getEventById(long eventId) {
    if (DecideContext.current() != null) {
      DecideContext.recordEvent(eventId);
      return super.getEventById(eventId);
    }
    Event event = this.eventFutureCache.get(null, eventId);
    return event == null ? null : event.clone();
  }

  @Override
  public List<Event> getExceptionalOccurrences(long parentRecurrentEventId) {
    List<Event> exceptionalOccurrences = super.getExceptionalOccurrences(parentRecurrentEventId);
    exceptionalOccurrences.forEach(exceptionalOccurrence -> DecideContext.recordEvent(exceptionalOccurrence.getId()));
    return exceptionalOccurrences;
  }

  @Override
  public Event createEvent(Event event) {
    Event createdEvent = super.createEvent(event);
    DecideContext.recordEvent(createdEvent.getId());
    return createdEvent;
  }

  @Override
  public Event updateEvent(Event event) {
    Event updatedEvent = super.updateEvent(event);
    this.eventFutureCache.remove(event.getId());
    DecideContext.recordEvent(event.getId());
    return updatedEvent;
  }

  @Override
  public void deleteEventById(long eventId) {
    super.deleteEventById(eventId);
    this.eventFutureCache.remove(eventId);
    DecideContext.recordEvent(eventId);
  }

  @Override
  public void deleteEventAfterRead(long eventId) {
    super.deleteEventAfterRead(eventId);
    this.eventFutureCache.remove(eventId);
    DecideContext.recordEvent(eventId);
  }

  /**
   * {@inheritDoc} A load in flight, started before the transaction ended, is
   * forgotten as well, so that it cannot put back the state it read then.
   */
  @Override
  public void evictEvents(Collection<Long> eventIds) {
    for (Long eventId : eventIds) {
      this.eventFutureCache.removeFuture(eventId);
      this.eventFutureCache.remove(eventId);
    }
  }

  @Override
  public void deleteCalendarEvents(long calendarId) {
    super.deleteCalendarEvents(calendarId);
    clearCalendarEventsFromCache(calendarId);
  }

  /**
   * {@inheritDoc} In addition, evicts the moved events from the event cache so
   * that subsequent reads return the new calendar membership.
   */
  @Override
  public List<Long> moveCalendarEvents(long fromCalendarId, long toCalendarId) {
    List<Long> movedEventIds = super.moveCalendarEvents(fromCalendarId, toCalendarId);
    clearCalendarEventsFromCache(fromCalendarId);
    return movedEventIds;
  }

  /**
   * Evicts from the event cache every event cached with a membership to the
   * given calendar.
   *
   * @param calendarId technical identifier of the calendar whose cached events
   *          have to be evicted
   */
  private void clearCalendarEventsFromCache(long calendarId) {
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
        DecideContext.recordEvent(exceptionalEventId);
      }
    }
    return exceptionalEventIds;
  }

  public void clearCache() {
    this.eventFutureCache.clear();
  }
}
