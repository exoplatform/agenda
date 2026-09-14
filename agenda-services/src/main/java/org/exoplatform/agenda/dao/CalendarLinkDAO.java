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

import org.springframework.data.jpa.repository.JpaRepository;

import org.exoplatform.agenda.entity.CalendarLinkEntity;

/**
 * Reads and writes calendar links. Both lookups hit a unique column, so each
 * answers at most one row and needs no page.
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

}
