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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventAttendee;

/**
 * The seat rule's counts, decision, stretches and pieces, on rows built in
 * memory.
 */
class SeatRuleTest {

  private static final ZoneId        PARIS = ZoneId.of("Europe/Paris");

  private static final ZonedDateTime D1    = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime D2    = D1.plusDays(1);

  private static final ZonedDateTime D3    = D1.plusDays(2);

  private static final ZonedDateTime D5    = D1.plusDays(4);

  private static final ZonedDateTime D6    = D1.plusDays(5);

  private static final ZonedDateTime D7    = D1.plusDays(6);

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

  /**
   * A range is cut at the bounds of the Yes rows that fall strictly inside
   * it, the answering attendee's own included; a bound at the range's start,
   * or written in another zone for the same instant, cuts once.
   */
  @Test
  void aRangeIsCutAtTheBoundsOfTheYesRowsInsideIt() {
    List<EventAttendee> rows = List.of(row(1, EventAttendeeResponse.ACCEPTED, null, D3),
                                       row(2, EventAttendeeResponse.ACCEPTED, D2, null),
                                       row(3, EventAttendeeResponse.ACCEPTED, D5, D7),
                                       row(4, EventAttendeeResponse.ACCEPTED, D3.withZoneSameInstant(PARIS), null));

    assertEquals(List.of(new SeatStretch(D2, D3), new SeatStretch(D3, D5), new SeatStretch(D5, D7), new SeatStretch(D7, null)),
                 SeatRule.stretches(rows, D2, null));
    assertEquals(List.of(new SeatStretch(null, D2),
                         new SeatStretch(D2, D3),
                         new SeatStretch(D3, D5),
                         new SeatStretch(D5, D6)),
                 SeatRule.stretches(rows, null, D6));
    assertEquals(List.of(new SeatStretch(D3, D5)), SeatRule.stretches(rows, D3, D5));
  }

  /**
   * A stretch with no counted date takes the decision of the counted stretch
   * before it, else of the one after it, and the runs with the same outcome
   * are one piece, carrying its first counted date.
   */
  @Test
  void aStretchWithNoCountedDateTakesTheDecisionBeforeItElseAfterIt() {
    List<SeatStretch> stretches = List.of(new SeatStretch(null, D2),
                                          new SeatStretch(D2, D3),
                                          new SeatStretch(D3, D5),
                                          new SeatStretch(D5, D6),
                                          new SeatStretch(D6, D7),
                                          new SeatStretch(D7, null));
    Map<SeatStretch, ZonedDateTime> countedDates = Map.of(stretches.get(1), D2, stretches.get(4), D6);
    Map<ZonedDateTime, EventAttendeeResponse> decisions = Map.of(D2,
                                                                 EventAttendeeResponse.ACCEPTED,
                                                                 D6,
                                                                 EventAttendeeResponse.WAITING);

    List<SeatPiece> pieces = SeatRule.pieces(stretches, countedDates::get, decisions::get, stretch -> false, EventAttendeeResponse.DECLINED);

    assertEquals(List.of(new SeatPiece(null, D6, EventAttendeeResponse.ACCEPTED, D2),
                         new SeatPiece(D6, null, EventAttendeeResponse.WAITING, D6)),
                 pieces);
  }

  /**
   * Consecutive counted stretches with the same outcome are one piece, and a
   * range with no counted date at all takes the outcome given for it.
   */
  @Test
  void theRunsWithTheSameOutcomeAreOnePiece() {
    List<SeatStretch> stretches = List.of(new SeatStretch(D1, D2), new SeatStretch(D2, D3), new SeatStretch(D3, null));
    Map<SeatStretch, ZonedDateTime> countedDates = Map.of(stretches.get(0), D1, stretches.get(1), D2, stretches.get(2), D3);
    Map<ZonedDateTime, EventAttendeeResponse> decisions = Map.of(D1,
                                                                 EventAttendeeResponse.WAITING,
                                                                 D2,
                                                                 EventAttendeeResponse.WAITING,
                                                                 D3,
                                                                 EventAttendeeResponse.ACCEPTED);

    assertEquals(List.of(new SeatPiece(D1, D3, EventAttendeeResponse.WAITING, D1),
                         new SeatPiece(D3, null, EventAttendeeResponse.ACCEPTED, D3)),
                 SeatRule.pieces(stretches, countedDates::get, decisions::get, stretch -> false, EventAttendeeResponse.DECLINED));
    assertEquals(List.of(new SeatPiece(D1, null, EventAttendeeResponse.DECLINED, null)),
                 SeatRule.pieces(stretches, stretch -> null, decisions::get, stretch -> false, EventAttendeeResponse.DECLINED));
  }

