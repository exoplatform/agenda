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

import java.util.Collections;
import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

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

}
