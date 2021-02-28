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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.EntityMapper;
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

  public List<Long> getEventIds(EventFilter eventFilter) {
    ZonedDateTime start = eventFilter.getStart();
    ZonedDateTime end = eventFilter.getEnd();
    Date startDate = new Date(start.withSecond(0).withNano(0).toEpochSecond() * 1000);
    Date endDate = end == null ? null : new Date(end.withSecond(59).withNano(999999999).toEpochSecond() * 1000);
    List<Long> attendeeIds = eventFilter.getAttendeeId() > 0 ? eventFilter.getAttendeeWithSpacesIds() : null;
    List<Long> ownerIds = eventFilter.getOwnerIds();
    List<EventAttendeeResponse> responseTypes = eventFilter.getResponseTypes();
    int limit = eventFilter.getEnd() == null ? DEFAULT_LIMIT : 0;
    return this.eventDAO.getEventIds(startDate, endDate, ownerIds, attendeeIds, responseTypes, limit);
  }

  public List<Long> getPendingEventIds(Long userIdentityId,
                                       List<Long> ownerIds,
                                       List<Long> attendeeIds,
                                       int offset,
                                       int limit) {
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.getPendingEventIds(userIdentityId, attendeeIds, offset, limit);
    } else {
      return this.eventDAO.getPendingEventIdsByOwnerIds(userIdentityId, ownerIds, attendeeIds, offset, limit);
    }
  }

  public long countPendingEvents(Long userIdentityId,
                                 List<Long> ownerIds,
                                 List<Long> attendeeIds) {
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.countPendingEvents(userIdentityId, attendeeIds);
    } else {
      return this.eventDAO.countPendingEventsByOwnerIds(userIdentityId, ownerIds, attendeeIds);
    }
  }

  public List<Long> getEventDatePollIds(List<Long> ownerIds,
                                        List<Long> attendeeIds,
                                        int offset,
                                        int limit) {
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.getEventDatePollIds(attendeeIds, offset, limit);
    } else {
      return this.eventDAO.getEventDatePollIdsByOwnerIds(ownerIds, attendeeIds, offset, limit);
    }
  }

  public long countEventDatePolls(List<Long> ownerIds, List<Long> attendeeIds) {
    if (ownerIds == null || ownerIds.isEmpty()) {
      return this.eventDAO.countEventDatePolls(attendeeIds);
    } else {
      return this.eventDAO.countEventDatePollsByOwnerIds(ownerIds, attendeeIds);
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

  @ExoTransactional
  public void deleteCalendarEvents(long calendarId) {
    eventDAO.deleteCalendarEvents(calendarId);
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

  public Event getExceptionalOccurrenceEvent(long parentRecurrentEventId, ZonedDateTime occurrenceId) {
    ZonedDateTime start = occurrenceId.toLocalDate().atStartOfDay(ZoneOffset.UTC);
    ZonedDateTime end = occurrenceId.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
    List<Long> exceptionalOccurenceEventIds = eventDAO.getExceptionalOccurenceIdsByPeriod(parentRecurrentEventId,
                                                                                          AgendaDateUtils.toDate(start),
                                                                                          AgendaDateUtils.toDate(end));
    if (exceptionalOccurenceEventIds == null || exceptionalOccurenceEventIds.isEmpty()) {
      return null;
    } else if (exceptionalOccurenceEventIds.size() > 1) {
      LOG.warn("More than one exceptional event on parent event {} is found for occurrence of day {}. Occurrence ids found: {}",
               parentRecurrentEventId,
               occurrenceId,
               StringUtils.join(exceptionalOccurenceEventIds, ","));
    }
    return getEventById(exceptionalOccurenceEventIds.get(0));
  }

  public Event updateEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);

    updateEventParent(event, eventEntity);
    updateEventCalendar(event, eventEntity);
    updateEventRecurrence(event, eventEntity);

    eventEntity = eventDAO.update(eventEntity);
    eventEntity = eventDAO.find(eventEntity.getId());
    return EntityMapper.fromEntity(eventEntity);
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

}
