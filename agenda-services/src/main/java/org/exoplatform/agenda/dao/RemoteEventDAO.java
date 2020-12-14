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

import javax.persistence.*;

import org.exoplatform.agenda.entity.RemoteEventEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class RemoteEventDAO extends GenericDAOJPAImpl<RemoteEventEntity, Long> {

  private static final Log LOG = ExoLogger.getLogger(RemoteEventDAO.class);

  @ExoTransactional
  public void deleteCalendarRemoteEvents(long calendarId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaRemoteEvent.deleteCalendarRemoteEvents");
    deleteEventsQuery.setParameter("calendarId", calendarId);
    deleteEventsQuery.executeUpdate();
  }

  @ExoTransactional
  public void deleteRemoteEvents(long eventId) {
    Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaRemoteEvent.deleteRemoteEvents");
    deleteEventsQuery.setParameter("eventId", eventId);
    deleteEventsQuery.executeUpdate();
  }

  public RemoteEventEntity findRemoteEvent(long eventId, long identityId) {
    TypedQuery<RemoteEventEntity> query =
                                        getEntityManager().createNamedQuery("AgendaRemoteEvent.findRemoteEventByEventIdAndIdentityId",
                                                                            RemoteEventEntity.class);
    query.setParameter("eventId", eventId);
    query.setParameter("identityId", identityId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {
      return null;
    } catch (NonUniqueResultException e) {
      LOG.warn("More than one result is returned for remote events for event {} and user {}", eventId, identityId);
      return query.getResultList().get(0);
    }
  }

}
