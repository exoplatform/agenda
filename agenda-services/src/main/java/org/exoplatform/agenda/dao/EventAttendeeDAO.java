// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import java.util.*;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventAttendeeDAO extends GenericDAOJPAImpl<EventAttendeeEntity, Long> {

  @ExoTransactional
  public void deleteCalendarAttendees(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventAttendee.deleteCalendarAttendees");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  @ExoTransactional
  public void deleteEventAttendees(long eventId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventAttendee.deleteEventAttendees");
    deleteEventsQuery.setParameter("eventId", eventId);
    deleteEventsQuery.executeUpdate();
  }

  public List<EventAttendeeEntity> getEventAttendees(long eventId) {
    TypedQuery<EventAttendeeEntity> query = getEntityManager().createNamedQuery("AgendaEventAttendee.getEventAttendeesByEventId",
                                                                                EventAttendeeEntity.class);
    query.setParameter("eventId", eventId);
    List<EventAttendeeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<EventAttendeeEntity> getEventAttendeesByResponses(long eventId,
                                                                EventAttendeeResponse... responses) {
    TypedQuery<EventAttendeeEntity> query =
                                          getEntityManager().createNamedQuery("AgendaEventAttendee.getEventAttendeesByEventIdAndByResponses",
                                                                              EventAttendeeEntity.class);
    query.setParameter("eventId", eventId);
    query.setParameter("responses", Arrays.asList(responses));
    List<EventAttendeeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<EventAttendeeEntity> getEventAttendees(long eventId, long identityId) {
    TypedQuery<EventAttendeeEntity> query = getEntityManager().createNamedQuery("AgendaEventAttendee.getEventAttendee",
                                                                                EventAttendeeEntity.class);
    query.setParameter("eventId", eventId);
    query.setParameter("identityId", identityId);
    List<EventAttendeeEntity> result = query.getResultList();
    return result == null ? Collections.emptyList() : result;
  }

}
