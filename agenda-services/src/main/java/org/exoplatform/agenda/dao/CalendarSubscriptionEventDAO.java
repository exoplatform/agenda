/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.agenda.entity.CalendarSubscriptionEventEntity;

/**
 * Reads and writes the occurrences subscriptions imported (EXO-90278). A
 * subscription holds a bounded number of them, and every read is paged all the
 * same.
 */
public interface CalendarSubscriptionEventDAO extends JpaRepository<CalendarSubscriptionEventEntity, Long> {

  /**
   * Finds the occurrences a subscription imported.
   *
   * @param subscriptionId technical identifier of the subscription
   * @param pageable the page
   * @return the rows, never null
   */
  List<CalendarSubscriptionEventEntity> findBySubscriptionId(long subscriptionId, Pageable pageable);

  /**
   * Deletes every occurrence row of a subscription; the events themselves are
   * the caller's to delete.
   *
   * @param subscriptionId technical identifier of the subscription
   * @return how many rows were deleted
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AgendaCalendarSubscriptionEvent e WHERE e.subscriptionId = :subscriptionId")
  int deleteBySubscriptionId(@Param("subscriptionId") long subscriptionId);

}
