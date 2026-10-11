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
package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;
import java.util.Collection;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;

/**
 * The seat rule of an event with a capacity limit, on the rows of one pool:
 * at every date the pool covers, the rows answering Yes number at most its
 * capacity, and nobody waits while a seat is free.
 * <p>
 * Given a date, the counts filter the rows by their range themselves, from
 * inclusive to until exclusive, the way {@code EventAttendeeList} does: never
 * through its shortcut, which returns a list of one row unfiltered. Given
 * none, as on a pool of one date, every row counts.
 */
final class SeatRule {

  private SeatRule() {
  }

  /**
   * @param event a stored row of an event
   * @return its capacity, 0 for no limit
   */
  static int capacityOf(Event event) {
    Integer capacity = event.getCapacity();
    return capacity == null ? 0 : capacity;
  }

  /**
   * @param response an answer
   * @return whether it asks for a seat: a Yes, or a place on the waiting
   *         list, which is a Yes given on a full date
   */
  static boolean asksForSeat(EventAttendeeResponse response) {
    return response == EventAttendeeResponse.ACCEPTED || response == EventAttendeeResponse.WAITING;
  }

  /**
   * @param row an attendee row
   * @param date an occurrence id, or null to take every row as valid, as on a
   *          pool of one date
   * @return whether the row is valid at the date: from inclusive, until
   *         exclusive, any row when there is no date
   */
  static boolean coversDate(EventAttendee row, ZonedDateTime date) {
    if (date == null) {
      return true;
    }
    ZonedDateTime from = row.getFromOccurrenceId();
    ZonedDateTime until = row.getUntilOccurrenceId();
    return (from == null || !from.isAfter(date)) && (until == null || until.isAfter(date));
  }

  /**
   * @param rows rows of one pool
   * @param date the date counted, or null to count every row, as on a pool of
   *          one date
   * @param excludedIdentityId an identity whose rows are not counted, the one
   *          answering; 0 to count every row
   * @return how many of the rows answer Yes at the date
   */
  static int acceptedAt(Collection<EventAttendee> rows, ZonedDateTime date, long excludedIdentityId) {
    return (int) rows.stream()
                     .filter(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED)
                     .filter(row -> row.getIdentityId() != excludedIdentityId)
                     .filter(row -> coversDate(row, date))
                     .count();
  }

  /**
   * @param capacity a pool's capacity, 0 for no limit
   * @param accepted the Yes answers counted at a date
   * @return whether one more Yes fits at that date
   */
  static boolean hasFreeSeat(int capacity, int accepted) {
    return capacity <= 0 || accepted < capacity;
  }

  /**
   * Decides the answer stored for a requested one at a date. A Yes, or a
   * place on the waiting list, is stored as a Yes when a seat is free or when
   * the attendee already holds one, as a place on the waiting list otherwise;
   * any other answer is stored as given.
   *
   * @param requested the answer given
   * @param held the attendee's answer stored at the date before this one, or
   *          null
   * @param capacity the pool's capacity, 0 for no limit
   * @param acceptedByOthers the Yes answers of the other attendees at the date
   * @return the answer to store
   */
  static EventAttendeeResponse decide(EventAttendeeResponse requested,
                                      EventAttendeeResponse held,
                                      int capacity,
                                      int acceptedByOthers) {
    if (!asksForSeat(requested)) {
      return requested;
    }
    if (held == EventAttendeeResponse.ACCEPTED || hasFreeSeat(capacity, acceptedByOthers)) {
      return EventAttendeeResponse.ACCEPTED;
    }
    return EventAttendeeResponse.WAITING;
  }
}
