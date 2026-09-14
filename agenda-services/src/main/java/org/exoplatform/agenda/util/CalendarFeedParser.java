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
package org.exoplatform.agenda.util;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAvailability;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;

/**
 * Reads an iCalendar document into the occurrences a subscription imports
 * (EXO-90278).
 * <p>
 * <b>Occurrences, not series.</b> A recurring event is expanded by ical4j —
 * RRULE, RDATE and EXDATE, in the event's own time zone, daylight saving
 * included — within the window, and each instance a RECURRENCE-ID override
 * replaces is taken from the override. Agenda's recurrence model cannot hold
 * every rule a feed may carry, and an expansion is one truthful path for all of
 * them. Every occurrence is identified by its UID and the instant it was
 * originally scheduled at, so a refresh finds the same occurrence again.
 * <p>
 * <b>What is kept</b>: title, location and description as plain text, the
 * times, whether the event is all-day, and its transparency. A cancelled event
 * or instance is left out. Organizers, attendees, alarms, attachments and URLs
 * are never read.
 * <p>
 * <b>Bounds</b>: rules repeating by the second or the minute are not expanded,
 * the expansion stops once it has produced four times the number of occurrences
 * kept, and only the earliest {@code maxEvents} occurrences are kept.
 */
public final class CalendarFeedParser {

  /** Longest title or location kept. */
  public static final int     MAX_TEXT        = 2000;

  /** Longest description kept, in characters of plain text. */
  public static final int     MAX_DESCRIPTION = 10000;

  /** Longest calendar name kept. */
  public static final int     MAX_NAME        = 200;

  /** How many occurrences may be expanded per occurrence kept, at most. */
  private static final int    EXPANSION_RATIO = 4;

  /**
   * Most candidate dates one step of a rule may produce before its instances are
   * selected: ical4j builds a step's candidates whole, as the product of the
   * rule's BY-lists, so a rule is refused on that product, not on its instances.
   */
  private static final int    MAX_CANDIDATES_PER_STEP = 1000;

  /**
   * Most candidate dates one rule may cost. A rule with a COUNT is walked from its
   * first instance, whatever the window, and ical4j keeps what it walks.
   */
  private static final long   MAX_RULE_WORK           = 500_000;

  /**
   * How many EXDATE comparisons cost one unit of work. ical4j removes excluded
   * instances with a list scan per instance, two comparisons per EXDATE value.
   * A comparison was measured 28 to 96 times cheaper than a candidate date
   * built; counting ten per unit errs on the strict side.
   */
  private static final int    COMPARISONS_PER_UNIT    = 10;

  /** Most candidate dates all the rules of one document may cost together. */
  private static final long   MAX_DOCUMENT_WORK       = 4_000_000;

  private static final Pattern HTML_TAG       = Pattern.compile("<\\s*/?\\s*[a-zA-Z!][^>]*>");

  private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<\\s*(br|/p|/div|/li)\\s*/?\\s*>");

  private static final Pattern WEEKS          = Pattern.compile("^P(\\d{1,4})W$");

