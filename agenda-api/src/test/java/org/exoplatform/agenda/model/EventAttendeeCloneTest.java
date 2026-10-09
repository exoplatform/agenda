/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.agenda.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * {@code EventAttendee.clone()} is a positional constructor call, like
 * {@code Event.clone()}: a field it forgets is lost by every copy of an
 * attendee row — for the response date, the place of a waiting attendee in the
 * list of a date materialised from its series.
 */
class EventAttendeeCloneTest {

  /**
   * A clone carries every field of the row it copies. The two range bounds and
   * the response date are three same-typed neighbours, so each gets its own
   * value: equal values would hide a transposition between them.
   */
  @Test
  void aCloneCarriesEveryField() {
    EventAttendee attendee = new EventAttendee(5,
                                               7,
                                               9,
                                               ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC),
                                               ZonedDateTime.of(2026, 9, 28, 9, 0, 0, 0, ZoneOffset.UTC),
                                               EventAttendeeResponse.WAITING,
                                               ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, ZoneOffset.UTC));

    EventAttendee copy = attendee.clone();

    assertEquals(attendee, copy);
    assertEquals(attendee.getResponseDate(), copy.getResponseDate(), "the copy keeps its place in a waiting list");
  }

  /**
   * The constructor every caller used before the response date existed still
   * builds the same row, with no response date.
   */
  @Test
  void theConstructorWithoutResponseDateLeavesItEmpty() {
    ZonedDateTime from = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC);

    EventAttendee attendee = new EventAttendee(5, 7, 9, from, null, EventAttendeeResponse.ACCEPTED);

    assertEquals(from, attendee.getFromOccurrenceId());
    assertEquals(EventAttendeeResponse.ACCEPTED, attendee.getResponse());
    assertNull(attendee.getResponseDate());
  }

}
