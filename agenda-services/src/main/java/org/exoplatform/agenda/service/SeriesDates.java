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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.util.Utils;

/**
 * The dates of a series that a seat is decided at, and the date its answer is
 * read at, from its rule, the rows of its dates and the current instant.
 * <p>
 * A counted date is a date the rule produces that has no row of its own and
 * is not over: a date with its own row is a pool of its own, cancelled or
 * not, and a date over decides nothing. Its id is the occurrence id the rule
 * gives it, normalised as {@link Utils#getOccurrenceId(boolean, ZonedDateTime,
 * ZoneId)} normalises the ids an answer is stored with. A row is the row of a
 * date when it designates the same date of the series
 * ({@link Utils#isSameSeriesDate}).
 * <p>
 * The rule is read through {@link Utils#getOccurrences(Event, LocalDate,
 * LocalDate, int)} in windows of the series' zone, each with an explicit end:
 * without one, the window closes with the UTC date of the series' end, which
 * leaves out its last date wherever that date ends, in UTC, on the day before
 * its own date (east of UTC, a date ending before the zone's offset).
 */
final class SeriesDates {

  /**
   * Days of the rule read at once: a quarter, or one day on a series with
   * several dates a day.
   */
  private static final int    WINDOW_DAYS                = 92;

  private static final int    SEVERAL_DATES_A_DAY_WINDOW = 1;

  /**
   * How far a series with no end is read past the last date it holds a row
   * for, or past now, before it is taken as holding no further date.
   */
  private static final int    HORIZON_YEARS              = 5;

  private static final long   DAY_SECONDS                = Duration.ofDays(1).getSeconds();

  private static final Duration RULE_READ_MARGIN         = Duration.ofHours(27);

  private final Event         series;

  private final ZoneId        zone;

  private final boolean       severalDatesADay;

  private final Supplier<List<Event>> dateRowsReader;

  private final ZonedDateTime now;

  private List<Event>         dateRows;

  private Map<Object, Event>  dateRowsByKey;

  /**
   * @param series the series, as stored
   * @param dateRowsReader reads the rows of its dates, cancelled ones
   *          included: once, and only when a date is looked for
   * @param now the current instant
   */
  SeriesDates(Event series, Supplier<List<Event>> dateRowsReader, ZonedDateTime now) {
    this.series = series;
    this.zone = series.getTimeZoneId() == null ? ZoneOffset.UTC : series.getTimeZoneId();
    this.severalDatesADay = Utils.hasSeveralDatesADay(series.getRecurrence());
    this.dateRowsReader = dateRowsReader;
    this.now = now;
  }

  /**
   * @param from the occurrence id the range starts at, inclusive, or null from
   *          the first date of the series
   * @param until the occurrence id the range ends before, exclusive, or null
   *          when it has no end
   * @return the id of the first counted date of the range, or null when it
   *         has none
   */
  ZonedDateTime firstCountedDate(ZonedDateTime from, ZonedDateTime until) {
    Event date = firstDate(from, until, true, this::isCounted);
    return date == null ? null : idOf(date);
  }

  /**
   * @return the id of the first date the rule of the series produces, over
   *         or not
   */
  ZonedDateTime firstDate() {
    Event date = firstDate(null, null, false, occurrence -> true);
    return date == null ? Utils.getOccurrenceId(series.isAllDay(), series.getStart(), zone) : idOf(date);
  }

  /**
   * The date a user's answer to the series as a whole is read at: the next
   * date the series carries, the first by start that is neither cancelled nor
   * over; when none remains, the last date it carries; when every date is
   * cancelled, the last date its rule produces.
   * <p>
   * A date whose row was not moved is where the rule puts it: the dates
   * stored on such a row are not relied on, a timed one being anchored on the
   * UTC day of its id, a day off wherever that day is not the date's day in
   * the series' zone. A moved row, its dates modified, is at its own dates.
   *
   * @return the id of that date, or null when the series has none
   */
  ZonedDateTime readDate() {
    Event next = firstDate(null, null, true, occurrence -> endOf(occurrence).isAfter(now) && isCarriedInPlace(occurrence));
    for (Event row : carriedMovedRows()) {
      if (endOf(row).isAfter(now) && (next == null || startOf(row).isBefore(startOf(next)))) {
        next = row;
      }
    }
    if (next != null) {
      return idOf(next);
    }
    Event last = lastDate(this::isCarriedInPlace);
    for (Event row : carriedMovedRows()) {
      if (last == null || startOf(row).isAfter(startOf(last))) {
        last = row;
      }
    }
    if (last == null) {
      last = lastDate(occurrence -> true);
    }
    return last == null ? null : idOf(last);
  }

