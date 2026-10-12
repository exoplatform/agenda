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
package org.exoplatform.agenda.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * The attendees of one date of a series are the rows whose range holds it,
 * from inclusive to until exclusive, however many rows the list holds.
 */
class EventAttendeeListTest {

  private static final ZonedDateTime FIRST  = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime SECOND = FIRST.plusDays(1);

  private static final ZonedDateTime THIRD  = FIRST.plusDays(2);

  /**
   * A list down to one row, from a later date, as when the space that invited
   * the series is removed after a member answered from that date: the row is
   * none of an earlier date's attendees.
   */
  @Test
  void aLoneRowIsNotAnAttendeeOfADateBeforeItsRange() {
    EventAttendee row = row(SECOND, null);
    EventAttendeeList list = new EventAttendeeList(List.of(row));

    assertEquals(List.of(), list.getEventAttendees(FIRST));
    assertEquals(List.of(row), list.getEventAttendees(SECOND));
    assertEquals(List.of(row), list.getEventAttendees(THIRD));
    assertEquals(List.of(row), list.getEventAttendees((ZonedDateTime) null));
  }

  /** A lone row capped at a date is none of that date's attendees. */
  @Test
  void aLoneRowIsNotAnAttendeeOfTheDateItIsCappedAt() {
    EventAttendee row = row(null, SECOND);
    EventAttendeeList list = new EventAttendeeList(List.of(row));

    assertEquals(List.of(row), list.getEventAttendees(FIRST));
    assertEquals(List.of(), list.getEventAttendees(SECOND));
  }

  private static EventAttendee row(ZonedDateTime from, ZonedDateTime until) {
    return new EventAttendee(1L, 10L, 100L, from, until, EventAttendeeResponse.ACCEPTED, null);
  }
}
