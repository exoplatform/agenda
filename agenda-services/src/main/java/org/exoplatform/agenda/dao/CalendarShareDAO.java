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

import org.exoplatform.agenda.entity.CalendarShareEntity;

/**
 * Reads and writes calendar shares (EXO-90357). The single-row lookup hits the
 * unique key and needs no page; the listings take one.
 */
public interface CalendarShareDAO extends JpaRepository<CalendarShareEntity, Long> {

  /**
   * Finds the share of a calendar with a colleague.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @return the share, or null when the calendar is not shared with them
   */
  CalendarShareEntity findByCalendarIdAndShareeIdentityId(long calendarId, long shareeIdentityId);

  /**
   * The shares of a calendar, oldest first.
   *
   * @param calendarId technical identifier of the calendar
   * @param pageable the page to read
   * @return the shares
   */
  List<CalendarShareEntity> findByCalendarIdOrderByCreatedDateAscIdAsc(long calendarId, Pageable pageable);

  /**
   * The shares a colleague received, newest first.
   *
   * @param shareeIdentityId identity identifier of the colleague
   * @param pageable the page to read
   * @return the shares
   */
  List<CalendarShareEntity> findByShareeIdentityIdOrderByCreatedDateDescIdDesc(long shareeIdentityId, Pageable pageable);

  /**
   * The identifiers of every calendar shared with a colleague: what the ACL
   * of every read relies on, so ids only, served by the sharee index.
   *
   * @param shareeIdentityId identity identifier of the colleague
   * @return the calendar identifiers
   */
  @Query("SELECT share.calendarId FROM AgendaCalendarShare share WHERE share.shareeIdentityId = :shareeIdentityId")
  List<Long> findCalendarIdsByShareeIdentityId(@Param("shareeIdentityId") long shareeIdentityId);

  /**
   * How many colleagues each calendar of an owner is shared with, in one
   * statement: the shares joined to their calendars and filtered on the
   * calendars' owner, as {@code CalendarLinkDAO.findByCalendarOwnerIds} does.
   *
   * @param ownerId identity identifier owning the calendars
   * @return one {@code [calendarId, count]} row per shared calendar
   */
  @Query("SELECT share.calendarId, COUNT(share) FROM AgendaCalendarShare share, AgendaCalendar calendar"
      + " WHERE calendar.id = share.calendarId AND calendar.ownerId = :ownerId"
      + " GROUP BY share.calendarId")
  List<Object[]> countByCalendarOfOwner(@Param("ownerId") long ownerId);

  /**
   * The shares of every calendar an owner has, delivered through a channel.
   *
   * @param ownerId identity identifier owning the calendars
   * @param deliveredTo the channel
   * @return the shares
   */
  @Query("SELECT share FROM AgendaCalendarShare share, AgendaCalendar calendar"
      + " WHERE calendar.id = share.calendarId AND calendar.ownerId = :ownerId AND share.deliveredTo = :deliveredTo")
  List<CalendarShareEntity> findByCalendarOwnerAndDeliveredTo(@Param("ownerId") long ownerId,
                                                              @Param("deliveredTo") String deliveredTo);

  /**
   * Deletes every share of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return how many rows went
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AgendaCalendarShare share WHERE share.calendarId = :calendarId")
  int deleteByCalendarId(@Param("calendarId") long calendarId);

  /**
   * Deletes every share a colleague received.
   *
   * @param shareeIdentityId identity identifier of the colleague
   * @return how many rows went
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AgendaCalendarShare share WHERE share.shareeIdentityId = :shareeIdentityId")
  int deleteByShareeIdentityId(@Param("shareeIdentityId") long shareeIdentityId);

  /**
   * Deletes every share of every calendar an owner has.
   *
   * @param ownerId identity identifier owning the calendars
   * @return how many rows went
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AgendaCalendarShare share WHERE share.calendarId IN"
      + " (SELECT calendar.id FROM AgendaCalendar calendar WHERE calendar.ownerId = :ownerId)")
  int deleteByCalendarOwner(@Param("ownerId") long ownerId);

}