  /**
   * @param row the row of one of its dates
   * @return whether that date is over: a moved row at its own dates, any other
   *         where the rule puts its date, the stored dates of such a row not
   *         being relied on: at the end the rule gives its day, its id keeping
   *         the time of day the series had when the row was made, or, for a
   *         day the rule no longer gives, at the series' time of day plus its
   *         length; on a series with several dates a day, at its id plus that
   *         length
   */
  boolean isOver(Event row) {
    return !endOfRow(row).isAfter(now);
  }

  private boolean isCounted(Event occurrence) {
    return endOf(occurrence).isAfter(now) && !hasOwnRow(occurrence);
  }

  private ZonedDateTime endOfRow(Event row) {
    if (row.getOccurrence().isDatesModified()) {
      return endOf(row);
    }
    ZonedDateTime occurrenceId = row.getOccurrence().getId();
    Duration length = Duration.between(series.getStart(), series.getEnd());
    if (!series.isAllDay() && severalDatesADay) {
      return occurrenceId.plus(length);
    }
    LocalDate day = Utils.getSeriesDate(series, occurrenceId);
    ZonedDateTime estimate = endAtSeriesTime(day, length);
    // The rule's date of that day starts on that day, at a time of day that
    // differs from the series' on a day the offset changes, so its end lies
    // within a day and two hours of the estimate: further from now than that,
    // the estimate decides alone, and the rule, which a reading walks from its
    // first date, is read only near now
    if (Duration.between(estimate, now).abs().compareTo(RULE_READ_MARGIN) > 0) {
      return estimate;
    }
    Object key = dateKey(occurrenceId);
    for (Event date : Utils.getOccurrences(series, day.minusDays(1), day.plusDays(1), 0)) {
      if (key.equals(dateKey(date.getOccurrence().getId()))) {
        return endOf(date);
      }
    }
    // A day the rule no longer gives
    return estimate;
  }

  /**
   * @return the end of a date on a day at the series' time of day, the last
   *         second of its last day for an all-day one
   */
  private ZonedDateTime endAtSeriesTime(LocalDate day, Duration length) {
    if (!series.isAllDay()) {
      LocalTime time = series.getStart().withZoneSameInstant(zone).toLocalTime();
      return day.atTime(time).atZone(zone).plus(length);
    }
    long days = Math.max(1, Math.round(length.getSeconds() / (double) DAY_SECONDS));
    return day.plusDays(days).atStartOfDay(zone).minusSeconds(1);
  }

  /**
   * @return whether the series carries a date of its rule where the rule puts
   *         it: the date has no row, or a row neither cancelled nor moved
   */
  private boolean isCarriedInPlace(Event occurrence) {
    Event row = rowOf(occurrence);
    return row == null || (row.getStatus() != EventStatus.CANCELLED && !row.getOccurrence().isDatesModified());
  }

  /**
   * @return the rows of dates that were moved and are not cancelled, each
   *         the row of its date
   */
  private List<Event> carriedMovedRows() {
    return dateRows().stream()
                     .filter(row -> row.getStatus() != EventStatus.CANCELLED && row.getOccurrence().isDatesModified()
                         && rowOf(row) == row)
                     .toList();
  }

  private boolean hasOwnRow(Event occurrence) {
    return rowOf(occurrence) != null;
  }

  /**
   * @return the row of a date, the first created among several, as the
   *         storage finds a date's row, or null when it has none
   */
  private Event rowOf(Event date) {
    if (dateRowsByKey == null) {
      dateRowsByKey = new HashMap<>();
      dateRows().forEach(row -> dateRowsByKey.merge(dateKey(row.getOccurrence().getId()),
                                                    row,
                                                    (first, other) -> other.getId() < first.getId() ? other : first));
    }
    return dateRowsByKey.get(dateKey(date.getOccurrence().getId()));
  }

  private List<Event> dateRows() {
    if (dateRows == null) {
      dateRows = dateRowsReader.get()
                               .stream()
                               .filter(row -> row.getOccurrence() != null && row.getOccurrence().getId() != null)
                               .toList();
    }
    return dateRows;
  }

  /**
   * @return what two ids of the same date of the series share: the instant on
   *         a series with several dates a day, the date otherwise
   */
  private Object dateKey(ZonedDateTime occurrenceId) {
    return severalDatesADay ? occurrenceId.toEpochSecond() : Utils.getSeriesDate(series, occurrenceId);
  }

