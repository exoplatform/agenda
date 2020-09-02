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
