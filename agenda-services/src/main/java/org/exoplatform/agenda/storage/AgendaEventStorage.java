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
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.EntityMapper;

public class AgendaEventStorage {

  private CalendarDAO        calendarDAO;

  private RemoteProviderDAO  remoteProviderDAO;

  private EventDAO           eventDAO;

  private EventRecurrenceDAO eventRecurrenceDAO;

  public AgendaEventStorage(RemoteProviderDAO remoteProviderDAO,
                            CalendarDAO calendarDAO,
                            EventDAO eventDAO,
                            EventRecurrenceDAO eventRecurrenceDAO) {
    this.calendarDAO = calendarDAO;
    this.remoteProviderDAO = remoteProviderDAO;
    this.eventDAO = eventDAO;
    this.eventRecurrenceDAO = eventRecurrenceDAO;
  }

  public List<Long> getEventIdsByOwnerIds(ZonedDateTime start, ZonedDateTime end, int limit, Long... ownerIds) {
    if (start == null) {
      throw new IllegalArgumentException("Start date is mandatory");
    }
    if (end == null) {
      throw new IllegalArgumentException("End date is mandatory");
    }

    Date startDate = new Date(start.withSecond(0).withNano(0).toEpochSecond() * 1000);
    Date endDate = new Date(end.withSecond(59).withNano(999999999).toEpochSecond() * 1000);
    return eventDAO.getEventIdsByPeriodAndOwnerIds(startDate, endDate, limit, ownerIds);
  }

  public List<Long> getEventIdsByAttendeeIds(ZonedDateTime start,
                                             ZonedDateTime end,
                                             int limit,
                                             List<Long> ownerIds,
                                             List<Long> attendeeIds) {
    if (start == null) {
      throw new IllegalArgumentException("Start date is mandatory");
    }

    Date startDate = new Date(start.withSecond(0).withNano(0).toEpochSecond() * 1000);
    Date endDate = end == null ? null : new Date(end.withSecond(59).withNano(999999999).toEpochSecond() * 1000);
    if (ownerIds == null || ownerIds.isEmpty()) {
      return eventDAO.getEventIdsByPeriodAndAttendeeIds(startDate, endDate, limit, attendeeIds);
    } else {
      return eventDAO.getEventIdsByPeriodAndAttendeeIdsAndOwnerIds(startDate, endDate, limit, ownerIds, attendeeIds);
    }
  }

  public Event getEventById(long eventId) {
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(eventEntity);
  }

  public void deleteEventById(long eventId) {
    eventDAO.deleteEvent(eventId);
  }

  public List<RemoteProvider> getRemoteProviders() {
    List<RemoteProviderEntity> remoteProviders = remoteProviderDAO.findAll();
    return remoteProviders == null ? Collections.emptyList()
                                   : remoteProviders.stream()
                                                    .map(remoteProviderEntity -> EntityMapper.fromEntity(remoteProviderEntity))
                                                    .collect(Collectors.toList());
  }

  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = EntityMapper.toEntity(remoteProvider);
    if (remoteProviderEntity.getId() == null) {
      remoteProviderEntity = remoteProviderDAO.create(remoteProviderEntity);
    } else {
      remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  /**
   * @param parentRecurrentEventId a parent recurrent {@link Event} technical
   *          identifier
   * @param start start DateTime of period to search on
   * @param end end DateTime of period to search on
   * @return {@link List} of {@link ZonedDateTime} corresponding to exceptional
   *         occurences events Identifiers of a parent recurrent event for a
   *         selected period of time
   */
  public List<Long> getExceptionalOccurenceEventIds(long parentRecurrentEventId,
                                                    ZonedDateTime start,
                                                    ZonedDateTime end) {
    return eventDAO.getExceptionalOccurenceEventIds(parentRecurrentEventId,
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

    if (event.getRemoteProviderId() > 0) {
      RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(event.getRemoteProviderId());
      eventEntity.setRemoteProvider(remoteProviderEntity);
    }

    eventEntity = eventDAO.create(eventEntity);

    createEventRecurrence(event, eventEntity);

    return EntityMapper.fromEntity(eventEntity);
  }

  public Event getExceptionalOccurrenceEvent(long parentRecurrentEventId, ZonedDateTime occurrenceId) {
    ZonedDateTime start = occurrenceId.toLocalDate().atStartOfDay(ZoneOffset.UTC);
    ZonedDateTime end = occurrenceId.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
    List<Long> exceptionalOccurenceEventIds = eventDAO.getExceptionalOccurenceEventIds(parentRecurrentEventId,
                                                                                       AgendaDateUtils.toDate(start),
                                                                                       AgendaDateUtils.toDate(end));
    if (exceptionalOccurenceEventIds == null || exceptionalOccurenceEventIds.isEmpty()) {
      return null;
    } else if (exceptionalOccurenceEventIds.size() > 1) {
      throw new IllegalStateException("More than one exceptional event on parent event " + parentRecurrentEventId
          + " is found for occurrence of day" + occurrenceId);
    }
    return getEventById(exceptionalOccurenceEventIds.get(0));
  }

  public Event updateEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);

    updateEventParent(event, eventEntity);
    updateEventCalendar(event, eventEntity);
    updateEventRemoteProvider(event, eventEntity);
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

  private void updateEventRemoteProvider(Event event, EventEntity eventEntity) {
    if (event.getRemoteProviderId() > 0) {
      RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(event.getRemoteProviderId());
      if (remoteProviderEntity == null) {
        throw new IllegalStateException("Can't find remote calendar provider with id " + event.getRemoteProviderId());
      }
      eventEntity.setRemoteProvider(remoteProviderEntity);
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