  /**
   * Reads the rule forward, window by window, from the start of the range,
   * or from the earliest start of a date not over yet when only those are
   * looked for, whichever is later.
   */
  private Event firstDate(ZonedDateTime from, ZonedDateTime until, boolean notOver, Predicate<Event> accepted) {
    LocalDate day = scanStart(from, notOver);
    LocalDate last = scanEnd(until);
    while (!day.isAfter(last)) {
      LocalDate windowEnd = day.plusDays(windowDays() - 1L);
      if (windowEnd.isAfter(last)) {
        windowEnd = last;
      }
      for (Event occurrence : Utils.getOccurrences(series, day, windowEnd, 0)) {
        ZonedDateTime date = idOf(occurrence);
        if (from != null && date.isBefore(from)) {
          continue;
        }
        if (until != null && !date.isBefore(until)) {
          return null;
        }
        if (accepted.test(occurrence)) {
          return occurrence;
        }
      }
      day = windowEnd.plusDays(1);
    }
    return null;
  }

  /**
   * Reads the rule backward, window by window, from the local date of the
   * series' end down to its start.
   */
  private Event lastDate(Predicate<Event> accepted) {
    LocalDate first = startDate();
    LocalDate day = endDate();
    while (!day.isBefore(first)) {
      LocalDate windowStart = day.minusDays(windowDays() - 1L);
      if (windowStart.isBefore(first)) {
        windowStart = first;
      }
      List<Event> occurrences = Utils.getOccurrences(series, windowStart, day, 0);
      for (int i = occurrences.size() - 1; i >= 0; i--) {
        if (accepted.test(occurrences.get(i))) {
          return occurrences.get(i);
        }
      }
      day = windowStart.minusDays(1);
    }
    return null;
  }

  private LocalDate scanStart(ZonedDateTime from, boolean notOver) {
    LocalDate start = startDate();
    if (notOver) {
      // A date not over yet starts after now less the length of a date
      long dateDays = Duration.between(series.getStart(), series.getEnd()).toDays() + 1;
      LocalDate notOverStart = now.withZoneSameInstant(zone).toLocalDate().minusDays(dateDays);
      if (notOverStart.isAfter(start)) {
        start = notOverStart;
      }
    }
    if (from != null) {
      LocalDate fromDate = Utils.getSeriesDate(series, from).minusDays(1);
      if (fromDate.isAfter(start)) {
        start = fromDate;
      }
    }
    return start;
  }

  private LocalDate scanEnd(ZonedDateTime until) {
    LocalDate end = endDate();
    if (until != null) {
      LocalDate untilDate = Utils.getSeriesDate(series, until).plusDays(1);
      if (untilDate.isBefore(end)) {
        end = untilDate;
      }
    }
    return end;
  }

  /**
   * @return the local date the series ends on, a day past it so that its last
   *         date is read in full; for a series with no end, the horizon past
   *         the later of now, its start and the last date it holds a row for
   */
  private LocalDate endDate() {
    EventRecurrence recurrence = series.getRecurrence();
    if (recurrence != null && recurrence.getOverallEnd() != null) {
      return recurrence.getOverallEnd().withZoneSameInstant(zone).toLocalDate().plusDays(1);
    }
    if (recurrence != null && recurrence.getUntil() != null) {
      return recurrence.getUntil().plusDays(1);
    }
    LocalDate latest = now.withZoneSameInstant(zone).toLocalDate();
    if (startDate().isAfter(latest)) {
      latest = startDate();
    }
    for (Event row : dateRows()) {
      LocalDate rowDate = Utils.getSeriesDate(series, row.getOccurrence().getId());
      if (rowDate.isAfter(latest)) {
        latest = rowDate;
      }
    }
    return latest.plusYears(HORIZON_YEARS);
  }

  private int windowDays() {
    return severalDatesADay ? SEVERAL_DATES_A_DAY_WINDOW : WINDOW_DAYS;
  }

  /**
   * @return the local date the series starts on, read as the rule reads it
   */
  private LocalDate startDate() {
    return series.isAllDay() ? series.getStart().toLocalDate() : series.getStart().withZoneSameInstant(zone).toLocalDate();
  }

  private ZonedDateTime idOf(Event date) {
    return Utils.getOccurrenceId(series.isAllDay(), date.getOccurrence().getId(), zone);
  }

  /**
   * @return the start of a date, the start of its first day for an all-day
   *         one, as the rule expands it
   */
  private ZonedDateTime startOf(Event date) {
    return series.isAllDay() ? date.getStart().toLocalDate().atStartOfDay(zone) : date.getStart();
  }

  /**
   * @return the end of a date, the last second of its last day for an all-day
   *         one. The rule ends an all-day date the length of the series after
   *         its start, an hour into the next day or an hour before the end of
   *         the day when that length spans a change of offset: the length of a
   *         date is counted in whole days.
   */
  private ZonedDateTime endOf(Event date) {
    if (!series.isAllDay()) {
      return date.getEnd();
    }
    long days = Math.max(1, Math.round(Duration.between(date.getStart(), date.getEnd()).getSeconds() / (double) DAY_SECONDS));
    return startOf(date).toLocalDate().plusDays(days).atStartOfDay(zone).minusSeconds(1);
  }
}
