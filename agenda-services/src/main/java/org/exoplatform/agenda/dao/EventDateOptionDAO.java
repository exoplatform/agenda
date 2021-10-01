// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;

import org.exoplatform.agenda.entity.EventDateOptionEntity;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class EventDateOptionDAO extends GenericDAOJPAImpl<EventDateOptionEntity, Long> {

  private EventDateVoteDAO dateVoteDAO;

  public EventDateOptionDAO(EventDateVoteDAO dateVoteDAO) {
    this.dateVoteDAO = dateVoteDAO;
  }

  public void deleteEventDateOptions(long eventId) {
    List<EventDateOptionEntity> dateOptionEntities = findDateOptionsByEventId(eventId);
    for (EventDateOptionEntity eventDateOptionEntity : dateOptionEntities) {
      delete(eventDateOptionEntity);
    }
  }

  @Override
  public void delete(EventDateOptionEntity entity) {
    dateVoteDAO.deleteDateOptionVotes(entity.getId());
    super.delete(entity);
  }

  public List<EventDateOptionEntity> findDateOptionsByEventId(long eventId) {
    TypedQuery<EventDateOptionEntity> query =
                                            getEntityManager().createNamedQuery("AgendaEventDateOption.findDateOptionsByEventId",
                                                                                EventDateOptionEntity.class);
    query.setParameter("eventId", eventId);
    List<EventDateOptionEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
