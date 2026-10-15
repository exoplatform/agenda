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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.util.Utils;

/**
 * The dates of a series a seat is decided at, and the date its answer is
 * read at, on series built in memory and a fixed current instant.
 */
class SeriesDatesTest {

  private static final ZoneId        PARIS    = ZoneId.of("Europe/Paris");

  private static final ZoneId        BRISBANE = ZoneId.of("Australia/Brisbane");

  private static final Duration      HOUR     = Duration.ofHours(1);

  /** Daily at 09:00 in Paris, ten dates from Monday 7 January 2030. */
  private static final ZonedDateTime START    = ZonedDateTime.of(2030, 1, 7, 9, 0, 0, 0, PARIS);

  private static final ZonedDateTime BEFORE   = ZonedDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  /**
   * Zones swept: UTC, east and west of it, with and without changes of
   * offset, two of them skipping or repeating midnight.
   */
  private static final List<ZoneId>    SWEPT_ZONES = List.of(ZoneOffset.UTC,
                                                             ZoneId.of("Europe/Paris"),
                                                             ZoneId.of("America/New_York"),
                                                             ZoneId.of("America/Santiago"),
                                                             ZoneId.of("America/Havana"),
                                                             ZoneId.of("Australia/Sydney"),
                                                             ZoneId.of("Australia/Brisbane"),
                                                             ZoneId.of("Pacific/Kiritimati"));

  /** Local start times swept, null standing for an all-day date. */
  private static final List<LocalTime> SWEPT_TIMES = Arrays.asList(LocalTime.MIDNIGHT,
                                                                   LocalTime.of(0, 30),
                                                                   LocalTime.of(2, 30),
                                                                   LocalTime.of(9, 0),
                                                                   LocalTime.of(23, 30),
                                                                   null);

  /** Rules swept, a count of 0 standing for a series with no end. */
  private static final List<SweptRule> SWEPT_RULES = List.of(new SweptRule(EventRecurrenceFrequency.DAILY, 6),
                                                             new SweptRule(EventRecurrenceFrequency.WEEKLY, 4),
                                                             new SweptRule(EventRecurrenceFrequency.MONTHLY, 3),
                                                             new SweptRule(EventRecurrenceFrequency.YEARLY, 3),
                                                             new SweptRule(EventRecurrenceFrequency.HOURLY, 30),
                                                             new SweptRule(EventRecurrenceFrequency.DAILY, 0));

  /**
   * The first counted date is the first date the rule produces, its id the
   * UTC instant of a timed date; a date with its own row is not counted,
   * cancelled or not.
   */
  @Test
  void aDateWithItsOwnRowIsNotCounted() {
    Event series = dailySeries(START, 10);

    assertEquals(date(0), dates(series, List.of(), BEFORE).firstCountedDate(null, null));
    assertEquals(date(2),
                 dates(series,
                       List.of(dateRow(series, 0, EventStatus.CONFIRMED), dateRow(series, 1, EventStatus.CANCELLED)),
                       BEFORE).firstCountedDate(null, null));
  }

  /**
   * A date over is not counted, a date under way is.
   */
  @Test
  void aDateOverIsNotCountedADateUnderWayIs() {
    Event series = dailySeries(START, 10);

    assertEquals(date(2), dates(series, List.of(), START.plusDays(2).plusMinutes(30)).firstCountedDate(null, null));
    assertEquals(date(3), dates(series, List.of(), START.plusDays(2).plus(HOUR)).firstCountedDate(null, null));
  }

  /**
   * A range counts the dates from its start included to its end excluded.
   */
  @Test
  void aRangeEndsBeforeItsUntil() {
    Event series = dailySeries(START, 10);
    SeriesDates dates = dates(series, List.of(dateRow(series, 1, EventStatus.CONFIRMED)), BEFORE);

    assertNull(dates.firstCountedDate(date(1), date(2)));
    assertEquals(date(2), dates.firstCountedDate(date(1), date(3)));
    assertEquals(date(4), dates.firstCountedDate(date(4), null));
    assertNull(dates.firstCountedDate(date(10), null));
  }

  /**
   * A series whose next date comes past the first window of the rule is read
   * window by window until it is found.
   */
  @Test
  void aDateFarPastTheFirstWindowIsFound() {
    Event series = series(START, HOUR, EventRecurrenceFrequency.YEARLY, 0, null);
    SeriesDates dates = dates(series, List.of(dateRow(series, START, EventStatus.CONFIRMED)), BEFORE);

    assertEquals(START.plusYears(1).withZoneSameInstant(ZoneOffset.UTC), dates.firstCountedDate(null, null));
  }

