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

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

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
