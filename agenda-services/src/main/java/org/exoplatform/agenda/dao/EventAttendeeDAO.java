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

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventAttendeeDAO extends GenericDAOJPAImpl<EventAttendeeEntity, Long> {

  /**
   * Deletes an attendee row and sends the delete at once. A decide step that
   * replaces an answer deletes a row and inserts one with the same key in its
   * one transaction: without the flush, the insert would reach the database
   * first. On an identity column (MySQL) Hibernate sends the insert when the
   * row is persisted, and on a sequence it sends a flush's inserts before its
   * deletes.
   *
   * @param entity the attendee row to delete
   */
  @Override
  @ExoTransactional
  public void delete(EventAttendeeEntity entity) {
    super.delete(entity);
    getEntityManager().flush();
  }

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

  /**
   * Reads the rows of an event that have one of the given answers, as scalar
   * values: no entity is loaded, so the rows a seat decision counts do not
   * enter the persistence context it writes through.
   *
   * @param eventId event identifier
   * @param responses the answers to read
   * @return per row: id, identity id, from occurrence id, until occurrence id,
   *         answer and answer date, in that order
   */
  public List<Object[]> getSeatRows(long eventId, EventAttendeeResponse... responses) {
    TypedQuery<Object[]> query = getEntityManager().createNamedQuery("AgendaEventAttendee.getSeatRows", Object[].class);
    query.setParameter("eventId", eventId);
    query.setParameter("responses", Arrays.asList(responses));
    List<Object[]> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  /**
   * Reads the waiting rows of an event in the order they are promoted: the
   * earliest answer date first, then the lowest id. Every waiting row is
   * written with its answer date, so none sorts on a null.
   *
   * @param eventId event identifier
   * @return the rows as {@link #getSeatRows(long, EventAttendeeResponse...)}
   *         returns them
   */
  public List<Object[]> getWaitingAttendees(long eventId) {
    TypedQuery<Object[]> query = getEntityManager().createNamedQuery("AgendaEventAttendee.getWaitingAttendees",
                                                                     Object[].class);
    query.setParameter("eventId", eventId);
    query.setParameter("response", EventAttendeeResponse.WAITING);
    List<Object[]> resultList = query.getResultList();
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
