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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
 * <p>
 * On the pool of a series, an answer covers a range of its dates, cut into
 * stretches at the bounds of the rows answering Yes, over which the count
 * stays the same: each stretch is decided at its first counted date, and the
 * runs of stretches with the same outcome are stored as pieces, one row
 * each.
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
   * @param rows an attendee's rows on the pool of a series
   * @param stretch a stretch of its dates
   * @return whether one of the rows answers Yes on a date of the stretch: over
   *         all of it, when the rows answering Yes bound the stretches
   */
  static boolean holdsSeatOver(Collection<EventAttendee> rows, SeatStretch stretch) {
    return rows.stream()
               .anyMatch(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED
                   && overlaps(row, stretch.from(), stretch.until()));
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

  /**
   * Cuts a range of the pool of a series into stretches over which the Yes
   * answers counted stay the same: at the bounds of the rows answering Yes
   * that fall inside the range, those of the attendee answering included, so
   * that whether they hold a seat stays the same too.
   *
   * @param acceptedRows the rows of the pool answering Yes
   * @param from the occurrence id the range starts at, inclusive, or null from
   *          the first date of the series
   * @param until the occurrence id the range ends before, exclusive, or null
   *          when it has no end
   * @return the stretches covering the range, in order
   */
  static List<SeatStretch> stretches(Collection<EventAttendee> acceptedRows, ZonedDateTime from, ZonedDateTime until) {
    Set<ZonedDateTime> bounds = new TreeSet<>(Comparator.comparing(ZonedDateTime::toInstant));
    for (EventAttendee row : acceptedRows) {
      addBound(bounds, row.getFromOccurrenceId(), from, until);
      addBound(bounds, row.getUntilOccurrenceId(), from, until);
    }
    List<SeatStretch> stretches = new ArrayList<>();
    ZonedDateTime start = from;
    for (ZonedDateTime bound : bounds) {
      stretches.add(new SeatStretch(start, bound));
      start = bound;
    }
    stretches.add(new SeatStretch(start, until));
    return stretches;
  }

  /**
   * Decides a range stretch by stretch, then joins the runs of stretches with
   * the same outcome into pieces. A stretch is decided at its first counted
   * date. A stretch with none, every date of it having its own row or being
   * over, decides nothing: it keeps the seat the attendee already held over
   * it, and otherwise takes the decision of the counted stretch before it, or
   * else of the one after it; with no counted stretch at all, a stretch not
   * held takes the outcome given for the range.
   *
   * @param stretches the stretches of the range, in order
   * @param firstCountedDate the first counted date of a stretch, or null when
   *          it has none
   * @param decisionAt the outcome decided at a counted date
   * @param heldOver whether the attendee held a seat over a stretch before
   *          the answer, read for a stretch with no counted date only
   * @param uncounted the outcome of a stretch not held, in a range with no
   *          counted date
   * @return the pieces covering the range, in order, with their first counted
   *         date, null for a piece whose stretches have none
   */
  static List<SeatPiece> pieces(List<SeatStretch> stretches,
                                Function<SeatStretch, ZonedDateTime> firstCountedDate,
                                Function<ZonedDateTime, EventAttendeeResponse> decisionAt,
                                Predicate<SeatStretch> heldOver,
                                EventAttendeeResponse uncounted) {
    int size = stretches.size();
    ZonedDateTime[] countedDates = new ZonedDateTime[size];
    EventAttendeeResponse[] decisions = new EventAttendeeResponse[size];
    for (int i = 0; i < size; i++) {
      countedDates[i] = firstCountedDate.apply(stretches.get(i));
      if (countedDates[i] != null) {
        decisions[i] = decisionAt.apply(countedDates[i]);
      } else if (heldOver.test(stretches.get(i))) {
        decisions[i] = EventAttendeeResponse.ACCEPTED;
      }
    }
    EventAttendeeResponse previous = null;
    for (int i = 0; i < size; i++) {
      if (countedDates[i] != null) {
        previous = decisions[i];
      } else if (decisions[i] == null) {
        decisions[i] = previous;
      }
    }
    EventAttendeeResponse next = uncounted;
    for (int i = size - 1; i >= 0; i--) {
      if (countedDates[i] != null) {
        next = decisions[i];
      } else if (decisions[i] == null) {
        decisions[i] = next;
      }
    }
    List<SeatPiece> pieces = new ArrayList<>();
    int start = 0;
    for (int i = 1; i <= size; i++) {
      if (i == size || decisions[i] != decisions[start]) {
        pieces.add(new SeatPiece(stretches.get(start).from(),
                                 stretches.get(i - 1).until(),
                                 decisions[start],
                                 firstNonNull(countedDates, start, i)));
        start = i;
      }
    }
    return pieces;
  }

  /**
   * The answer date a piece is stored with. Where the stored answer changes,
   * it is now. It is kept only when the attendee's rows before the answer
   * held the piece's answer over the whole of its range, from the first date
   * of the series, with no gap: then it is the earliest of their dates, a
   * null one first, so that a place on the waiting list keeps its rank.
   *
   * @param piece the piece written
   * @param priorRows the attendee's rows on the pool before the answer
   * @param seriesFirstDate the occurrence id of the first date of the
   *          series, read only for a piece with no lower bound whose first
   *          row has one
   * @param now the instant of the answer
   * @return the answer date of the piece: now, or an earlier date kept,
   *         possibly null
   */
  static ZonedDateTime pieceResponseDate(SeatPiece piece,
                                         Collection<EventAttendee> priorRows,
                                         Supplier<ZonedDateTime> seriesFirstDate,
                                         ZonedDateTime now) {
    ZonedDateTime until = piece.until();
    List<EventAttendee> overlapping = priorRows.stream()
                                               .filter(row -> overlaps(row, piece.from(), until))
                                               .sorted(Comparator.comparing(EventAttendee::getFromOccurrenceId,
                                                                            Comparator.nullsFirst(Comparator.naturalOrder())))
                                               .toList();
    if (overlapping.isEmpty() || overlapping.stream().anyMatch(row -> row.getResponse() != piece.response())) {
      return now;
    }
    // The range is covered from its lower bound, or from the first date of
    // the series: no date comes before it
    ZonedDateTime covered = piece.from();
    for (EventAttendee row : overlapping) {
      ZonedDateTime rowFrom = row.getFromOccurrenceId();
      if (rowFrom != null && covered == null) {
        covered = seriesFirstDate.get();
      }
      if (rowFrom != null && rowFrom.isAfter(covered)) {
        return now;
      }
      ZonedDateTime rowUntil = row.getUntilOccurrenceId();
      if (rowUntil == null || (until != null && !rowUntil.isBefore(until))) {
        return overlapping.stream()
                          .min(Comparator.comparing(EventAttendee::getResponseDate,
                                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                          .orElseThrow()
                          .getResponseDate();
      }
      if (covered == null || rowUntil.isAfter(covered)) {
        covered = rowUntil;
      }
    }
    return now;
  }

  private static boolean overlaps(EventAttendee row, ZonedDateTime from, ZonedDateTime until) {
    ZonedDateTime rowFrom = row.getFromOccurrenceId();
    ZonedDateTime rowUntil = row.getUntilOccurrenceId();
    return (rowUntil == null || from == null || rowUntil.isAfter(from))
        && (until == null || rowFrom == null || rowFrom.isBefore(until));
  }

  private static void addBound(Set<ZonedDateTime> bounds, ZonedDateTime bound, ZonedDateTime from, ZonedDateTime until) {
    if (bound != null && (from == null || bound.isAfter(from)) && (until == null || bound.isBefore(until))) {
      bounds.add(bound);
    }
  }

  private static ZonedDateTime firstNonNull(ZonedDateTime[] dates, int from, int to) {
    for (int i = from; i < to; i++) {
      if (dates[i] != null) {
        return dates[i];
      }
    }
    return null;
  }
}
