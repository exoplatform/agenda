// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import java.util.*;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.exoplatform.agenda.entity.EventReminderEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventReminderDAO extends GenericDAOJPAImpl<EventReminderEntity, Long> {

  @ExoTransactional
  public void deleteCalendarReminders(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventReminder.deleteCalendarReminders");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  @ExoTransactional
  public void deleteEventReminders(long eventId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventReminder.deleteEventReminders");
    deleteEventsQuery.setParameter("eventId", eventId);
    deleteEventsQuery.executeUpdate();
  }

  public List<EventReminderEntity> getEventReminders(long eventId, long userId) {
    TypedQuery<EventReminderEntity> query =
                                          getEntityManager().createNamedQuery("AgendaEventReminder.getEventRemindersByEventIdAndUserId",
                                                                              EventReminderEntity.class);
    query.setParameter("eventId", eventId);
    query.setParameter("userId", userId);
    List<EventReminderEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<EventReminderEntity> getEventReminders(long eventId) {
    TypedQuery<EventReminderEntity> query =
                                          getEntityManager().createNamedQuery("AgendaEventReminder.getEventRemindersByEventId",
                                                                              EventReminderEntity.class);
    query.setParameter("eventId", eventId);
    List<EventReminderEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<EventReminderEntity> getEventReminders(Date start, Date end) {
    TypedQuery<EventReminderEntity> query = getEntityManager().createNamedQuery("AgendaEventReminder.getEventRemindersByPeriod",
                                                                                EventReminderEntity.class);
    query.setParameter("start", start);
    query.setParameter("end", end);
    List<EventReminderEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
