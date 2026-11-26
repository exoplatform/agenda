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
   * Lists the meeting links of a given conference type holding a path segment,
   * each with the series of the event holding it: the event itself when it is
   * not an occurrence. A link holds the segment when the segment is followed by
   * the end of the link, a query string, a fragment or another segment; a link
   * where it is only the beginning of a longer segment is not read, so it does
   * not count toward the limit. A link a series copies onto its stored
   * occurrences comes back once. The segment's LIKE wildcards are escaped with
   * <code>!</code>, the one escape character every supported engine reads the
   * same way (MySQL reads a backslash in a literal as an escape of its own).
   * The rows come by series, the earliest created first, so the limit keeps
   * the series created first, whenever their links were saved.
   *
   * @param type conference type, the web conferencing provider type
   * @param escapedSegment the segment, such as <code>/</code> and a call
   *          identifier, its LIKE wildcards escaped with <code>!</code>
   * @param limit maximum number of rows to return
   * @return distinct rows of { url, series or event id }, possibly empty
   */
  public List<Object[]> findByTypeAndUrlSegment(String type, String escapedSegment, int limit) {
    TypedQuery<Object[]> query = getEntityManager().createNamedQuery("AgendaEventConference.findByTypeAndUrlSegment",
                                                                     Object[].class);
    query.setParameter("type", type);
    query.setParameter("atEnd", "%" + escapedSegment);
    query.setParameter("beforeQuery", "%" + escapedSegment + "?%");
    query.setParameter("beforeFragment", "%" + escapedSegment + "#%");
    query.setParameter("beforePath", "%" + escapedSegment + "/%");
    query.setMaxResults(limit);
    List<Object[]> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