  /**
   * A stretch with no counted date keeps the seat the attendee held over it,
   * whatever its neighbours decide; one the attendee did not hold still takes
   * the decision of the counted stretch before it, else after it, never the
   * seat kept; with no counted stretch at all, it takes the outcome given for
   * the range. Whether a seat was held is not read for a counted stretch,
   * decided at its date.
   */
  @Test
  void aStretchWithNoCountedDateKeepsTheSeatTheAttendeeHeldOverIt() {
    List<SeatStretch> stretches = List.of(new SeatStretch(null, D2),
                                          new SeatStretch(D2, D3),
                                          new SeatStretch(D3, D5),
                                          new SeatStretch(D5, D6),
                                          new SeatStretch(D6, null));
    Map<SeatStretch, ZonedDateTime> countedDates = Map.of(stretches.get(1), D2, stretches.get(4), D6);
    Map<ZonedDateTime, EventAttendeeResponse> decisions = Map.of(D2,
                                                                 EventAttendeeResponse.WAITING,
                                                                 D6,
                                                                 EventAttendeeResponse.ACCEPTED);
    Set<SeatStretch> held = Set.of(stretches.get(0), stretches.get(1), stretches.get(2));

    assertEquals(List.of(new SeatPiece(null, D2, EventAttendeeResponse.ACCEPTED, null),
                         new SeatPiece(D2, D3, EventAttendeeResponse.WAITING, D2),
                         new SeatPiece(D3, D5, EventAttendeeResponse.ACCEPTED, null),
                         new SeatPiece(D5, D6, EventAttendeeResponse.WAITING, null),
                         new SeatPiece(D6, null, EventAttendeeResponse.ACCEPTED, D6)),
                 SeatRule.pieces(stretches, countedDates::get, decisions::get, held::contains, EventAttendeeResponse.DECLINED));

    List<SeatStretch> heldBeforeTheFirstCounted = List.of(new SeatStretch(null, D2),
                                                          new SeatStretch(D2, D3),
                                                          new SeatStretch(D3, null));
    assertEquals(List.of(new SeatPiece(null, D2, EventAttendeeResponse.WAITING, null),
                         new SeatPiece(D2, D3, EventAttendeeResponse.ACCEPTED, null),
                         new SeatPiece(D3, null, EventAttendeeResponse.WAITING, D3)),
                 SeatRule.pieces(heldBeforeTheFirstCounted,
                                 stretch -> D3.equals(stretch.from()) ? D3 : null,
                                 date -> EventAttendeeResponse.WAITING,
                                 stretch -> D2.equals(stretch.from()),
                                 EventAttendeeResponse.DECLINED));

    List<SeatStretch> noneCounted = List.of(new SeatStretch(null, D2), new SeatStretch(D2, null));
    assertEquals(List.of(new SeatPiece(null, D2, EventAttendeeResponse.ACCEPTED, null),
                         new SeatPiece(D2, null, EventAttendeeResponse.DECLINED, null)),
                 SeatRule.pieces(noneCounted,
                                 stretch -> null,
                                 date -> EventAttendeeResponse.WAITING,
                                 stretch -> stretch.from() == null,
                                 EventAttendeeResponse.DECLINED));
  }

  /**
   * An attendee holds a seat over a stretch when one of their rows answers
   * Yes on one of its dates, from inclusive to until exclusive, a null bound
   * open.
   */
  @Test
  void aSeatIsHeldOverAStretchAYesRowOverlaps() {
    List<EventAttendee> rows = List.of(row(1, EventAttendeeResponse.ACCEPTED, null, D3),
                                       row(1, EventAttendeeResponse.DECLINED, D3, D6),
                                       row(1, EventAttendeeResponse.ACCEPTED, D6, null));

    assertTrue(SeatRule.holdsSeatOver(rows, new SeatStretch(null, D2)));
    assertTrue(SeatRule.holdsSeatOver(rows, new SeatStretch(D2, D3)));
    assertFalse(SeatRule.holdsSeatOver(rows, new SeatStretch(D3, D5)));
    assertFalse(SeatRule.holdsSeatOver(rows, new SeatStretch(D5, D6)));
    assertTrue(SeatRule.holdsSeatOver(rows, new SeatStretch(D7, null)));
    assertFalse(SeatRule.holdsSeatOver(List.of(row(1, EventAttendeeResponse.WAITING, null, null)), new SeatStretch(null, null)));
  }