  /**
   * On a series with several dates a day, each date is its instant: the row
   * of one hour leaves the next one counted.
   */
  @Test
  void eachDateOfAnHourlySeriesIsCountedOnItsOwn() {
    Event series = series(START, Duration.ofMinutes(30), EventRecurrenceFrequency.HOURLY, 5, START.plusHours(4).plusMinutes(30));
    SeriesDates dates = dates(series, List.of(dateRow(series, START.plusHours(1), EventStatus.CONFIRMED)), BEFORE);

    assertEquals(START.plusHours(2).withZoneSameInstant(ZoneOffset.UTC), dates.firstCountedDate(START.plusHours(1), null));
  }

  /**
   * The row of a day the rule no longer gives is over at the series' time of
   * day on that day, plus its length, not at its own id or dates, which are
   * at another time.
   */
  @Test
  void aRowOfADayTheRuleNoLongerGivesIsOverAtTheSeriesTimeOfDay() {
    Event series = series(START, HOUR, EventRecurrenceFrequency.WEEKLY, 10, null);
    Event row = dateRow(series, START.plusDays(1).withHour(1), EventStatus.CONFIRMED);

    assertFalse(dates(series, List.of(row), START.plusDays(1).plus(HOUR).minusMinutes(10)).isOver(row));
    assertTrue(dates(series, List.of(row), START.plusDays(1).plus(HOUR)).isOver(row));
  }

  /**
   * The first date of the series is read over or not.
   */
  @Test
  void theFirstDateIsReadOverOrNot() {
    Event series = dailySeries(START, 10);

    assertEquals(date(0), dates(series, List.of(), START.plusDays(30)).firstDate());
  }

  /**
   * The answer of a series is read at its next date, by start, neither
   * cancelled nor over, a date with its own row read on that row.
   */
  @Test
  void theAnswerIsReadAtTheNextDateNotCancelled() {
    Event series = dailySeries(START, 10);

    assertEquals(date(0), dates(series, List.of(), BEFORE).readDate());
    assertEquals(date(0), dates(series, List.of(dateRow(series, 0, EventStatus.CONFIRMED)), BEFORE).readDate());
    assertEquals(date(1), dates(series, List.of(dateRow(series, 0, EventStatus.CANCELLED)), BEFORE).readDate());
  }

  /**
   * A date moved before the one preceding it is the next date: the dates are
   * ordered by their start, the date read keeping its own id.
   */
  @Test
  void aDateMovedEarlierIsTheNextDate() {
    Event series = dailySeries(START, 10);
    Event moved = dateRow(series, 3, EventStatus.CONFIRMED);
    moved.setStart(START.plusDays(2).minusHours(3));
    moved.setEnd(moved.getStart().plus(HOUR));
    moved.getOccurrence().setDatesModified(true);

    assertEquals(date(3), dates(series, List.of(moved), START.plusDays(1).plus(HOUR)).readDate());
  }

  /**
   * Of several rows of a date, the first created is the date's row, as the
   * storage finds it, whatever the order they are read in: moved, it takes
   * the date from its place though a later row stays there; not moved, it
   * keeps the date in its place though a later row was moved.
   */
  @Test
  void theFirstCreatedOfSeveralRowsOfADateIsItsRow() {
    Event series = dailySeries(START, 10);
    ZonedDateTime now = START.plusDays(1).plus(HOUR);
    Event movedFirst = dateRow(series, 2, EventStatus.CONFIRMED);
    movedFirst.setId(10);
    movedFirst.setStart(START.plusDays(5).plusHours(3));
    movedFirst.setEnd(movedFirst.getStart().plus(HOUR));
    movedFirst.getOccurrence().setDatesModified(true);
    Event laterInPlace = dateRow(series, 2, EventStatus.CONFIRMED);
    laterInPlace.setId(20);
    Event inPlaceFirst = dateRow(series, 4, EventStatus.CONFIRMED);
    inPlaceFirst.setId(5);
    Event laterMoved = dateRow(series, 4, EventStatus.CONFIRMED);
    laterMoved.setId(30);
    laterMoved.setStart(START.plusDays(1).plusHours(3));
    laterMoved.setEnd(laterMoved.getStart().plus(HOUR));
    laterMoved.getOccurrence().setDatesModified(true);

    assertEquals(date(3), dates(series, List.of(laterInPlace, movedFirst), now).readDate());
    assertEquals(date(2), dates(series, List.of(laterMoved, inPlaceFirst), now).readDate());
  }

