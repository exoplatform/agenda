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
package org.exoplatform.agenda.dao;

import java.util.*;
import java.util.stream.Collectors;

import javax.persistence.*;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventDAO extends GenericDAOJPAImpl<EventEntity, Long> {

  private EventConferenceDAO eventConferenceDAO;

  private EventAttendeeDAO   eventAttendeeDAO;

  private EventAttachmentDAO eventAttachmentDAO;

  private EventReminderDAO   eventReminderDAO;

  private EventRecurrenceDAO eventRecurrenceDAO;

  public EventDAO(EventConferenceDAO eventConferenceDAO,
                  EventAttendeeDAO eventAttendeeDAO,
                  EventAttachmentDAO eventAttachmentDAO,
                  EventReminderDAO eventReminderDAO,
                  EventRecurrenceDAO eventRecurrenceDAO) {
    this.eventConferenceDAO = eventConferenceDAO;
    this.eventAttendeeDAO = eventAttendeeDAO;
    this.eventAttachmentDAO = eventAttachmentDAO;
    this.eventReminderDAO = eventReminderDAO;
    this.eventRecurrenceDAO = eventRecurrenceDAO;
  }

  @Override
  @ExoTransactional
  public void delete(EventEntity entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity is mandatory");
    }
    if (entity.getId() == null) {
      throw new IllegalArgumentException("Entity with valid identifier is mandatory");
    }
    this.eventConferenceDAO.deleteEventConferences(entity.getId());
    this.eventAttendeeDAO.deleteEventAttendees(entity.getId());
    this.eventAttachmentDAO.deleteEventAttachments(entity.getId());
    this.eventReminderDAO.deleteEventReminders(entity.getId());
    this.eventRecurrenceDAO.deleteEventRecurrences(entity.getId());

    super.delete(entity);
  }

  public void deleteCalendarEvents(long calendarId) {
    this.eventConferenceDAO.deleteCalendarConferences(calendarId);
    this.eventAttendeeDAO.deleteCalendarAttendees(calendarId);
    this.eventAttachmentDAO.deleteCalendarAttachments(calendarId);
    this.eventReminderDAO.deleteCalendarReminders(calendarId);
    this.eventRecurrenceDAO.deleteCalendarRecurrences(calendarId);

    executeDeleteCalendarEventsQuery(calendarId);
  }

  public EventEntity deleteEvent(long eventId) {
    EventEntity eventEntity = find(eventId);
    if (eventEntity == null) {
      return null;
    }

    List<Long> childEventIds = this.getChildEvents(eventId);
    for (Long childEventId : childEventIds) {
      this.deleteEvent(childEventId);
    }

    this.eventConferenceDAO.deleteEventConferences(eventId);
    this.eventAttendeeDAO.deleteEventAttendees(eventId);
    this.eventAttachmentDAO.deleteEventAttachments(eventId);
    this.eventReminderDAO.deleteEventReminders(eventId);

    if (eventEntity.getRecurrence() != null) {
      this.eventRecurrenceDAO.delete(eventEntity.getRecurrence());
    }

    delete(eventEntity);

    return eventEntity;
  }

  @Override
  @ExoTransactional
  public EventEntity create(EventEntity entity) {
    entity.setCreatedDate(new Date());
    return super.create(entity);
  }

  @Override
  @ExoTransactional
  public EventEntity update(EventEntity entity) {
    entity.setUpdatedDate(new Date());
    return super.update(entity);
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteAll(List<EventEntity> entities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateAll(List<EventEntity> entities) {
    throw new UnsupportedOperationException();
  }

  public List<Long> getChildEvents(long eventId) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getChildEvents", Long.class);
    query.setParameter("parentEventId", eventId);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  @ExoTransactional
  public void executeDeleteCalendarEventsQuery(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEvent.deleteCalendarEvents");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  public List<Long> getEventIds(Date startDate,
                                Date endDate,
                                List<Long> ownerIds,
                                List<Long> attendeeIds,
                                List<EventAttendeeResponse> responseTypes,
                                int limit) {
    verifyLimit(endDate, limit);

    boolean filterAttendees = attendeeIds != null && !attendeeIds.isEmpty();
    boolean filterOwners = ownerIds != null && !ownerIds.isEmpty();

    // We avoid to use QueryBuilder to avoid contention,
    // thus we will have to build a specific query for each use case
    StringBuilder jpql = new StringBuilder("SELECT DISTINCT(ev.id), ev.startDate FROM AgendaEvent ev");
    if (filterAttendees) {
      jpql.append(" INNER JOIN ev.attendees att");
    }
    jpql.append(" INNER JOIN ev.calendar cal");
    jpql.append(" WHERE ev.status = :status");
    jpql.append(" AND (ev.endDate IS NULL OR ev.endDate >= :start)");
    if (endDate != null) {
      jpql.append(" AND ev.startDate < :end");
    }
    if (filterOwners) {
      jpql.append(" AND cal.ownerId IN (:ownerIds)");
    }
    if (filterAttendees) {
      jpql.append(" AND att.identityId IN (:attendeeIds)");
      if (responseTypes != null) {
        jpql.append(" AND att.response IN (:responseTypes)");
      }
    }
    jpql.append(" ORDER BY ev.startDate DESC");

    TypedQuery<Tuple> query = getEntityManager().createQuery(jpql.toString(), Tuple.class);
    query.setParameter("start", startDate);
    query.setParameter("status", EventStatus.CONFIRMED);
    if (endDate != null) {
      query.setParameter("end", endDate);
    }
    if (filterOwners) {
      query.setParameter("ownerIds", ownerIds);
    }
    if (filterAttendees) {
      query.setParameter("attendeeIds", attendeeIds);
      if (responseTypes != null) {
        query.setParameter("responseTypes", responseTypes);
      }
    }
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public List<Long> getExceptionalOccurenceEventIds(long parentRecurrentEventId, Date startDate, Date endDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getExceptionalOccurenceEventIds", Long.class);
    query.setParameter("parentEventId", parentRecurrentEventId);
    query.setParameter("start", startDate);
    query.setParameter("end", endDate);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  private void verifyLimit(Date endDate, int limit) {
    if (limit <= 0 && endDate == null) {
      throw new IllegalStateException("Limit of events to retrieve is missing, whether us endDate or limit parameters");
    }
  }

}
