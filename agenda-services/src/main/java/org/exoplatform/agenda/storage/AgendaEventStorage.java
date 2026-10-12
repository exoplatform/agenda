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
package org.exoplatform.agenda.storage;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaEventStorage {

  private static final Log   LOG           = ExoLogger.getLogger(AgendaEventStorage.class);

  private static final int   DEFAULT_LIMIT = 200;

  private CalendarDAO        calendarDAO;

  private EventDAO           eventDAO;

  private EventRecurrenceDAO eventRecurrenceDAO;

  public AgendaEventStorage(CalendarDAO calendarDAO,
                            EventDAO eventDAO,
                            EventRecurrenceDAO eventRecurrenceDAO) {
    this.calendarDAO = calendarDAO;
    this.eventDAO = eventDAO;
    this.eventRecurrenceDAO = eventRecurrenceDAO;
  }

  /**
   * Retrieves the identifiers of the events matching a filter.
   *
   * @param eventFilter criteria of the events to retrieve
   * @return {@link List} of matching event technical identifiers
   */
  public List<Long> getEventIds(EventFilter eventFilter) {
    ZonedDateTime start = eventFilter.getStart();
    ZonedDateTime end = eventFilter.getEnd();
    Date startDate = new Date(start.withSecond(0).withNano(0).toEpochSecond() * 1000);
    Date endDate = end == null ? null : new Date(end.withSecond(59).withNano(999999999).toEpochSecond() * 1000);
    List<Long> attendeeIds = eventFilter.getAttendeeId() > 0 ? eventFilter.getAttendeeWithSpacesIds() : null;
    List<Long> ownerIds = eventFilter.getOwnerIds();
    List<EventAttendeeResponse> responseTypes = eventFilter.getResponseTypes();
    int limit = eventFilter.getEnd() == null ? DEFAULT_LIMIT : 0;
    return this.eventDAO.getEventIds(startDate,
                                     endDate,
                                     ownerIds,
                                     attendeeIds,
                                     responseTypes,
                                     eventFilter.getExcludedCalendarIds(),
                                     eventFilter.getCalendarIds(),
                                     limit);
  }

  public List<Long> getPendingEventIds(Long userIdentityId,
                                       List<Long> ownerIds,
                                       List<Long> attendeeIds,
                                       List<Long> calenderIds,
                                       int offset,
                                       int limit) {
    Date now = getNowDate();
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.getPendingEventIds(userIdentityId, attendeeIds, calenderIds, now, offset, limit);
    } else {
      return this.eventDAO.getPendingEventIdsByOwnerIds(userIdentityId, ownerIds, attendeeIds, now, offset, limit);
    }
  }

  public long countPendingEvents(Long userIdentityId,
                                 List<Long> ownerIds,
                                 List<Long> attendeeIds,
                                 List<Long> calenderIds) {
    Date now = getNowDate();
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.countPendingEvents(userIdentityId, attendeeIds, calenderIds, now);
    } else {
      return this.eventDAO.countPendingEventsByOwnerIds(userIdentityId, ownerIds, attendeeIds, now);
    }
  }

  public List<Long> getUserEventCalenderIds(Long userIdentityId) {
    return this.eventDAO.getUserEventCalenderIds(userIdentityId);
  }

  public List<Long> getEventDatePollIds(Long userIdentityId,
                                        List<Long> ownerIds,
                                        List<Long> attendeeIds,
                                        int offset,
                                        int limit) {
    Date now = getNowDate();
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.getEventDatePollIds(userIdentityId, attendeeIds, now, offset, limit);
    } else {
      return this.eventDAO.getEventDatePollIdsByOwnerIds(userIdentityId, ownerIds, attendeeIds, now, offset, limit);
    }
  }

  public List<Long> getEventDatePollIds(long userIdentityId,
                                        List<Long> ownerIds,
                                        List<Long> attendeeIds,
                                        ZonedDateTime start,
                                        ZonedDateTime end) {
    Date startDate = AgendaDateUtils.toDate(start);
    Date endDate = AgendaDateUtils.toDate(end);
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.getEventDatePollIds(userIdentityId, attendeeIds, startDate, endDate);
    } else {
      return this.eventDAO.getEventDatePollIdsByOwnerIds(userIdentityId, ownerIds, attendeeIds, startDate, endDate);
    }
  }

  public long countEventDatePolls(List<Long> ownerIds, List<Long> attendeeIds) {
    Date now = getNowDate();
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.countEventDatePolls(attendeeIds, now);
    } else {
      return this.eventDAO.countEventDatePollsByOwnerIds(ownerIds, attendeeIds, now);
    }
  }

  public List<Event> getParentRecurrentEventIds(ZonedDateTime start, ZonedDateTime end) {
    Date startDate = new Date(start.withSecond(0).withNano(0).toEpochSecond() * 1000);
    Date endDate = new Date(end.withSecond(59).toEpochSecond() * 1000);
    List<EventEntity> events = this.eventDAO.getParentRecurrentEventIds(startDate, endDate);
    return events.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
  }

  public Event getEventById(long eventId) {
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(eventEntity);
  }

  @ExoTransactional
  public void deleteEventById(long eventId) {
    eventDAO.deleteEvent(eventId);
  }

  /**
   * Deletes an event whose attendees, conferences or reminders were read in
   * the same request, which {@link #deleteEventById(long)} cannot: the
   * persistence context is cleared before the bulk deletion of its children.
   *
   * @param eventId the event to delete
   */
  @ExoTransactional
  public void deleteEventAfterRead(long eventId) {
    eventDAO.deleteEventAfterRead(eventId);
  }

  @ExoTransactional
  public void deleteCalendarEvents(long calendarId) {
    eventDAO.deleteCalendarEvents(calendarId);
  }

  /**
   * Moves all events of a calendar to another calendar, keeping every event
   * and its associated data (attendees, reminders, conferences, recurrences,
   * remote identifiers) untouched apart from the calendar membership.
   *
   * @param fromCalendarId technical identifier of the calendar the events are
   *          moved away from
   * @param toCalendarId technical identifier of the calendar receiving the
   *          events
   * @return {@link List} of technical identifiers of the moved events, empty
   *         when the source calendar had no events
   */
  @ExoTransactional
  public List<Long> moveCalendarEvents(long fromCalendarId, long toCalendarId) {
    return eventDAO.moveCalendarEvents(fromCalendarId, toCalendarId);
  }

  /**
   * @param parentRecurrentEventId a parent recurrent {@link Event} technical
   *          identifier
   * @return {@link List} of Event identifiers corresponding to exceptional
   *         occurences events Identifiers of a parent recurrent event for a
   *         selected period of time
   */
  public List<Long> getExceptionalOccurenceIds(long parentRecurrentEventId) {
    return eventDAO.getExceptionalOccurenceIds(parentRecurrentEventId);
  }

  /**
   * @param parentRecurrentEventId a parent recurrent {@link Event} technical
   *          identifier
   * @param start start DateTime of period to search on
   * @param end end DateTime of period to search on
   * @return {@link List} of Event identifiers corresponding to exceptional
   *         occurences events Identifiers of a parent recurrent event for a
   *         selected period of time
   */
  public List<Long> getExceptionalOccurenceIdsByPeriod(long parentRecurrentEventId,
                                                       ZonedDateTime start,
                                                       ZonedDateTime end) {
    return eventDAO.getExceptionalOccurenceIdsByPeriod(parentRecurrentEventId,
                                                       AgendaDateUtils.toDate(start),
                                                       AgendaDateUtils.toDate(end));
  }

  /**
   * Reads the exceptional occurrences of a recurrent event in one statement,
   * ordered by start date.
   *
   * @param parentRecurrentEventId a parent recurrent {@link Event} technical
   *          identifier
   * @return {@link List} of its exceptional occurrence events
   */
  public List<Event> getExceptionalOccurrences(long parentRecurrentEventId) {
    return eventDAO.getExceptionalOccurences(parentRecurrentEventId)
                   .stream()
                   .map(EntityMapper::fromEntity)
                   .collect(Collectors.toList());
  }

  /**
   * Locks event rows until the current transaction ends, as its first reads:
   * every row is locked before any of them is read. The persistence context
   * is flushed then cleared first, so that no entity loaded before the lock
   * is served after it with the state it had then. The rows are locked in
   * ascending identifier order, so that two transactions locking the same
   * rows cannot deadlock.
   *
   * @param eventIds the technical identifiers of the events to lock
   * @return whether every row exists, and so is locked; false as soon as one
   *         does not
   */
  public boolean lockEvents(long... eventIds) {
    eventDAO.flushAndClear();
    long[] sortedEventIds = eventIds.clone();
    Arrays.sort(sortedEventIds);
    for (long eventId : sortedEventIds) {
      if (!eventDAO.lockEvent(eventId)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Evicts events from the cache this storage reads through, once the
   * transaction that read or wrote them has ended. This storage has no cache.
   *
   * @param eventIds the technical identifiers of the events to evict
   */
  public void evictEvents(Collection<Long> eventIds) {
    // No cache to evict from
  }

  public Event createEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);
    eventEntity.setId(null);

    if (event.getParentId() > 0) {
      EventEntity parentEvent = eventDAO.find(event.getParentId());
      eventEntity.setParent(parentEvent);
    }

    updateEventCalendar(event, eventEntity);
    eventEntity = eventDAO.create(eventEntity);

    createEventRecurrence(event, eventEntity);

    return EntityMapper.fromEntity(eventEntity);
  }

  public List<Long> deleteExceptionalOccurences(long parentRecurrentEventId) {
    List<Long> deletedEventIds = this.eventDAO.getExceptionalOccurenceIds(parentRecurrentEventId);
    this.eventDAO.deleteExceptionalOccurences(parentRecurrentEventId);
    return deletedEventIds;
  }

  /**
   * Finds the row a series holds for one of its dates: the row whose
   * occurrence id designates the same date of the series
   * ({@link Utils#isSameSeriesDate}), the first created when the date has
   * several.
   * <p>
   * The occurrence list hides the computed dates a row stands for with a rule
   * of its own, the same UTC day or less than twelve hours apart, which knows
   * neither the series' zone nor its several dates a day.
   *
   * @param series the series
   * @param occurrenceId the date's occurrence id
   * @return the date's row, or null when it has none
   */
  public Event findExceptionalOccurrence(Event series, ZonedDateTime occurrenceId) {
    List<Long> rowIds;
    if (Utils.hasSeveralDatesADay(series.getRecurrence())) {
      // A date of a series with several dates a day is its instant: only the
      // rows stored within that second are read
      ZonedDateTime second = occurrenceId.truncatedTo(ChronoUnit.SECONDS);
      rowIds = getExceptionalOccurenceIdsByPeriod(series.getId(), second, second.plusNanos(999_000_000));
    } else {
      // A row of the date is stored within the date's UTC day widened by the
      // largest offset of a zone, fourteen hours on either side
      ZonedDateTime dateStart = Utils.getSeriesDate(series, occurrenceId).atStartOfDay(ZoneOffset.UTC);
      rowIds = getExceptionalOccurenceIdsByPeriod(series.getId(), dateStart.minusHours(14), dateStart.plusDays(1).plusHours(14));
    }
    List<Event> dateRows = new ArrayList<>();
    for (long rowId : rowIds) {
      Event row = getEventById(rowId);
      ZonedDateTime rowOccurrenceId = row == null || row.getOccurrence() == null ? null : row.getOccurrence().getId();
      if (rowOccurrenceId != null && Utils.isSameSeriesDate(series, occurrenceId, rowOccurrenceId)) {
        dateRows.add(row);
      }
    }
    if (dateRows.isEmpty()) {
      return null;
    }
    dateRows.sort(Comparator.comparing(Event::getId));
    if (dateRows.size() > 1) {
      LOG.warn("Event {} holds {} rows for its date {}, the first created is used: {}",
               series.getId(),
               dateRows.size(),
               occurrenceId,
               dateRows.stream().map(Event::getId).toList());
    }
    return dateRows.get(0);
  }

  public Event updateEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);

    updateEventParent(event, eventEntity);
    updateEventCalendar(event, eventEntity);
    updateEventRecurrence(event, eventEntity);

    eventEntity = eventDAO.update(eventEntity);
    eventEntity = eventDAO.find(eventEntity.getId());
    // The save leaves the capacity mark as stored, which an answer may have
    // set, or a reset cleared, since the model saved, or the row the
    // persistence context holds, was read: the row read back carries the
    // stored one
    eventEntity.setCapacityReachedDate(eventDAO.getCapacityReachedDate(eventEntity.getId()));
    return EntityMapper.fromEntity(eventEntity);
  }

  /**
   * Writes the mark of an event row's pool having reached its capacity, and
   * nothing else of the row: once the row exists, the only write of that
   * mark, which a save of the whole row leaves as it is. It requires an
   * active transaction, the one of the decide step holding the event lock.
   *
   * @param eventId event identifier
   * @param capacityReachedDate the instant the pool is marked as having
   *          reached its capacity, or null to clear the mark
   */
  public void saveCapacityReachedDate(long eventId, ZonedDateTime capacityReachedDate) {
    Date date = AgendaDateUtils.toDate(capacityReachedDate);
    eventDAO.saveCapacityReachedDate(eventId, date);
    // The row the persistence context may already hold reads the mark written
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity != null) {
      eventEntity.setCapacityReachedDate(date);
    }
  }

  public boolean isRecurrentEvent(long eventId) {
    Event event = getEventById(eventId);
    if (event == null) {
      return false;
    }
    return event.getRecurrence() != null;
  }

  private void updateEventCalendar(Event event, EventEntity eventEntity) {
    CalendarEntity calendarEntity = calendarDAO.find(event.getCalendarId());
    eventEntity.setCalendar(calendarEntity);
  }

  private void updateEventParent(Event event, EventEntity eventEntity) {
    if (event.getParentId() > 0) {
      EventEntity parentEvent = eventDAO.find(event.getParentId());
      if (parentEvent == null) {
        throw new IllegalStateException("Can't find parent event with id " + event.getParentId());
      }
      eventEntity.setParent(parentEvent);
    }
  }

  private void updateEventRecurrence(Event event, EventEntity eventEntity) {
    EventRecurrence recurrence = event.getRecurrence();
    EventEntity storedEventEntity = eventDAO.find(eventEntity.getId());
    if (storedEventEntity == null) {
      throw new IllegalStateException("Can't find event with id " + eventEntity.getId());
    }

    if (recurrence != null) {
      EventRecurrenceEntity eventRecurrenceEntity = EntityMapper.toEntity(event, recurrence);
      eventRecurrenceEntity.setEvent(eventEntity);
      if (storedEventEntity.getRecurrence() != null) {
        eventRecurrenceEntity.setId(storedEventEntity.getRecurrence().getId());
        eventRecurrenceEntity.setEvent(storedEventEntity);
        eventRecurrenceEntity = eventRecurrenceDAO.update(eventRecurrenceEntity);
      } else {
        eventRecurrenceEntity.setId(null);
        eventRecurrenceEntity.setEvent(eventEntity);
        eventEntity.setRecurrence(eventRecurrenceEntity);
        eventRecurrenceEntity = eventRecurrenceDAO.create(eventRecurrenceEntity);
      }
      eventEntity.setRecurrence(eventRecurrenceEntity);
    } else if (storedEventEntity.getRecurrence() != null) {
      eventRecurrenceDAO.delete(storedEventEntity.getRecurrence());
    }
  }

  private void createEventRecurrence(Event event, EventEntity eventEntity) {
    if (event.getRecurrence() != null) {
      EventRecurrenceEntity eventRecurrenceEntity = EntityMapper.toEntity(event, event.getRecurrence());
      eventRecurrenceEntity.setId(null);
      eventRecurrenceEntity.setEvent(eventEntity);
      eventEntity.setRecurrence(eventRecurrenceEntity);
      eventRecurrenceDAO.create(eventRecurrenceEntity);
    }
  }

  private Date getNowDate() {
    return AgendaDateUtils.toDate(ZonedDateTime.now(ZoneOffset.UTC));
  }

}
