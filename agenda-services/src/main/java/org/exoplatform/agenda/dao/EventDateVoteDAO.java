package org.exoplatform.agenda.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.*;

import org.exoplatform.agenda.entity.EventDateVoteEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventDateVoteDAO extends GenericDAOJPAImpl<EventDateVoteEntity, Long> {

  public List<EventDateVoteEntity> findVotersByDateOptionId(Long dateOptionId) {
    TypedQuery<EventDateVoteEntity> query = getEntityManager().createNamedQuery("AgendaEventDateVote.findVotersByDateOptionId",
                                                                                EventDateVoteEntity.class);
    query.setParameter("dateOptionId", dateOptionId);
    List<EventDateVoteEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public EventDateVoteEntity findVoteByOptionAndIdentity(long dateOptionId, long identityId) {
    TypedQuery<EventDateVoteEntity> query = getEntityManager().createNamedQuery("AgendaEventDateVote.findVoteByOptionAndIdentity",
                                                                                EventDateVoteEntity.class);
    query.setParameter("dateOptionId", dateOptionId);
    query.setParameter("identityId", identityId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {// NOSONAR : normal to not log this and not
                                   // rethrow it
      return null;
    }
  }

  @ExoTransactional
  public void deleteDateOptionVotes(Long dateOptionId) {
    Query query = getEntityManager().createNamedQuery("AgendaEventDateVote.deleteVotesByOptionId");
    query.setParameter("dateOptionId", dateOptionId);
    query.executeUpdate();
  }

}
