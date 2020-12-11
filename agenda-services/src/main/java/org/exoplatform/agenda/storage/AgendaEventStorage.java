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

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaEventStorage {

  private static final Log   LOG           = ExoLogger.getLogger(AgendaEventStorage.class);

  private static final int   DEFAULT_LIMIT = 200;

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

  public RemoteProvider getRemoteProviderById(long remoteProviderId) {
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(remoteProviderId);
    if (remoteProviderEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = EntityMapper.toEntity(remoteProvider);
    if (remoteProviderEntity.getId() == null) {
      RemoteProviderEntity existingRemoteProviderEntity = remoteProviderDAO.findByName(remoteProvider.getName());
      if (existingRemoteProviderEntity == null) {
        remoteProviderEntity = remoteProviderDAO.create(remoteProviderEntity);
      } else {
        remoteProviderEntity.setId(existingRemoteProviderEntity.getId());
        remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
      }
    } else {
      remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public RemoteProvider getConnectorByName(String connectorName) {
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.findByName(connectorName);
    if (remoteProviderEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public void saveRemoteProviderStatus(String connectorName, boolean enabled) {
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.findByName(connectorName);
    if (remoteProviderEntity == null) {
      throw new IllegalStateException("Remote calendar not found with name " + remoteProviderEntity);
    }
    remoteProviderEntity.setEnabled(enabled);
    remoteProviderDAO.update(remoteProviderEntity);
  }

  /**
   * @param parentRecurrentEventId a parent recurrent {@link Event} technical
   *          identifier
   * @return {@link List} of Event identifiers corresponding to exceptional
   *         occurences events Identifiers of a parent recurrent event for a
   *         selected period of time
   */
  public List<Long> getExceptionalOccurenceEventIds(long parentRecurrentEventId) {
    return eventDAO.getExceptionalOccurences(parentRecurrentEventId)
                   .stream()
                   .map(EventEntity::getId)
                   .collect(Collectors.toList());
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

  public void deleteExceptionalOccurences(long parentRecurrentEventId) {
    this.eventDAO.deleteExceptionalOccurences(parentRecurrentEventId);
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
      LOG.warn("More than one exceptional event on parent event {} is found for occurrence of day {}. Occurrence ids found: {}",
               parentRecurrentEventId,
               occurrenceId,
               StringUtils.join(exceptionalOccurenceEventIds, ","));
    }
    return getEventById(exceptionalOccurenceEventIds.get(0));
  }

  public List<Event> getEventExceptionalOccurrences(long parentRecurrentEventId) {
    List<EventEntity> eventExceptionalOccurrences = eventDAO.getExceptionalOccurences(parentRecurrentEventId);

    if (eventExceptionalOccurrences == null || eventExceptionalOccurrences.isEmpty()) {
      return null;
    }
    return eventExceptionalOccurrences.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
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
