// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import javax.persistence.Query;

import org.exoplatform.agenda.entity.EventRecurrenceEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventRecurrenceDAO extends GenericDAOJPAImpl<EventRecurrenceEntity, Long> {

  @ExoTransactional
  public void deleteCalendarRecurrences(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventRecurrence.deleteCalendarRecurrences");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  @ExoTransactional
  public void deleteEventRecurrences(long eventId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventRecurrence.deleteEventRecurrences");
    deleteEventsQuery.setParameter("eventId", eventId);
    deleteEventsQuery.executeUpdate();
  }

}
