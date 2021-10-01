// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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

  private static final int   DEFAULT_LIMIT = 10;

  private EventConferenceDAO eventConferenceDAO;

  private EventAttendeeDAO   eventAttendeeDAO;

  private EventReminderDAO   eventReminderDAO;

  private EventRecurrenceDAO eventRecurrenceDAO;

  private RemoteEventDAO     remoteEventDAO;

  private EventDateOptionDAO dateOptionDAO;

  private EventDatePollDAO   datePollDAO;

  public EventDAO(EventConferenceDAO eventConferenceDAO,
                  EventAttendeeDAO eventAttendeeDAO,
                  EventReminderDAO eventReminderDAO,
                  EventRecurrenceDAO eventRecurrenceDAO,
                  RemoteEventDAO remoteEventDAO,
                  EventDateOptionDAO dateOptionDAO,
                  EventDatePollDAO datePollDAO) {
    this.eventConferenceDAO = eventConferenceDAO;
    this.eventAttendeeDAO = eventAttendeeDAO;
    this.eventReminderDAO = eventReminderDAO;
    this.eventRecurrenceDAO = eventRecurrenceDAO;
    this.remoteEventDAO = remoteEventDAO;
    this.dateOptionDAO = dateOptionDAO;
    this.datePollDAO = datePollDAO;
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
    this.eventReminderDAO.deleteEventReminders(entity.getId());
    this.eventRecurrenceDAO.deleteEventRecurrences(entity.getId());
    this.remoteEventDAO.deleteRemoteEvents(entity.getId());
    this.datePollDAO.deleteEventPoll(entity.getId());
    this.dateOptionDAO.deleteEventDateOptions(entity.getId());

    super.delete(entity);
  }

  public void deleteCalendarEvents(long calendarId) {
    this.eventConferenceDAO.deleteCalendarConferences(calendarId);
    this.eventAttendeeDAO.deleteCalendarAttendees(calendarId);
    this.eventReminderDAO.deleteCalendarReminders(calendarId);
    this.eventRecurrenceDAO.deleteCalendarRecurrences(calendarId);
    this.remoteEventDAO.deleteCalendarRemoteEvents(calendarId);

    List<Long> calendarEventIds = getCalendarEventIds(calendarId);
    for (Long eventId : calendarEventIds) {
      this.datePollDAO.deleteEventPoll(eventId);
      this.dateOptionDAO.deleteEventDateOptions(eventId);
    }

    // Ensure to delete all entities on DB to avoid having a DB constraint error
    if (getEntityManager().getTransaction() != null && getEntityManager().getTransaction().isActive()) {
      getEntityManager().getTransaction().commit();
    }

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
    this.eventReminderDAO.deleteEventReminders(eventId);
    this.remoteEventDAO.deleteRemoteEvents(eventId);

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
  public void updateAll(List<EventEntity> entities) {
    throw new UnsupportedOperationException();
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
      if (responseTypes != null && !responseTypes.isEmpty()) {
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

  public List<Long> getPendingEventIds(Long userIdentityId,
                                       List<Long> attendeeIds,
                                       Date fromDate,
                                       int offset,
                                       int limit) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getPendingEventIds", Tuple.class);
    query.setParameter("status", EventStatus.CONFIRMED);
    query.setParameter("response", EventAttendeeResponse.NEEDS_ACTION);
    query.setParameter("userIdentityId", userIdentityId);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("date", fromDate);
    if (offset >= 0) {
      query.setFirstResult(offset);
    } else {
      query.setFirstResult(0);
    }
    if (limit > 0) {
      query.setMaxResults(limit);
    } else {
      query.setMaxResults(DEFAULT_LIMIT);
    }
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public Long countPendingEvents(Long userIdentityId,
                                 List<Long> attendeeIds,
                                 Date fromDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.countPendingEvents", Long.class);
    query.setParameter("status", EventStatus.CONFIRMED);
    query.setParameter("response", EventAttendeeResponse.NEEDS_ACTION);
    query.setParameter("userIdentityId", userIdentityId);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("date", fromDate);
    try {
      Long count = query.getSingleResult();
      return count == null ? 0l : count.longValue();
    } catch (NoResultException e) {
      return 0l;
    }
  }

  public List<Long> getPendingEventIdsByOwnerIds(Long userIdentityId,
                                                 List<Long> ownerIds,
                                                 List<Long> attendeeIds,
                                                 Date fromDate,
                                                 int offset,
                                                 int limit) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getPendingEventIdsByOwnerIds", Tuple.class);
    query.setParameter("status", EventStatus.CONFIRMED);
    query.setParameter("response", EventAttendeeResponse.NEEDS_ACTION);
    query.setParameter("userIdentityId", userIdentityId);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("ownerIds", ownerIds);
    query.setParameter("date", fromDate);
    if (offset >= 0) {
      query.setFirstResult(offset);
    } else {
      query.setFirstResult(0);
    }
    if (limit > 0) {
      query.setMaxResults(limit);
    } else {
      query.setMaxResults(DEFAULT_LIMIT);
    }
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public long countPendingEventsByOwnerIds(Long userIdentityId,
                                           List<Long> ownerIds,
                                           List<Long> attendeeIds,
                                           Date fromDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.countPendingEventsByOwnerIds", Long.class);
    query.setParameter("status", EventStatus.CONFIRMED);
    query.setParameter("response", EventAttendeeResponse.NEEDS_ACTION);
    query.setParameter("userIdentityId", userIdentityId);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("ownerIds", ownerIds);
    query.setParameter("date", fromDate);
    try {
      Long count = query.getSingleResult();
      return count == null ? 0l : count.longValue();
    } catch (NoResultException e) {
      return 0l;
    }
  }

  public List<Long> getEventDatePollIds(Long userIdentityId,
                                        List<Long> attendeeIds,
                                        Date fromDate,
                                        int offset,
                                        int limit) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getPendingDatePollIds", Tuple.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("userIdentityId", userIdentityId);
    query.setParameter("date", fromDate);
    if (offset >= 0) {
      query.setFirstResult(offset);
    } else {
      query.setFirstResult(0);
    }
    if (limit > 0) {
      query.setMaxResults(limit);
    } else {
      query.setMaxResults(DEFAULT_LIMIT);
    }
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public List<Long> getEventDatePollIds(Long userIdentityId,
                                        List<Long> attendeeIds,
                                        Date start,
                                        Date end) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getDatePollIdsByDates", Tuple.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("start", start);
    query.setParameter("end", end);
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public Long countEventDatePolls(List<Long> attendeeIds,
                                  Date fromDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.countPendingDatePoll", Long.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("date", fromDate);
    try {
      Long count = query.getSingleResult();
      return count == null ? 0l : count.longValue();
    } catch (NoResultException e) {
      return 0l;
    }
  }

  public List<Long> getEventDatePollIdsByOwnerIds(Long userIdentityId,
                                                  List<Long> ownerIds,
                                                  List<Long> attendeeIds,
                                                  Date fromDate,
                                                  int offset,
                                                  int limit) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getPendingDatePollIdsByOwnerIds", Tuple.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("ownerIds", ownerIds);
    query.setParameter("date", fromDate);
    query.setParameter("userIdentityId", userIdentityId);
    if (offset >= 0) {
      query.setFirstResult(offset);
    } else {
      query.setFirstResult(0);
    }
    if (limit > 0) {
      query.setMaxResults(limit);
    } else {
      query.setMaxResults(DEFAULT_LIMIT);
    }
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public List<Long> getEventDatePollIdsByOwnerIds(Long userIdentityId,
                                                  List<Long> ownerIds,
                                                  List<Long> attendeeIds,
                                                  Date start,
                                                  Date end) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getDatePollIdsByOwnerIdsAndDates", Tuple.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("ownerIds", ownerIds);
    query.setParameter("start", start);
    query.setParameter("end", end);
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public Long countEventDatePollsByOwnerIds(List<Long> ownerIds,
                                            List<Long> attendeeIds,
                                            Date fromDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.countPendingDatePollByOwnerIds", Long.class);
    query.setParameter("status", EventStatus.TENTATIVE);
    query.setParameter("attendeeIds", attendeeIds);
    query.setParameter("ownerIds", ownerIds);
    query.setParameter("date", fromDate);
    try {
      Long count = query.getSingleResult();
      return count == null ? 0l : count.longValue();
    } catch (NoResultException e) {
      return 0l;
    }
  }

  public List<EventEntity> getParentRecurrentEventIds(Date startDate, Date endDate) {
    TypedQuery<EventEntity> query = getEntityManager().createNamedQuery("AgendaEvent.getParentRecurrentEventIds",
                                                                        EventEntity.class);
    query.setParameter("start", startDate);
    query.setParameter("end", endDate);
    query.setParameter("status", EventStatus.CONFIRMED);
    List<EventEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList;
  }

  public List<Long> getExceptionalOccurenceIdsByPeriod(long parentRecurrentEventId, Date startDate, Date endDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getExceptionalOccurenceIdsByPeriod", Long.class);
    query.setParameter("parentEventId", parentRecurrentEventId);
    query.setParameter("start", startDate);
    query.setParameter("end", endDate);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<Long> getExceptionalOccurenceIdsByStart(long parentRecurrentEventId, Date startDate) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getExceptionalOccurenceIdsByStart", Long.class);
    query.setParameter("parentEventId", parentRecurrentEventId);
    query.setParameter("start", startDate);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<Long> getExceptionalOccurenceIds(long parentRecurrentEventId) {
    TypedQuery<Tuple> query = getEntityManager().createNamedQuery("AgendaEvent.getExceptionalOccurenceIds", Tuple.class);
    query.setParameter("parentEventId", parentRecurrentEventId);
    List<Tuple> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList()
                              : resultList.stream().map(tuple -> tuple.get(0, Long.class)).collect(Collectors.toList());
  }

  public void deleteExceptionalOccurences(long parentRecurrentEventId) {
    List<Long> result = getExceptionalOccurenceIds(parentRecurrentEventId);
    for (Long id : result) {
      deleteEvent(id);
    }
  }

  private List<Long> getChildEvents(long eventId) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getChildEvents", Long.class);
    query.setParameter("parentEventId", eventId);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  private List<Long> getCalendarEventIds(long calendarId) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaEvent.getCalendarEventIds", Long.class);
    query.setParameter("calendarId", calendarId);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  private void verifyLimit(Date endDate, int limit) {
    if (limit <= 0 && endDate == null) {
      throw new IllegalStateException("Limit of events to retrieve is missing, whether us endDate or limit parameters");
    }
  }

}