  /**
   * A date moved past the one following it leaves the place the rule gives
   * it: the date following it is the next date.
   */
  @Test
  void aDateMovedLaterLeavesItsPlace() {
    Event series = dailySeries(START, 10);
    Event moved = dateRow(series, 1, EventStatus.CONFIRMED);
    moved.setStart(START.plusDays(2).plusHours(3));
    moved.setEnd(moved.getStart().plus(HOUR));
    moved.getOccurrence().setDatesModified(true);

    assertEquals(date(2), dates(series, List.of(moved), START.plus(HOUR)).readDate());
  }

  /**
   * A row whose dates were not moved is read where the rule puts its date,
   * whatever the dates stored on it: east of UTC, a morning date's row
   * anchored on the UTC day of its id starts and ends a day early, and is
   * still the next date once that day's hour is past.
   */
  @Test
  void aRowNotMovedIsReadWhereTheRulePutsItsDate() {
    ZonedDateTime start = ZonedDateTime.of(2030, 1, 7, 9, 0, 0, 0, ZoneId.of("Australia/Sydney"));
    Event series = series(start, HOUR, EventRecurrenceFrequency.DAILY, 10, start.plusDays(9).plus(HOUR));
    Event anchoredADayEarly = dateRow(series, start.plusDays(1), EventStatus.CONFIRMED);
    anchoredADayEarly.setStart(start);
    anchoredADayEarly.setEnd(start.plus(HOUR));

    assertEquals(start.plusDays(1).withZoneSameInstant(ZoneOffset.UTC),
                 dates(series, List.of(anchoredADayEarly), start.plusMinutes(90)).readDate());
  }