  private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]{1,6});");

  /**
   * One occurrence, ready to be written as an agenda event.
   *
   * @param key UID and original instant, stable across refreshes
   * @param summary the title, plain text, may be null
   * @param description the description, plain text, may be null
   * @param location the location, plain text, may be null
   * @param allDay whether the occurrence spans whole days
   * @param start the start; for an all-day occurrence, its first day at UTC
   *          midnight
   * @param end the end; for an all-day occurrence, its last day at UTC midnight
   * @param timeZone the time zone the event is expressed in
   * @param availability FREE for a transparent event, BUSY otherwise
   */
  public record ImportedEvent(String key,
                              String summary,
                              String description,
                              String location,
                              boolean allDay,
                              ZonedDateTime start,
                              ZonedDateTime end,
                              ZoneId timeZone,
                              EventAvailability availability) {

    /**
     * A digest of everything imported, so an unchanged occurrence is not
     * rewritten.
     *
     * @return lowercase hexadecimal SHA-256
     */
    public String contentHash() {
      return sha256(String.join("\0",
                                StringUtils.defaultString(summary),
                                StringUtils.defaultString(description),
                                StringUtils.defaultString(location),
                                String.valueOf(allDay),
                                String.valueOf(start.toInstant().toEpochMilli()),
                                String.valueOf(end.toInstant().toEpochMilli()),
                                timeZone.getId(),
                                String.valueOf(availability)));
    }
  }

  /**
   * A document read.
   *
   * @param name the calendar's own name, null when it names none
   * @param refreshInterval the refresh interval it advertises, null when none
   * @param events the occurrences kept, earliest first
   * @param truncated whether occurrences were left out beyond the limit
   */
  public record ParsedCalendar(String name, Duration refreshInterval, List<ImportedEvent> events, boolean truncated) {
  }

  /**
   * Not instantiable.
   */
  private CalendarFeedParser() {
  }

  /**
   * Reads a document.
   *
   * @param body the bytes read, UTF-8
   * @param windowStart occurrences ending at or before this instant are left out
   * @param windowEnd occurrences starting at or after this instant are left out
   * @param maxEvents most occurrences kept
   * @return the document read
   * @throws CalendarFeedException when the body is not an iCalendar document, or
   *           cannot be read as one
   */
  public static ParsedCalendar parse(byte[] body, Instant windowStart, Instant windowEnd, int maxEvents) throws CalendarFeedException {
    String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
    text = StringUtils.stripStart(StringUtils.removeStart(text, "﻿"), null);
    if (!StringUtils.startsWithIgnoreCase(text, "BEGIN:VCALENDAR")) {
      throw new CalendarFeedException(CalendarFeedException.NOT_A_CALENDAR);
    }
    net.fortuna.ical4j.model.Calendar calendar;
    try {
      calendar = new CalendarBuilder().build(new StringReader(text));
    } catch (IOException | ParserException | RuntimeException e) {
      throw new CalendarFeedException(CalendarFeedException.MALFORMED_CALENDAR, e);
    }
    String name = StringUtils.left(plainText(propertyValue(calendar.getProperty("X-WR-CALNAME"))), MAX_NAME);
    Duration refresh = duration(propertyValue(calendar.getProperty("REFRESH-INTERVAL")));
    if (refresh == null) {
      refresh = duration(propertyValue(calendar.getProperty("X-PUBLISHED-TTL")));
    }
    ZoneId floatingZone = zone(propertyValue(calendar.getProperty("X-WR-TIMEZONE")));
    Period window = new Period(new DateTime(windowStart.toEpochMilli()), new DateTime(windowEnd.toEpochMilli()));

    Map<String, VEvent> masters = new LinkedHashMap<>();
    Map<String, Map<String, VEvent>> overrides = new HashMap<>();
    for (Object component : calendar.getComponents(Component.VEVENT)) {
      VEvent event = (VEvent) component;
      String uid = uid(event);
      if (event.getRecurrenceId() == null) {
        masters.putIfAbsent(uid, event);
      } else {
        overrides.computeIfAbsent(uid, key -> new HashMap<>()).put(instantKey(event.getRecurrenceId()), event);
      }
    }

    int budget = Math.max(maxEvents, 1) * EXPANSION_RATIO;
    long[] work = { MAX_DOCUMENT_WORK };
    List<ImportedEvent> events = new ArrayList<>();
    boolean truncated = false;
    for (Map.Entry<String, VEvent> master : masters.entrySet()) {
      if (events.size() >= budget) {
        truncated = true;
        break;
      }
      if (!expand(master.getKey(),
                  master.getValue(),
                  overrides.getOrDefault(master.getKey(), Map.of()),
                  window,
                  floatingZone,
                  events,
                  budget - events.size(),
                  work)) {
        truncated = true;
      }
    }
    for (Map.Entry<String, Map<String, VEvent>> byUid : overrides.entrySet()) {
      for (Map.Entry<String, VEvent> override : byUid.getValue().entrySet()) {
        if (!isCancelled(override.getValue())) {
          add(events,
              occurrence(byUid.getKey() + "|" + override.getKey(), override.getValue(), null, null, floatingZone),
              windowStart,
              windowEnd);
        }
      }
    }
    events.sort(Comparator.comparing((ImportedEvent event) -> event.start().toInstant()).thenComparing(ImportedEvent::key));
    if (events.size() > maxEvents) {
      events = new ArrayList<>(events.subList(0, maxEvents));
      truncated = true;
    }
    return new ParsedCalendar(StringUtils.isBlank(name) ? null : name, refresh, events, truncated);
  }

  /**
   * Turns a plain-text description into the markup agenda renders a
   * description with: escaped, line breaks kept.
   *
   * @param text plain text, may be null
   * @return the markup, null for no text
   */
  public static String descriptionMarkup(String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    String escaped = text.replace("&", "&amp;")
                         .replace("<", "&lt;")
                         .replace(">", "&gt;")
                         .replace("\"", "&quot;")
                         .replace("'", "&#39;");
    return escaped.replace("\n", "<br>");
  }

  /**
   * Expands one master event within the window, the instances an override
   * replaces left to the override.
   * <p>
   * ical4j expands a series with no limit of its own, so its cost is bounded
   * before it runs, from the rules alone:
   * <ul>
   * <li>a rule firing every second or minute, or whose one step builds more than
   * {@link #MAX_CANDIDATES_PER_STEP} candidates, is not expanded;</li>
   * <li>an instance overlaps the window when it starts after the window's start
   * minus the instance's length, and ical4j walks from there: a series whose
   * rules all ended before is not expanded at all, and one whose instances
   * overlapping the window's start alone exceed {@code allowed} is refused;</li>
   * <li>the window is shortened, from the series' first instance in it, so the
   * series yields about {@code allowed} instances at most;</li>
   * <li>ical4j reaches the start of its walk one step at a time from the series'
   * first instance, and walks a rule with a COUNT from there building every
   * candidate: a series costing more than {@link #MAX_RULE_WORK} steps and
   * candidates, or more than the document has left, is not expanded.</li>
   * </ul>
   *
   * @param uid the event's UID
   * @param master the event
   * @param overridden the overrides of the event, by original instant
   * @param window the window
   * @param floatingZone the zone of floating times
   * @param events where the occurrences go
   * @param allowed how many more instances may be built
   * @param work the candidates the document may still cost, decreased by what
   *          this series costs
   * @return false when instances of the series were left out
   */
  private static boolean expand(String uid,
                                VEvent master,
                                Map<String, VEvent> overridden,
                                Period window,
                                ZoneId floatingZone,
                                List<ImportedEvent> events,
                                int allowed,
                                long[] work) {
    if (isCancelled(master) || master.getStartDate() == null || master.getStartDate().getDate() == null) {
      return true;
    }
    Instant windowStart = Instant.ofEpochMilli(window.getStart().getTime());
    Instant windowEnd = Instant.ofEpochMilli(window.getEnd().getTime());
    boolean recurring = master.getProperty(Property.RRULE) != null || master.getProperty(Property.RDATE) != null;
    if (!recurring) {
      add(events, occurrence(uid + "|", master, null, null, floatingZone), windowStart, windowEnd);
      return true;
    }
    Duration length = instanceLength(master);
    if (length == null) {
      return false;
    }
    Instant seed = master.getStartDate().getDate().toInstant();
    Instant reach = windowStart.minus(length);
    // floating and all-day dates are read in the JVM's zone: a day of margin
    // before a series is taken as ended
    Instant ended = reach.minus(Duration.ofDays(1));
    List<Recur> rules = new ArrayList<>();
    boolean anyInstance = !master.getProperties(Property.RDATE).isEmpty();
    for (Object property : master.getProperties(Property.RRULE)) {
      Recur rule = ((RRule) property).getRecur();
      rules.add(rule);
      anyInstance |= rule == null || rule.getUntil() == null || !rule.getUntil().toInstant().isBefore(ended);
    }
    if (!anyInstance) {
      // every rule ended before an instance could overlap the window
      return true;
    }
    for (Object property : master.getProperties(Property.EXRULE)) {
      rules.add(((ExRule) property).getRecur());
    }
    double rate = 0;
    for (Recur rule : rules) {
      Duration step = shortestStep(rule);
      if (step == null || candidatesPerStep(rule) > MAX_CANDIDATES_PER_STEP) {
        // a rule firing every second or minute is no calendar anybody reads,
        // and a step building that many candidates exhausts the memory
        return false;
      }
      rate += candidatesPerStep(rule) / step.toMillis();
    }
    Instant firstStart = seed.isAfter(reach) ? seed : reach;
    // shortened from the series' first instance in the window, not from the
    // window's start: a dense series starting late in the window keeps its
    // first instances
    Instant rangeStart = seed.isAfter(windowStart) ? seed : windowStart;
    boolean shortened = false;
    Instant end = windowEnd;
    if (rate > 0) {
      double available = Math.max(allowed, 1) / rate - Math.max(Duration.between(firstStart, rangeStart).toMillis(), 0);
      if (available <= 0) {
        // the instances overlapping the window's start alone are too many
        return false;
      }
      if (rangeStart.isBefore(windowEnd) && available < Duration.between(rangeStart, windowEnd).toMillis()) {
        end = rangeStart.plusMillis((long) Math.ceil(available));
        shortened = true;
      }
    }
    int rdateValues = dateValues(master, Property.RDATE);
    int exdateValues = dateValues(master, Property.EXDATE);
    double instances = rdateValues + 1d;
    for (Recur rule : rules) {
      instances += stepsBetween(firstStart, end, shortestStep(rule)) * candidatesPerStep(rule) + candidatesPerStep(rule);
    }
    // ical4j scans every EXDATE value twice for each instance it built
    double cost = rdateValues + exdateValues + instances * exdateValues * 2d / COMPARISONS_PER_UNIT;
    for (Recur rule : rules) {
      Duration step = shortestStep(rule);
      Instant walkFrom = rule.getCount() > 0 ? seed : firstStart;
      Instant walkTo = rule.getUntil() != null && rule.getUntil().toInstant().isBefore(end) ? rule.getUntil().toInstant() : end;
      cost += stepsBetween(seed, walkFrom, step);
      cost += Math.max(stepsBetween(walkFrom, walkTo, step), 1) * candidatesPerStep(rule);
    }
    if (cost > MAX_RULE_WORK || cost > work[0]) {
      return false;
    }
    work[0] -= (long) cost;
    boolean allDay = !(master.getStartDate().getDate() instanceof DateTime);
    PeriodList periods;
    try {
      periods = master.calculateRecurrenceSet(new Period(new DateTime(windowStart.toEpochMilli()),
                                                         new DateTime(end.toEpochMilli())));
    } catch (RuntimeException e) {
      // a negative duration far enough moves ical4j's start past the period's
      // end, and it refuses the range
      return false;
    }
    int added = 0;
    for (Object value : periods) {
      if (added >= Math.max(allowed, 1)) {
        return false;
      }
      Period period = (Period) value;
      String key = allDay ? utcDate(period.getStart()).toString() : instantKey(period.getStart());
      if (!overridden.containsKey(key)) {
        int before = events.size();
        add(events, occurrence(uid + "|" + key, master, period.getStart(), period.getEnd(), floatingZone), windowStart, windowEnd);
        added += events.size() - before;
      }
    }
    return !shortened;
  }

  /**
   * How long one instance of a series lasts, read the way ical4j 3.2.19 reads it
   * to move the start of its walk back: DURATION first, else DTEND, else DUE,
   * else nothing. A negative length is counted by its size.
   *
   * @param master the series
   * @return the length, or null when it cannot be read
   */
  private static Duration instanceLength(VEvent master) {
    try {
      Instant start = master.getStartDate().getDate().toInstant();
      net.fortuna.ical4j.model.property.Duration duration = master.getProperty(Property.DURATION);
      if (duration != null && duration.getDuration() != null) {
        return Duration.between(start, ZonedDateTime.ofInstant(start, ZoneOffset.UTC).plus(duration.getDuration()).toInstant()).abs();
      }
      DateProperty end = master.getProperty(Property.DTEND);
      if (end == null || end.getDate() == null) {
        end = master.getProperty(Property.DUE);
      }
      return end == null || end.getDate() == null ? Duration.ZERO : Duration.between(start, end.getDate().toInstant()).abs();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * How many dates or periods the properties of a kind carry together: one
   * RDATE or EXDATE property may list any number.
   *
   * @param master the series
   * @param name RDATE or EXDATE
   * @return the number of values
   */
  private static int dateValues(VEvent master, String name) {
    int values = 0;
    for (Object property : master.getProperties(name)) {
      DateListProperty dates = (DateListProperty) property;
      values += dates.getDates() == null ? 0 : dates.getDates().size();
      if (property instanceof RDate rdate && rdate.getPeriods() != null) {
        values += rdate.getPeriods().size();
      }
    }
    return values;
  }

  /**
   * How many steps of a rule fit between two instants, counted generously.
   *
   * @param from the first instant
   * @param to the last instant
   * @param step the shortest step of the rule
   * @return the steps, 0 when {@code to} is not after {@code from}
   */
  private static double stepsBetween(Instant from, Instant to, Duration step) {
    return to.isAfter(from) ? (double) Duration.between(from, to).toMillis() / step.toMillis() + 1 : 0;
  }

  /**
   * The shortest a step of a rule can last, a daylight-saving change included.
   *
   * @param rule the rule
   * @return the duration, or null for a rule firing every second or minute, or
   *         with no frequency
   */
  private static Duration shortestStep(Recur rule) {
    if (rule == null || rule.getFrequency() == null) {
      return null;
    }
    Duration unit = switch (rule.getFrequency().name()) {
    case "YEARLY" -> Duration.ofHours(365L * 24 - 1);
    case "MONTHLY" -> Duration.ofHours(28L * 24 - 1);
    case "WEEKLY" -> Duration.ofHours(7L * 24 - 1);
    case "DAILY" -> Duration.ofHours(23);
    case "HOURLY" -> Duration.ofHours(1);
    default -> null;
    };
    return unit == null ? null : unit.multipliedBy(Math.max(rule.getInterval(), 1));
  }

  /**
   * An upper bound of the candidates one step of a rule builds: the product of
   * its BY-lists, each counted for what it expands to within the step.
   *
   * @param rule the rule
   * @return the bound, as a double so that no product overflows
   */
  private static double candidatesPerStep(Recur rule) {
    boolean yearly = "YEARLY".equals(rule.getFrequency().name());
    boolean monthly = "MONTHLY".equals(rule.getFrequency().name());
    int months = rule.getMonthList().size();
    int weeks = rule.getWeekNoList().size();
    int days = rule.getDayList().size();
    double candidates = Math.max(months, 1);
    if (weeks > 0) {
      candidates *= days > 0 ? weeks : weeks * 7d;
    }
    candidates *= Math.max(rule.getYearDayList().size(), 1);
    if (!rule.getMonthDayList().isEmpty()) {
      candidates *= rule.getMonthDayList().size() * (yearly && months == 0 ? 12d : 1d);
    }
    if (days > 0) {
      candidates *= days * (yearly && months == 0 && weeks == 0 ? 53d : (yearly || monthly) ? 5d : 1d);
    }
    candidates *= Math.max(rule.getHourList().size(), 1);
    candidates *= Math.max(rule.getMinuteList().size(), 1);
    candidates *= Math.max(rule.getSecondList().size(), 1);
    return candidates;
  }

  /**
   * Adds an occurrence when it overlaps the window.
   *
   * @param events where the occurrences go
   * @param event the occurrence, null when it could not be read
   * @param windowStart start of the window
   * @param windowEnd end of the window
   */
  private static void add(List<ImportedEvent> events, ImportedEvent event, Instant windowStart, Instant windowEnd) {
    if (event == null) {
      return;
    }
    Instant start = event.start().toInstant();
    Instant end = event.allDay() ? event.end().toInstant().plus(Duration.ofDays(1)) : event.end().toInstant();
    if (end.isAfter(windowStart) && start.isBefore(windowEnd)) {
      events.add(event);
    }
  }

  /**
   * Builds one occurrence of an event.
   *
   * @param key the occurrence's identity
   * @param event the event, master or override
   * @param occurrenceStart the instance start of an expanded series, null to
   *          read the event's own
   * @param occurrenceEnd the instance end of an expanded series, null to read
   *          the event's own
   * @param floatingZone the zone of floating times
   * @return the occurrence, or null when its times cannot be read
   */
  private static ImportedEvent occurrence(String key,
                                          VEvent event,
                                          Date occurrenceStart,
                                          Date occurrenceEnd,
                                          ZoneId floatingZone) {
    DateProperty startProperty = event.getStartDate();
    if (startProperty == null || startProperty.getDate() == null) {
      return null;
    }
    boolean allDay = !(startProperty.getDate() instanceof DateTime);
    Date start = occurrenceStart == null ? startProperty.getDate() : occurrenceStart;
    Date end = occurrenceEnd;
    if (end == null) {
      DateProperty endProperty = event.getEndDate(true);
      end = endProperty == null ? null : endProperty.getDate();
    }
    ZoneId zone = eventZone(startProperty, floatingZone);
    EventAvailability availability = "TRANSPARENT".equalsIgnoreCase(propertyValue(event.getProperty(Property.TRANSP)))
                                                                                                                          ? EventAvailability.FREE
                                                                                                                          : EventAvailability.BUSY;
    String summary = StringUtils.left(plainText(propertyValue(event.getSummary())), MAX_TEXT);
    String description = StringUtils.left(plainText(propertyValue(event.getDescription())), MAX_DESCRIPTION);
    String location = StringUtils.left(plainText(propertyValue(event.getLocation())), MAX_TEXT);
    if (allDay) {
      LocalDate firstDay = utcDate(start);
      LocalDate endExclusive = end == null ? firstDay.plusDays(1) : utcDate(end);
      if (!endExclusive.isAfter(firstDay)) {
        endExclusive = firstDay.plusDays(1);
      }
      return new ImportedEvent(key,
                               summary,
                               description,
                               location,
                               true,
                               firstDay.atStartOfDay(ZoneOffset.UTC),
                               endExclusive.minusDays(1).atStartOfDay(ZoneOffset.UTC),
                               ZoneOffset.UTC,
                               availability);
    }
    Instant startInstant = instant(start, floatingZone);
    Instant endInstant = end == null ? startInstant : instant(end, floatingZone);
    if (endInstant.isBefore(startInstant)) {
      endInstant = startInstant;
    }
    return new ImportedEvent(key,
                             summary,
                             description,
                             location,
                             false,
                             ZonedDateTime.ofInstant(startInstant, zone),
                             ZonedDateTime.ofInstant(endInstant, zone),
                             zone,
                             availability);
  }

  /**
   * The instant a date-time stands for, a floating one read in the given zone.
   *
   * @param date the date-time
   * @param floatingZone the zone of floating times
   * @return the instant
   */
  private static Instant instant(Date date, ZoneId floatingZone) {
    if (isFloating(date)) {
      // ical4j reads a floating time in the JVM's zone; its local fields are
      // what the feed wrote
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).atZone(floatingZone).toInstant();
    }
    return Instant.ofEpochMilli(date.getTime());
  }

  /**
   * The key of an original instant, stable whichever node reads it: the UTC day
   * of a date, the local fields of a floating time, the epoch otherwise.
   *
   * @param date the date or date-time
   * @return the key
   */
  private static String instantKey(Date date) {
    if (!(date instanceof DateTime)) {
      return utcDate(date).toString();
    }
    if (isFloating(date)) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).toString();
    }
    return String.valueOf(date.getTime());
  }

  /**
   * The key of an override's RECURRENCE-ID.
   *
   * @param recurrenceId the property
   * @return the key of the instant it names
   */
  private static String instantKey(DateProperty recurrenceId) {
    return instantKey(recurrenceId.getDate());
  }

  /**
   * Whether a date-time is floating: neither UTC nor bound to a zone.
   *
   * @param date the date or date-time
   * @return true for a floating date-time
   */
  private static boolean isFloating(Date date) {
    return date instanceof DateTime dateTime && !dateTime.isUtc() && dateTime.getTimeZone() == null;
  }

  /**
   * The UTC day of a date — ical4j keeps a DATE value at UTC midnight.
   *
   * @param date the date
   * @return the day
   */
  private static LocalDate utcDate(Date date) {
    return Instant.ofEpochMilli(date.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
  }

  /**
   * The zone an event is written in: its TZID when Java knows it, UTC for a UTC
   * time or an unknown TZID (the instants stay right), the floating zone for a
   * floating time.
   *
   * @param start the DTSTART property
   * @param floatingZone the zone of floating times
   * @return the zone
   */
  private static ZoneId eventZone(DateProperty start, ZoneId floatingZone) {
    if (isFloating(start.getDate())) {
      return floatingZone;
    }
    TzId tzId = start.getParameter(net.fortuna.ical4j.model.Parameter.TZID);
    ZoneId zone = tzId == null ? null : zoneOrNull(tzId.getValue());
    return zone == null ? ZoneOffset.UTC : zone;
  }

  /**
   * The zone of floating times: {@code X-WR-TIMEZONE} when Java knows it, UTC
   * otherwise.
   *
   * @param value the property value, may be null
   * @return the zone
   */
  private static ZoneId zone(String value) {
    ZoneId zone = zoneOrNull(value);
    return zone == null ? ZoneOffset.UTC : zone;
  }

  /**
   * A zone by its identifier, or null.
   *
   * @param value the identifier
   * @return the zone, or null when unknown
   */
  private static ZoneId zoneOrNull(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      return ZoneId.of(value.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * An event's UID, or a stable stand-in built from its start and title when it
   * carries none.
   *
   * @param event the event
   * @return the UID
   */
  private static String uid(VEvent event) {
    String uid = propertyValue(event.getUid());
    if (StringUtils.isNotBlank(uid)) {
      return uid.trim();
    }
    return "no-uid:" + sha256(propertyValue(event.getStartDate()) + "\0" + propertyValue(event.getSummary()));
  }

  /**
   * Whether an event or instance is cancelled.
   *
   * @param event the event
   * @return true when its STATUS is CANCELLED
   */
  private static boolean isCancelled(VEvent event) {
    return "CANCELLED".equalsIgnoreCase(propertyValue(event.getStatus()));
  }

  /**
   * A duration as a feed writes it: ISO-8601, weeks included.
   *
   * @param value the value, may be null
   * @return the duration, or null when there is none or it cannot be read
   */
  static Duration duration(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String trimmed = value.trim().toUpperCase();
    Matcher weeks = WEEKS.matcher(trimmed);
    if (weeks.matches()) {
      return Duration.ofDays(7L * Integer.parseInt(weeks.group(1)));
    }
    try {
      Duration duration = Duration.parse(trimmed);
      return duration.isNegative() || duration.isZero() ? null : duration;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Plain text from a value a feed may have written as markup: tags removed,
   * line-breaking tags kept as line breaks, common entities decoded, control
   * characters dropped, blank lines collapsed.
   *
   * @param value the value, may be null
   * @return the text, null when blank
   */
  static String plainText(String value) {
    if (value == null) {
      return null;
    }
    String text = value;
    if (HTML_TAG.matcher(text).find()) {
      text = LINE_BREAK_TAG.matcher(text).replaceAll("\n");
      text = HTML_TAG.matcher(text).replaceAll("");
      text = decodeEntities(text);
    }
    StringBuilder clean = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
        clean.append(c);
      }
    }
    String result = clean.toString().replace("\r", "").replaceAll("\n{3,}", "\n\n").trim();
    return result.isEmpty() ? null : result;
  }

  /**
   * Decodes the entities markup commonly carries.
   *
   * @param text the text
   * @return the text decoded
   */
  private static String decodeEntities(String text) {
    Matcher numeric = NUMERIC_ENTITY.matcher(text);
    StringBuilder decoded = new StringBuilder();
    while (numeric.find()) {
      String code = numeric.group(1);
      int codePoint;
      try {
        codePoint = code.startsWith("x") || code.startsWith("X") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
      } catch (NumberFormatException e) {
        codePoint = -1;
      }
      String replacement = Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : "";
      numeric.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
    }
    numeric.appendTail(decoded);
    return decoded.toString()
                  .replace("&nbsp;", " ")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&apos;", "'")
                  .replace("&amp;", "&");
  }

  /**
   * The value of a property, null when absent.
   *
   * @param property the property, may be null
   * @return its value
   */
  private static String propertyValue(Property property) {
    return property == null ? null : property.getValue();
  }

  /**
   * SHA-256 of a text.
   *
   * @param text the text
   * @return lowercase hexadecimal digest
   */
  static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available on this JVM", e);
    }
  }

}
