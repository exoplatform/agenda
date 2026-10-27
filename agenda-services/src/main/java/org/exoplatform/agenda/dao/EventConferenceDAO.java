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

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

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

  /**
   * Lists the meeting links of a given conference type that match a LIKE
   * pattern, each with the series of the event holding it: the event itself
   * when it is not an occurrence. A link a series copies onto its stored
   * occurrences comes back once. The pattern escapes its
   * wildcards with <code>!</code>, the one escape character every supported
   * engine reads the same way (MySQL reads a backslash in a literal as an
   * escape of its own).
   *
   * @param type conference type, the web conferencing provider type
   * @param urlPattern LIKE pattern, wildcards of the searched text escaped
   *          with <code>!</code>
   * @param limit maximum number of rows to return
   * @return distinct rows of { url, series or event id }, possibly empty
   */
  public List<Object[]> findByTypeAndUrlLike(String type, String urlPattern, int limit) {
    TypedQuery<Object[]> query = getEntityManager().createNamedQuery("AgendaEventConference.findByTypeAndUrlLike",
                                                                     Object[].class);
    query.setParameter("type", type);
    query.setParameter("url", urlPattern);
    query.setMaxResults(limit);
    List<Object[]> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