  /**
   * Once no date remains, the answer is read at the last date the series
   * carries, a cancelled last date left out; when every date is cancelled,
   * at the last date its rule produces.
   */
  @Test
  void anEndedSeriesIsReadAtItsLastDateCarried() {
    Event series = dailySeries(START, 10);
    ZonedDateTime after = START.plusDays(20);

    assertEquals(date(9), dates(series, List.of(), after).readDate());
    assertEquals(date(8), dates(series, List.of(dateRow(series, 9, EventStatus.CANCELLED)), after).readDate());
    List<Event> everyDateCancelled = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      everyDateCancelled.add(dateRow(series, i, EventStatus.CANCELLED));
    }
    assertEquals(date(9), dates(series, everyDateCancelled, after).readDate());
    assertEquals(date(9), dates(series, everyDateCancelled, BEFORE).readDate());
  }

  /**
   * A daily series with a count in UTC+10, 08:00 to 09:00 local time, every
   * date cancelled: its last date ends, in UTC, on the day before its own date,
   * so a window closed with the UTC date of the series' end would leave it out.
   * It is the date read.
   */
  @Test
  void theLastDateOfASeriesEndingOnTheUtcDayBeforeItIsRead() {
    ZonedDateTime start = ZonedDateTime.of(2030, 1, 7, 8, 0, 0, 0, BRISBANE);
    Event series = series(start, HOUR, EventRecurrenceFrequency.DAILY, 3, start.plusDays(2).plus(HOUR));
    List<Event> cancelled = List.of(dateRow(series, start, EventStatus.CANCELLED),
                                    dateRow(series, start.plusDays(1), EventStatus.CANCELLED),
                                    dateRow(series, start.plusDays(2), EventStatus.CANCELLED));

    assertEquals(ZonedDateTime.of(2030, 1, 8, 22, 0, 0, 0, ZoneOffset.UTC), dates(series, cancelled, BEFORE).readDate());
  }

  /**
   * Every date of a series with several dates a day is counted once, a date
   * that starts before midnight in the series' zone and runs into the next day
   * included: it is not counted again with the dates of that day.
   */
  @Test
  void aDateRunningIntoTheNextDayIsCountedOnce() {
    ZonedDateTime start = ZonedDateTime.of(2030, 1, 7, 10, 0, 0, 0, PARIS);
    Event series = series(start, HOUR, EventRecurrenceFrequency.HOURLY, 48, start.plusHours(47).plus(HOUR));

    assertEquals(48, dates(series, List.of(), BEFORE).countCountedDates(null, null));
    assertEquals(24, dates(series, List.of(), BEFORE).countCountedDates(start.plusHours(12).withZoneSameInstant(ZoneOffset.UTC),
                                                                         start.plusHours(36).withZoneSameInstant(ZoneOffset.UTC)));
  }

  /**
   * The rows of the dates are read once, and only when a date is looked for.
   */
  @Test
  void theRowsOfTheDatesAreReadOnceWhenNeeded() {
    Event series = dailySeries(START, 10);
    int[] reads = new int[1];
    Supplier<List<Event>> reader = () -> {
      reads[0]++;
      return List.of(dateRow(series, 0, EventStatus.CONFIRMED));
    };
    SeriesDates dates = new SeriesDates(series, reader, BEFORE);

    assertEquals(0, reads[0]);
    assertEquals(date(1), dates.firstCountedDate(null, null));
    assertEquals(date(1), dates.firstCountedDate(date(1), null));
    assertEquals(1, reads[0]);
  }

  /**
   * An ended yearly series whose last date is cancelled is read at the date
   * before, read back past several windows of the rule.
   */
  @Test
  void anEndedSeriesIsReadBackPastSeveralWindows() {
    Event series = series(START, HOUR, EventRecurrenceFrequency.YEARLY, 3, START.plusYears(2).plus(HOUR));

    assertEquals(START.plusYears(1).withZoneSameInstant(ZoneOffset.UTC),
                 dates(series, List.of(dateRow(series, START.plusYears(2), EventStatus.CANCELLED)), START.plusYears(3)).readDate());
  }

  /**
   * Swept over zones on both sides of UTC, the changes of offset of 2030,
   * start times around them, all-day and timed dates, rules with one date a
   * day or several, with or without an end, rows of dates moved or not, some
   * anchored as the materialisation of a date anchors them, their ids in each
   * form a row may be stored with, and current instants before the series,
   * in the last minutes of a date, at its end and past the series: the first
   * counted date of a range, the number of its counted dates, the first date
   * and the date read are those a reading of every date of the rule at once
   * gives, an all-day date ending with the last of its days.
   */
  @Test
  void theDatesFoundAreThoseOfTheWholeRuleReadAtOnce() {
    List<String> mismatches = new ArrayList<>();
    int checks = 0;
    for (ZoneId zone : SWEPT_ZONES) {
      for (LocalDate firstDay : sweptFirstDays(zone)) {
        for (LocalTime time : SWEPT_TIMES) {
          for (SweptRule rule : SWEPT_RULES) {
            if (time == null && rule.frequency() == EventRecurrenceFrequency.HOURLY) {
              continue;
            }
            Event series = sweptSeries(zone, firstDay, time, rule);
            List<Event> wholeRule = Utils.getOccurrences(series,
                                                         firstDay.minusDays(1),
                                                         firstDay.plus(rule.count() > 0 ? Period.ofYears(4) : Period.ofDays(120)),
                                                         0);
            int last = rule.count() > 0 ? wholeRule.size() - 1 : 5;
            int mid = last / 2;
            List<ZonedDateTime> ids = wholeRule.stream().map(date -> normalisedId(series, date)).toList();
            List<ZonedDateTime> starts = wholeRule.stream().map(date -> dateStart(series, date)).toList();
            List<ZonedDateTime> ends = wholeRule.stream().map(date -> dateEnd(series, date)).toList();
            List<ZonedDateTime[]> ranges = Arrays.asList(new ZonedDateTime[] { null, null },
                                                         new ZonedDateTime[] { ids.get(1), null },
                                                         new ZonedDateTime[] { null, ids.get(mid) },
                                                         new ZonedDateTime[] { ids.get(mid), ids.get(mid + 1) });
            List<ZonedDateTime> nows = List.of(wholeRule.get(0).getStart().minusDays(3),
                                               ends.get(mid).minusMinutes(10),
                                               ends.get(mid),
                                               ends.get(last).plusDays(1));
            for (Map<Integer, SweptRow> rows : sweptRows(rule, wholeRule.size(), mid, last)) {
              for (int n = 0; n < nows.size(); n++) {
                ZonedDateTime now = nows.get(n);
                // Before the series no date is over, and each row's id takes
                // every form; past that instant, the form turns with it
                for (int form : n == 0 ? List.of(0, 1, 2) : List.of(n % 3)) {
                  List<Event> dateRows = rows.entrySet()
                                             .stream()
                                             .map(row -> sweptRow(series, wholeRule.get(row.getKey()), row.getKey(), form, row.getValue()))
                                             .toList();
                  SeriesDates dates = dates(series, dateRows, now);
                  String label = zone + " " + firstDay + " " + (time == null ? "all-day" : time) + " " + rule + ", rows "
                      + rows + " from id form " + form + ", now " + now.withZoneSameInstant(ZoneOffset.UTC);
                  checks += 2;
                  check(mismatches, label + ": first date", ids.get(0), dates.firstDate());
                  check(mismatches, label + ": date read", expectedReadDate(series, starts, ends, ids, rows, now), dates.readDate());
                  for (ZonedDateTime[] range : ranges) {
                    checks += 2;
                    check(mismatches,
                          label + ": first counted date of [" + range[0] + ", " + range[1] + ")",
                          expectedFirstCountedDate(ends, ids, rows, now, range[0], range[1]),
                          dates.firstCountedDate(range[0], range[1]));
                    check(mismatches,
                          label + ": counted dates of [" + range[0] + ", " + range[1] + ")",
                          range[1] == null && rule.count() <= 0 ? -1
                                                                : expectedCountedDates(ends, ids, rows, now, range[0], range[1]),
                          dates.countCountedDates(range[0], range[1]));
                  }
                  List<Map.Entry<Integer, SweptRow>> sweptRows = List.copyOf(rows.entrySet());
                  for (int r = 0; r < sweptRows.size(); r++) {
                    int index = sweptRows.get(r).getKey();
                    int movedDays = sweptRows.get(r).getValue().movedDays();
                    ZonedDateTime end = movedDays == 0 ? ends.get(index)
                                                       : movedDates(series, starts.get(index), ends.get(index), movedDays)[1];
                    boolean over = !end.isAfter(now);
                    checks++;
                    if (dates.isOver(dateRows.get(r)) != over) {
                      mismatches.add(label + ": row of date " + index + " " + (over ? "over" : "not over") + " expected, its end "
                          + end);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    assertEquals(List.of(),
                 mismatches.stream().limit(10).toList(),
                 mismatches.size() + " of " + checks + " checks differ");
  }

  private static SeriesDates dates(Event series, List<Event> dateRows, ZonedDateTime now) {
    return new SeriesDates(series, () -> dateRows, now);
  }

  private static ZonedDateTime date(int index) {
    return START.plusDays(index).withZoneSameInstant(ZoneOffset.UTC);
  }

  private static Event dailySeries(ZonedDateTime start, int count) {
    return series(start, HOUR, EventRecurrenceFrequency.DAILY, count, start.plusDays(count - 1L).plus(HOUR));
  }

  private static Event series(ZonedDateTime start,
                              Duration length,
                              EventRecurrenceFrequency frequency,
                              int count,
                              ZonedDateTime lastEnd) {
    Event series = new Event();
    series.setId(1);
    series.setSummary("series");
    series.setTimeZoneId(start.getZone());
    series.setStart(start);
    series.setEnd(start.plus(length));
    series.setStatus(EventStatus.CONFIRMED);
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(frequency);
    recurrence.setInterval(1);
    recurrence.setCount(count);
    recurrence.setOverallEnd(lastEnd == null ? null : lastEnd.withZoneSameInstant(ZoneOffset.UTC));
    series.setRecurrence(recurrence);
    return series;
  }

  private static Event dateRow(Event series, int index, EventStatus status) {
    return dateRow(series, series.getStart().plusDays(index), status);
  }

  private static Event dateRow(Event series, ZonedDateTime start, EventStatus status) {
    Event row = new Event();
    row.setId(100 + start.toEpochSecond());
    row.setParentId(series.getId());
    row.setSummary(series.getSummary());
    row.setTimeZoneId(series.getTimeZoneId());
    row.setStart(start);
    row.setEnd(start.plus(Duration.between(series.getStart(), series.getEnd())));
    row.setStatus(status);
    row.setOccurrence(new EventOccurrence(start.withZoneSameInstant(ZoneOffset.UTC), true, false));
    return row;
  }

  /**
   * The local dates a swept series starts on: the day before each of the
   * first two changes of offset of 2030 in the zone, or a day of January in
   * a zone without any.
   */
  private static List<LocalDate> sweptFirstDays(ZoneId zone) {
    List<LocalDate> days = new ArrayList<>();
    Instant instant = BEFORE.toInstant();
    for (int i = 0; i < 2; i++) {
      ZoneOffsetTransition transition = zone.getRules().nextTransition(instant);
      if (transition == null || transition.getInstant().isAfter(BEFORE.plusYears(1).toInstant())) {
        break;
      }
      days.add(transition.getDateTimeBefore().toLocalDate().minusDays(1));
      instant = transition.getInstant();
    }
    if (days.isEmpty()) {
      days.add(START.toLocalDate());
    }
    return days;
  }

  /**
   * A series as the storage gives it back: an all-day one starting at the UTC
   * midnight of its first day and ending at the last second of its last day,
   * two days for a weekly one, one otherwise; a timed one lasting an hour,
   * half an hour when hourly. With a count, its overall end is the one the
   * storage gives it, from the end the rule gives its last date; without
   * one, ten years past its start, as the storage gives a series with no end.
   */
  private static Event sweptSeries(ZoneId zone, LocalDate firstDay, LocalTime time, SweptRule rule) {
    Event series = new Event();
    series.setId(1);
    series.setSummary("series");
    series.setTimeZoneId(zone);
    series.setAllDay(time == null);
    if (time == null) {
      series.setStart(firstDay.atStartOfDay(ZoneOffset.UTC));
      series.setEnd(series.getStart().plusDays(rule.frequency() == EventRecurrenceFrequency.WEEKLY ? 2 : 1).minusSeconds(1));
    } else {
      series.setStart(ZonedDateTime.of(firstDay, time, zone));
      series.setEnd(series.getStart()
                          .plus(rule.frequency() == EventRecurrenceFrequency.HOURLY ? Duration.ofMinutes(30) : HOUR));
    }
    series.setStatus(EventStatus.CONFIRMED);
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(rule.frequency());
    recurrence.setInterval(1);
    recurrence.setCount(rule.count());
    series.setRecurrence(recurrence);
    if (rule.count() > 0) {
      List<Event> dates = Utils.getOccurrences(series, firstDay.minusDays(1), firstDay.plusYears(4), 0);
      ZonedDateTime lastEnd = dates.get(dates.size() - 1).getEnd();
      recurrence.setOverallEnd(time == null ? lastEnd.toLocalDate().plusDays(1).atStartOfDay(zone).minusSeconds(1)
                                            : lastEnd.withZoneSameInstant(zone));
    } else {
      recurrence.setOverallEnd(time == null ? firstDay.plusYears(10).plusDays(1).atStartOfDay(zone).minusSeconds(1)
                                            : series.getStart().plusYears(10).withZoneSameInstant(zone));
    }
    return series;
  }

  /**
   * The rows swept, by index of their date in the rule: none, confirmed ones
   * on the first two dates, cancelled ones on the second and last dates,
   * confirmed ones on the middle and last dates, the middle date moved a day
   * later past the next one, the last date moved a day earlier with the first
   * cancelled and, on a series with an end, every date cancelled. A timed date
   * moved by a day is moved by two hours more.
   */
  private static List<Map<Integer, SweptRow>> sweptRows(SweptRule rule, int size, int mid, int last) {
    SweptRow confirmed = new SweptRow(EventStatus.CONFIRMED, 0);
    SweptRow cancelled = new SweptRow(EventStatus.CANCELLED, 0);
    List<Map<Integer, SweptRow>> rows = new ArrayList<>(List.of(Map.of(),
                                                                Map.of(0, confirmed, 1, confirmed),
                                                                Map.of(1, cancelled, last, cancelled),
                                                                Map.of(mid, confirmed, last, confirmed),
                                                                Map.of(mid, new SweptRow(EventStatus.CONFIRMED, 1)),
                                                                Map.of(0, cancelled, last, new SweptRow(EventStatus.CONFIRMED, -1))));
    if (rule.count() > 0) {
      Map<Integer, SweptRow> everyDateCancelled = new HashMap<>();
      for (int i = 0; i < size; i++) {
        everyDateCancelled.put(i, cancelled);
      }
      rows.add(everyDateCancelled);
    }
    return rows;
  }

  /**
   * The row of a date as the storage gives it back, its id in the form its
   * index and the form given point to, so that each date meets every form
   * before the series, when none is over. Moved, at its own dates with its
   * dates modified. Not moved: an all-day one from the UTC midnight of its
   * day; a timed one at the date's own start and end on an even index, and
   * on an odd one where the materialisation of a date anchors it, at the
   * series' time of day on the UTC day of its id.
   */
  private static Event sweptRow(Event series, Event date, int index, int form, SweptRow swept) {
    Event row = new Event();
    row.setId(100 + date.getStart().toEpochSecond());
    row.setParentId(series.getId());
    row.setSummary(series.getSummary());
    row.setTimeZoneId(series.getTimeZoneId());
    row.setAllDay(series.isAllDay());
    if (swept.movedDays() != 0) {
      ZonedDateTime[] moved = movedDates(series, dateStart(series, date), dateEnd(series, date), swept.movedDays());
      if (series.isAllDay()) {
        row.setStart(moved[0].toLocalDate().atStartOfDay(ZoneOffset.UTC));
        row.setEnd(moved[1].toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1));
      } else {
        row.setStart(moved[0].withZoneSameInstant(ZoneOffset.UTC));
        row.setEnd(moved[1].withZoneSameInstant(ZoneOffset.UTC));
      }
    } else if (series.isAllDay()) {
      row.setStart(date.getStart().toLocalDate().atStartOfDay(ZoneOffset.UTC));
      row.setEnd(dateEnd(series, date).toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1));
    } else if (index % 2 == 0) {
      row.setStart(date.getStart().withZoneSameInstant(ZoneOffset.UTC));
      row.setEnd(date.getEnd().withZoneSameInstant(ZoneOffset.UTC));
    } else {
      ZonedDateTime seriesStart = series.getStart().withZoneSameInstant(series.getTimeZoneId());
      ZonedDateTime id = normalisedId(series, date);
      ZonedDateTime anchored = seriesStart.withYear(id.getYear())
                                          .withMonth(id.getMonthValue())
                                          .withDayOfMonth(id.getDayOfMonth())
                                          .withHour(seriesStart.getHour())
                                          .withMinute(seriesStart.getMinute());
      row.setStart(anchored.withZoneSameInstant(ZoneOffset.UTC));
      row.setEnd(anchored.plus(Duration.between(series.getStart(), series.getEnd())).withZoneSameInstant(ZoneOffset.UTC));
    }
    row.setStatus(swept.status());
    row.setOccurrence(new EventOccurrence(storedId(series, date, (index + form) % 3, swept), true, swept.movedDays() != 0));
    return row;
  }

  /**
   * The id a row of a date is stored with, in a form: 0, the normalised id a
   * row created now stores; 1, the id the rule gives the date, an all-day one
   * past its UTC midnight once a change of offset skipped midnight; 2, an id
   * of an older formula, the instant of an all-day date's local start, or
   * another time of a timed date's day when the series has a date a day and
   * the row was not moved.
   */
  private static ZonedDateTime storedId(Event series, Event date, int form, SweptRow swept) {
    if (form == 0) {
      return normalisedId(series, date);
    } else if (form == 1) {
      return date.getOccurrence().getId();
    } else if (series.isAllDay()) {
      return dateStart(series, date).withZoneSameInstant(ZoneOffset.UTC);
    } else if (swept.movedDays() == 0 && !Utils.hasSeveralDatesADay(series.getRecurrence())) {
      return date.getStart().with(LocalTime.of(1, 0)).withZoneSameInstant(ZoneOffset.UTC);
    } else {
      return date.getOccurrence().getId();
    }
  }

  /**
   * The start and end a date moved by a number of days takes: an all-day one
   * from the first second of its first day to the last second of its last
   * day, in the series' zone; a timed one also moved by two hours a day.
   */
  private static ZonedDateTime[] movedDates(Event series, ZonedDateTime start, ZonedDateTime end, int days) {
    if (series.isAllDay()) {
      ZoneId zone = series.getTimeZoneId();
      return new ZonedDateTime[] { start.toLocalDate().plusDays(days).atStartOfDay(zone),
          end.toLocalDate().plusDays(days + 1L).atStartOfDay(zone).minusSeconds(1) };
    }
    return new ZonedDateTime[] { start.plusDays(days).plusHours(2L * days), end.plusDays(days).plusHours(2L * days) };
  }

  /**
   * The start of a date of the rule: its own for a timed one, the first
   * second of its day in the series' zone for an all-day one.
   */
  private static ZonedDateTime dateStart(Event series, Event date) {
    return series.isAllDay() ? date.getStart().toLocalDate().atStartOfDay(series.getTimeZoneId()) : date.getStart();
  }

  /**
   * The end of a date of the rule: its own for a timed one; for an all-day
   * one, the last second of the last of the days the series spans, counted
   * from the day it starts.
   */
  private static ZonedDateTime dateEnd(Event series, Event date) {
    if (!series.isAllDay()) {
      return date.getEnd();
    }
    long days = ChronoUnit.DAYS.between(series.getStart().toLocalDate(), series.getEnd().toLocalDate()) + 1;
    return date.getStart().toLocalDate().plusDays(days).atStartOfDay(series.getTimeZoneId()).minusSeconds(1);
  }

  private static ZonedDateTime normalisedId(Event series, Event date) {
    return Utils.getOccurrenceId(series.isAllDay(), date.getOccurrence().getId(), series.getTimeZoneId());
  }

  /**
   * The first date of a range, by index in the whole rule, with no row and
   * not over.
   */
  private static ZonedDateTime expectedFirstCountedDate(List<ZonedDateTime> ends,
                                                        List<ZonedDateTime> ids,
                                                        Map<Integer, SweptRow> rows,
                                                        ZonedDateTime now,
                                                        ZonedDateTime from,
                                                        ZonedDateTime until) {
    for (int i = 0; i < ends.size(); i++) {
      ZonedDateTime id = ids.get(i);
      if (!rows.containsKey(i) && ends.get(i).isAfter(now) && (from == null || !id.isBefore(from))
          && (until == null || id.isBefore(until))) {
        return id;
      }
    }
    return null;
  }

  private static int expectedCountedDates(List<ZonedDateTime> ends,
                                          List<ZonedDateTime> ids,
                                          Map<Integer, SweptRow> rows,
                                          ZonedDateTime now,
                                          ZonedDateTime from,
                                          ZonedDateTime until) {
    int count = 0;
    for (int i = 0; i < ends.size(); i++) {
      ZonedDateTime id = ids.get(i);
      if (!rows.containsKey(i) && ends.get(i).isAfter(now) && (from == null || !id.isBefore(from))
          && (until == null || id.isBefore(until))) {
        count++;
      }
    }
    return count;
  }

  /**
   * The dates carried are the dates of the rule with no row or a row neither
   * cancelled nor moved, at their place in the rule, and the moved rows not
   * cancelled, at their own dates. By start, a date in place before a moved
   * one starting with it: the first not over; else the last, a date in place
   * before a moved one starting with it; else the last date of the rule.
   */
  private static ZonedDateTime expectedReadDate(Event series,
                                                List<ZonedDateTime> starts,
                                                List<ZonedDateTime> ends,
                                                List<ZonedDateTime> ids,
                                                Map<Integer, SweptRow> rows,
                                                ZonedDateTime now) {
    List<CarriedDate> carried = new ArrayList<>();
    for (int i = 0; i < ends.size(); i++) {
      SweptRow row = rows.get(i);
      if (row == null || (row.status() != EventStatus.CANCELLED && row.movedDays() == 0)) {
        carried.add(new CarriedDate(starts.get(i), ends.get(i), ids.get(i), true));
      } else if (row.status() != EventStatus.CANCELLED) {
        ZonedDateTime[] moved = movedDates(series, starts.get(i), ends.get(i), row.movedDays());
        carried.add(new CarriedDate(moved[0], moved[1], ids.get(i), false));
      }
    }
    carried.sort(Comparator.comparing((CarriedDate date) -> date.start().toInstant())
                           .thenComparing(date -> !date.inPlace()));
    CarriedDate last = null;
    for (CarriedDate date : carried) {
      if (date.end().isAfter(now)) {
        return date.id();
      }
      if (last == null || date.start().isAfter(last.start())) {
        last = date;
      }
    }
    return last == null ? ids.get(ids.size() - 1) : last.id();
  }

  private static void check(List<String> mismatches, String label, ZonedDateTime expected, ZonedDateTime found) {
    boolean same = expected == null ? found == null : found != null && expected.isEqual(found);
    if (!same) {
      mismatches.add(label + ": " + expected + " expected, " + found + " found");
    }
  }

  private static void check(List<String> mismatches, String label, int expected, int found) {
    if (expected != found) {
      mismatches.add(label + ": " + expected + " expected, " + found + " found");
    }
  }

  private record SweptRule(EventRecurrenceFrequency frequency, int count) {
  }

  /**
   * A swept row: its status, and the days its date is moved by, 0 when it is
   * not moved.
   */
  private record SweptRow(EventStatus status, int movedDays) {
  }

  private record CarriedDate(ZonedDateTime start, ZonedDateTime end, ZonedDateTime id, boolean inPlace) {
  }
}
