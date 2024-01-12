package org.exoplatform.agenda.dao;

import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import org.exoplatform.agenda.entity.EventDatePollEntity;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventDatePollDAO extends GenericDAOJPAImpl<EventDatePollEntity, Long> {

  public EventDatePollEntity findDatePollByEventId(long eventId) {
    TypedQuery<EventDatePollEntity> query = getEntityManager().createNamedQuery("AgendaEventDatePoll.findDatePollByEventId",
                                                                                EventDatePollEntity.class);
    query.setParameter("eventId", eventId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {// NOSONAR : normal to not log this and not
                                   // rethrow it
      return null;
    }
  }

  public void deleteEventPoll(long eventId) {
    EventDatePollEntity datePollEntity = findDatePollByEventId(eventId);
    if (datePollEntity != null) {
      delete(datePollEntity);
    }
  }

}
