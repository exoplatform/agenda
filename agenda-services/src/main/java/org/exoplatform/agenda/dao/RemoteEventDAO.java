// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
    } catch (NoResultException e) {// NOSONAR : normal to not log this and not
                                   // rethrow it
      return null;
    } catch (NonUniqueResultException e) {
      LOG.warn("More than one result is returned for remote events for event {} and user {}", eventId, identityId, e);
      return query.getResultList().get(0);
    }
  }

}
