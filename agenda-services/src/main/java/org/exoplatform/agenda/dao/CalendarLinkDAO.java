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

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.exoplatform.agenda.entity.CalendarLinkEntity;

/**
 * Reads and writes calendar links. The two single-row lookups hit a unique
 * column and need no page; the listing by owners takes one.
 */
public interface CalendarLinkDAO extends JpaRepository<CalendarLinkEntity, Long> {

  /**
   * Finds the link of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return the link, or null when the calendar has none
   */
  CalendarLinkEntity findByCalendarId(long calendarId);

  /**
   * Finds the link a token digest opens.
   *
   * @param tokenHash lowercase hexadecimal SHA-256 digest of a token
   * @return the link, or null when no link carries that digest
   */
  CalendarLinkEntity findByTokenHash(String tokenHash);

  /**
   * Finds the links of the calendars some identities own, in one statement: the
   * links joined to their calendars and filtered on the calendars' owners,
   * newest first.
   *
   * @param ownerIds identity identifiers owning the calendars, never empty
   * @param pageable the page to read
   * @return the links, newest first
   */
  @Query("SELECT link FROM AgendaCalendarLink link, AgendaCalendar calendar"
      + " WHERE calendar.id = link.calendarId AND calendar.ownerId IN (:ownerIds)"
      + " ORDER BY link.createdDate DESC, link.id DESC")
  List<CalendarLinkEntity> findByCalendarOwnerIds(@Param("ownerIds") Collection<Long> ownerIds, Pageable pageable);

}
