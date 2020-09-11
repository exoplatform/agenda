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

import javax.persistence.TypedQuery;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class CalendarDAO extends GenericDAOJPAImpl<CalendarEntity, Long> {

  private EventDAO eventDAO;

  public CalendarDAO(EventDAO eventDAO) {
    this.eventDAO = eventDAO;
  }

  @Override
  @ExoTransactional
  public void delete(CalendarEntity entity) {
    this.eventDAO.deleteCalendarEvents(entity.getId());
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
