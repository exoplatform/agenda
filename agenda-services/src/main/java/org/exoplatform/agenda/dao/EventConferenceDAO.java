// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.exoplatform.agenda.entity.EventConferenceEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventConferenceDAO extends GenericDAOJPAImpl<EventConferenceEntity, Long> {

  @ExoTransactional
  public void deleteCalendarConferences(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventConference.deleteCalendarConferences");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  @ExoTransactional
  public void deleteEventConferences(long eventId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventConference.deleteEventConferences");
    deleteEventsQuery.setParameter("eventId", eventId);
    deleteEventsQuery.executeUpdate();
  }

  public List<EventConferenceEntity> getEventConferences(long eventId) {
    TypedQuery<EventConferenceEntity> query =
                                            getEntityManager().createNamedQuery("AgendaEventConference.getEventConferencesByEventId",
                                                                                EventConferenceEntity.class);
    query.setParameter("eventId", eventId);
    List<EventConferenceEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
