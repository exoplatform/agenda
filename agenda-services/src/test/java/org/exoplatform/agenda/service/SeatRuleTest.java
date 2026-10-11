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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventAttendee;

/**
 * The seat rule's counts and decision, on rows built in memory.
 */
class SeatRuleTest {

  private static final ZonedDateTime D1 = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime D2 = D1.plusDays(1);

  private static final ZonedDateTime D3 = D1.plusDays(2);

  /**
   * A list of one row whose range starts after the date counts nothing at
   * that date: the count filters by range itself, where
   * {@code EventAttendeeList#getEventAttendees(ZonedDateTime)} would return
   * the lone row unfiltered.
   */
  @Test
  void aLoneRowStartingLaterIsNotCountedAtAnEarlierDate() {
    List<EventAttendee> rows = List.of(row(1, EventAttendeeResponse.ACCEPTED, D3, null));

    assertEquals(0, SeatRule.acceptedAt(rows, D1, 0));
    assertEquals(1, SeatRule.acceptedAt(rows, D3, 0));
  }

  /**
   * A row is valid from its first date included to its until date excluded,
   * and a row with no range at every date.
   */
  @Test
  void aRowCoversFromItsStartIncludedToItsUntilExcluded() {
    EventAttendee ranged = row(1, EventAttendeeResponse.ACCEPTED, D1, D2);

    assertTrue(SeatRule.coversDate(ranged, D1));
    assertFalse(SeatRule.coversDate(ranged, D2));
    assertFalse(SeatRule.coversDate(ranged, D1.minusDays(1)));
    assertTrue(SeatRule.coversDate(row(2, EventAttendeeResponse.ACCEPTED, null, null), D3));
    assertTrue(SeatRule.coversDate(ranged, null));
  }

  /**
   * The count takes the Yes answers only, and leaves out the attendee who
   * answers, whose own seat is not counted against them.
   */
  @Test
  void theCountTakesTheYesOfTheOthers() {
    List<EventAttendee> rows = List.of(row(1, EventAttendeeResponse.ACCEPTED, null, null),
                                       row(2, EventAttendeeResponse.ACCEPTED, null, null),
                                       row(3, EventAttendeeResponse.WAITING, null, null),
                                       row(4, EventAttendeeResponse.TENTATIVE, null, null));

    assertEquals(2, SeatRule.acceptedAt(rows, null, 0));
    assertEquals(1, SeatRule.acceptedAt(rows, null, 2));
  }

  /**
   * A Yes, or a place on the waiting list, is a Yes while a seat is free and
   * waits once none is; an attendee already seated keeps their seat; a Maybe
   * or a No never takes one; no limit always has a seat.
   */
  @Test
  void aYesTakesAFreeSeatAndWaitsOtherwise() {
    assertEquals(EventAttendeeResponse.ACCEPTED, SeatRule.decide(EventAttendeeResponse.ACCEPTED, null, 2, 1));
    assertEquals(EventAttendeeResponse.WAITING, SeatRule.decide(EventAttendeeResponse.ACCEPTED, null, 2, 2));
    assertEquals(EventAttendeeResponse.ACCEPTED, SeatRule.decide(EventAttendeeResponse.WAITING, null, 2, 1));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 SeatRule.decide(EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.ACCEPTED, 2, 2));
    assertEquals(EventAttendeeResponse.WAITING,
                 SeatRule.decide(EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.WAITING, 2, 2));
    assertEquals(EventAttendeeResponse.TENTATIVE, SeatRule.decide(EventAttendeeResponse.TENTATIVE, null, 2, 2));
    assertEquals(EventAttendeeResponse.DECLINED, SeatRule.decide(EventAttendeeResponse.DECLINED, null, 2, 2));
    assertEquals(EventAttendeeResponse.ACCEPTED, SeatRule.decide(EventAttendeeResponse.WAITING, null, 0, 50));
  }

  private static EventAttendee row(long identityId, EventAttendeeResponse response, ZonedDateTime from, ZonedDateTime until) {
    return new EventAttendee(identityId, 1, identityId, from, until, response, null);
  }
}
