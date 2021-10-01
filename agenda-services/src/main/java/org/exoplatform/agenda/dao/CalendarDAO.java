// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import java.util.*;

import javax.persistence.TypedQuery;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class CalendarDAO extends GenericDAOJPAImpl<CalendarEntity, Long> {

  @Override
  @ExoTransactional
  public void delete(CalendarEntity entity) {
    super.delete(entity);
  }

  @Override
  @ExoTransactional
  public CalendarEntity create(CalendarEntity entity) {
    entity.setCreatedDate(new Date());
    entity.setUpdatedDate(null);
    return super.create(entity);
  }

  @Override
  @ExoTransactional
  public CalendarEntity update(CalendarEntity entity) {
    entity.setUpdatedDate(new Date());
    return super.update(entity);
  }

  public List<Long> getCalendarIdsByOwnerIds(int offset, int limit, Long... ownerIds) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaCalendar.getCalendarIdsByOwnerIds",
                                                                 Long.class);
    query.setParameter("ownerIds", Arrays.asList(ownerIds));
    query.setFirstResult(offset);
    query.setMaxResults(limit);
    List<Long> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countCalendarsByOwnerIds(Long... ownerIds) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaCalendar.countCalendarsByOwnerIds",
                                                                 Long.class);
    query.setParameter("ownerIds", Arrays.asList(ownerIds));
    Long count = query.getSingleResult();
    return count == null ? 0 : count.intValue();
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteAll(List<CalendarEntity> entities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createAll(List<CalendarEntity> entities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateAll(List<CalendarEntity> entities) {
    throw new UnsupportedOperationException();
  }
}