  /**
   * A piece keeps an answer date only where the stored answer does not
   * change: the attendee's rows cover its whole range with no gap and all
   * hold its answer, and then the earliest of their dates is kept, a null
   * one first. The first date of the series is read only for a piece with no
   * lower bound whose first row has one.
   */
  @Test
  void aPieceKeepsTheEarliestDateOnlyWhereTheAnswerDoesNotChange() {
    ZonedDateTime now = D7.plusDays(30);
    ZonedDateTime early = D1.minusDays(10);
    ZonedDateTime late = D1.minusDays(5);
    SeatPiece fromD2 = new SeatPiece(D2, null, EventAttendeeResponse.WAITING, D2);
    Supplier<ZonedDateTime> unread = () -> {
      throw new AssertionError("The first date of the series is not read");
    };

    // Covered by two waiting rows, the capped row before the piece left out
    assertEquals(early,
                 SeatRule.pieceResponseDate(fromD2,
                                            List.of(row(1, EventAttendeeResponse.DECLINED, null, D2, D1),
                                                    row(1, EventAttendeeResponse.WAITING, D2, D5, late),
                                                    row(1, EventAttendeeResponse.WAITING, D5, null, early)),
                                            unread,
                                            now));
    // A null date is the earliest
    assertNull(SeatRule.pieceResponseDate(fromD2,
                                          List.of(row(1, EventAttendeeResponse.WAITING, D2, D5, late),
                                                  row(1, EventAttendeeResponse.WAITING, D5, null, null)),
                                          unread,
                                          now));
    // The answer changes on part of the range
    assertEquals(now,
                 SeatRule.pieceResponseDate(fromD2,
                                            List.of(row(1, EventAttendeeResponse.WAITING, D2, D5, late),
                                                    row(1, EventAttendeeResponse.DECLINED, D5, null, early)),
                                            unread,
                                            now));
    // No row over part of the range
    assertEquals(now,
                 SeatRule.pieceResponseDate(fromD2,
                                            List.of(row(1, EventAttendeeResponse.WAITING, D3, null, early)),
                                            unread,
                                            now));
    assertEquals(now, SeatRule.pieceResponseDate(fromD2, List.of(), unread, now));
    // A bounded piece needs its range covered only
    assertEquals(late,
                 SeatRule.pieceResponseDate(new SeatPiece(D2, D5, EventAttendeeResponse.WAITING, D2),
                                            List.of(row(1, EventAttendeeResponse.WAITING, null, D6, late),
                                                    row(1, EventAttendeeResponse.DECLINED, D6, null, early)),
                                            unread,
                                            now));
  }

  /**
   * A piece with no lower bound is covered from the first date of the
   * series: a row starting there leaves no gap, a row starting later does.
   */
  @Test
  void aPieceWithNoLowerBoundIsCoveredFromTheFirstDateOfTheSeries() {
    ZonedDateTime now = D7.plusDays(30);
    ZonedDateTime late = D1.minusDays(5);
    SeatPiece every = new SeatPiece(null, null, EventAttendeeResponse.ACCEPTED, D1);
    List<EventAttendee> fromFirstDate = List.of(row(1, EventAttendeeResponse.ACCEPTED, D1, null, late));

    assertEquals(late, SeatRule.pieceResponseDate(every, fromFirstDate, () -> D1, now));
    assertEquals(now, SeatRule.pieceResponseDate(every, fromFirstDate, () -> D1.minusDays(1), now));
    assertEquals(late,
                 SeatRule.pieceResponseDate(every,
                                            List.of(row(1, EventAttendeeResponse.ACCEPTED, null, null, late)),
                                            () -> {
                                              throw new AssertionError("The first date of the series is not read");
                                            },
                                            now));
  }

  private static EventAttendee row(long identityId, EventAttendeeResponse response, ZonedDateTime from, ZonedDateTime until) {
    return new EventAttendee(identityId, 1, identityId, from, until, response, null);
  }

  private static EventAttendee row(long identityId,
                                   EventAttendeeResponse response,
                                   ZonedDateTime from,
                                   ZonedDateTime until,
                                   ZonedDateTime responseDate) {
    return new EventAttendee(identityId, 1, identityId, from, until, response, responseDate);
  }
}
