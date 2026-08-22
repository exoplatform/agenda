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

import jakarta.persistence.TypedQuery;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class CalendarDAO extends GenericDAOJPAImpl<CalendarEntity, Long> {

  @Override
  @ExoTransactional
  public void delete(CalendarEntity entity) {
    super.delete(entity);
  }

  /**
   * {@inheritDoc} In addition to the generic creation, initializes the
   * creation date and generates a stable synchronization identifier
   * ({@code SYNC_UID}) when none was provided, so that every stored calendar
   * carries an environment-independent unique identifier.
   */
  @Override
  @ExoTransactional
  public CalendarEntity create(CalendarEntity entity) {
    entity.setCreatedDate(new Date());
    entity.setUpdatedDate(null);
    if (entity.getSyncUid() == null) {
      entity.setSyncUid(UUID.randomUUID().toString());
    }
    return super.create(entity);
  }

  /**
   * Updates a calendar, giving it a {@code SYNC_UID} when it still has none.
   *
   * <p>
   * The same guard {@link #create(CalendarEntity)} applies, for the calendars
   * that predate the column: one restored from an old backup after the
   * migration ran would otherwise carry no anchor and be invisible to every
   * integration that binds by it. This complements the migration rather than
   * replacing it — the calendar that matters most is the system one, which is
   * almost never updated.
   */
  @Override
  @ExoTransactional
  public CalendarEntity update(CalendarEntity entity) {
    entity.setUpdatedDate(new Date());
    if (entity.getSyncUid() == null) {
      entity.setSyncUid(UUID.randomUUID().toString());
    }
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

  /**
   * Retrieves the technical identifier of the system (default) calendar of a
   * given owner. When several system calendars exist for the same owner (a
   * data anomaly), the oldest one is returned to keep the result
   * deterministic.
   *
   * @param ownerId technical identifier of the calendar owner identity
   * @return technical identifier of the owner's system calendar, or
   *         {@code null} when the owner has no system calendar
   */
  public Long getSystemCalendarIdByOwnerId(long ownerId) {
    TypedQuery<Long> query = getEntityManager().createNamedQuery("AgendaCalendar.getSystemCalendarIdsByOwnerId",
                                                                 Long.class);
    query.setParameter("ownerId", ownerId);
    query.setMaxResults(1);
    List<Long> resultList = query.getResultList();
    return resultList == null || resultList.isEmpty() ? null : resultList.get(0);
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
