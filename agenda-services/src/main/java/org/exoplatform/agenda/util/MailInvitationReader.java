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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.MailInvitationEvent;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RRule;

/**
 * Reads the iCalendar object of an invitation received by mail into the event
 * agenda lands (EXO-90866).
 * <p>
 * <b>The object is the sender's.</b> It came in a mail from outside, so it is
 * bounded before ical4j reads it and narrowed after: the text's length, the
 * number of components, of excluded dates and of overridden occurrences are
 * capped, and an object over any cap is refused rather than read in part; the
 * title, location and description are kept as plain text, capped as a
 * subscribed feed's are ({@link CalendarFeedParser}); a rule that would cost
 * agenda more to expand than a feed is allowed to is refused. Attendees are
 * never read — the only person a landing acts for is the user who asked — and
 * neither are alarms, attachments or conference properties. The organiser is
 * read as a bare address, only to be compared with the organiser of an event
 * the user already holds.
 * <p>
 * <b>One event per object.</b> An invitation is about one UID (RFC 5546): an
 * object naming another UID than the one the reader showed, or several, is
 * refused, and so is one carrying only an occurrence of a series.
 * <p>
 * Every refusal is an {@link IllegalArgumentException}: the sender's content,
 * not an incident.
 */
public final class MailInvitationReader {

  /** Longest object read, in characters. */
  public static final int      MAX_CHARS            = 1_000_000;

  /** Longest UID kept: it is stored beside the event and looked up by. */
  public static final int      MAX_UID              = 255;

  /** Most VEVENT components an object may carry. */
  public static final int      MAX_COMPONENTS       = 200;

  /** Most occurrences a series' EXDATEs may exclude. */
  public static final int      MAX_EXCLUSIONS       = 200;

  /** Most occurrences an object may override. */
  public static final int      MAX_OVERRIDES        = 50;

  /** Longest organiser address kept. */
  private static final int     MAX_ADDRESS          = 320;

  /** What a bare mail address looks like, enough to compare two of them. */
  private static final Pattern ADDRESS              = Pattern.compile("^[^\\s@<>\"]+@[^\\s@<>\"]+$");

  /** The frequencies a landed series may have: a meeting does not repeat by the hour. */
  private static final Set<EventRecurrenceFrequency> LANDABLE_FREQUENCIES = Set.of(EventRecurrenceFrequency.DAILY,
                                                                                    EventRecurrenceFrequency.WEEKLY,
                                                                                    EventRecurrenceFrequency.MONTHLY,
                                                                                    EventRecurrenceFrequency.YEARLY);

  /**
   * Not instantiable: static helpers only.
   */
  private MailInvitationReader() {
  }

  /**
   * Reads the event an invitation describes.
   *
   * @param icalendar the object, as text
   * @param expectedUid the UID the reader showed the user
   * @return the event, its exclusions and its overrides
   * @throws IllegalArgumentException when the object is over a cap, cannot be
   *           read, names another or more than one event, carries only an
   *           occurrence, has no start, or carries a rule agenda does not land
   */
  public static MailInvitationEvent read(String icalendar, String expectedUid) {
    String uid = StringUtils.trimToNull(expectedUid);
    if (uid == null || uid.length() > MAX_UID) {
      throw new IllegalArgumentException("The invitation's UID is missing or longer than " + MAX_UID + " characters");
    }
    if (icalendar == null || icalendar.length() > MAX_CHARS) {
      throw new IllegalArgumentException("The invitation is missing or longer than " + MAX_CHARS + " characters");
    }
    String text = StringUtils.stripStart(StringUtils.removeStart(icalendar, "﻿"), null);
    if (!StringUtils.startsWithIgnoreCase(text, "BEGIN:VCALENDAR")) {
      throw new IllegalArgumentException("The invitation is not an iCalendar object");
    }
    net.fortuna.ical4j.model.Calendar calendar;
    try {
      calendar = new CalendarBuilder().build(new StringReader(text));
    } catch (IOException | ParserException | RuntimeException e) {
      throw new IllegalArgumentException("The invitation cannot be read as iCalendar", e);
    }
    String method = StringUtils.upperCase(StringUtils.trimToNull(valueOf(calendar.getProperty(Property.METHOD))), Locale.ROOT);
    List<VEvent> components = new ArrayList<>();
    for (Object component : calendar.getComponents(Component.VEVENT)) {
      components.add((VEvent) component);
    }
    if (components.isEmpty()) {
      throw new IllegalArgumentException("The invitation carries no event");
    }
    if (components.size() > MAX_COMPONENTS) {
      throw new IllegalArgumentException("The invitation carries more than " + MAX_COMPONENTS + " events");
    }
    VEvent master = null;
    List<VEvent> overridden = new ArrayList<>();
    for (VEvent component : components) {
      if (!uid.equals(StringUtils.trim(valueOf(component.getUid())))) {
        throw new IllegalArgumentException("The invitation names another event than " + uid);
      }
      if (component.getRecurrenceId() == null) {
        if (master != null) {
          throw new IllegalArgumentException("The invitation carries event " + uid + " twice");
        }
        master = component;
      } else {
        overridden.add(component);
      }
    }
    if (master == null) {
      throw new IllegalArgumentException("The invitation is about one occurrence of a series, which is not landed");
    }
    if (overridden.size() > MAX_OVERRIDES) {
      throw new IllegalArgumentException("The invitation overrides more than " + MAX_OVERRIDES + " occurrences");
    }
    MailInvitationEvent read = component(master, uid, method, null);
    EventRecurrence recurrence = recurrenceOf(master, read.timeZone());
    if (recurrence == null) {
      // A single event: overrides and exclusions name occurrences it does not have.
      return withSeries(read, null, List.of(), List.of());
    }
    List<ZonedDateTime> excluded = exclusionsOf(master, read.timeZone());
    List<MailInvitationEvent> overrides = new ArrayList<>();
    for (VEvent component : overridden) {
      overrides.add(component(component, uid, method, occurrenceOf(component.getRecurrenceId(), read.timeZone())));
    }
    return withSeries(read, recurrence, excluded, overrides);
  }

  /**
   * One component of the object: the master or an override.
   *
   * @param event the component
   * @param uid its UID, already checked
   * @param method the object's METHOD
   * @param occurrenceId the occurrence an override replaces, null for the
   *          master
   * @return the component read, with no series
   * @throws IllegalArgumentException when it has no start
   */
  private static MailInvitationEvent component(VEvent event, String uid, String method, ZonedDateTime occurrenceId) {
    DateProperty startProperty = event.getStartDate();
    if (startProperty == null || startProperty.getDate() == null) {
      throw new IllegalArgumentException("The invitation " + uid + " carries an event with no start");
    }
    boolean allDay = !(startProperty.getDate() instanceof DateTime);
    DateProperty endProperty = event.getEndDate(true);
    Date end = endProperty == null ? null : endProperty.getDate();
    String transparency = valueOf(event.getProperty(Property.TRANSP));
    EventAvailability availability = transparency == null ? EventAvailability.DEFAULT
                                                          : "TRANSPARENT".equalsIgnoreCase(transparency.trim()) ? EventAvailability.FREE
                                                                                                                 : EventAvailability.BUSY;
    ZonedDateTime start;
    ZonedDateTime endTime;
    ZoneId zone;
    if (allDay) {
      LocalDate firstDay = utcDate(startProperty.getDate());
      LocalDate endExclusive = end == null ? firstDay.plusDays(1) : utcDate(end);
      if (!endExclusive.isAfter(firstDay)) {
        endExclusive = firstDay.plusDays(1);
      }
      zone = ZoneOffset.UTC;
      start = firstDay.atStartOfDay(ZoneOffset.UTC);
      endTime = endExclusive.minusDays(1).atStartOfDay(ZoneOffset.UTC);
    } else {
      zone = zoneOf(startProperty);
      Instant startInstant = instantOf(startProperty.getDate());
      Instant endInstant = end == null ? startInstant : instantOf(end);
      if (endInstant.isBefore(startInstant)) {
        endInstant = startInstant;
      }
      start = ZonedDateTime.ofInstant(startInstant, zone);
      endTime = ZonedDateTime.ofInstant(endInstant, zone);
    }
    return new MailInvitationEvent(uid,
                                   method,
                                   sequenceOf(event),
                                   "CANCELLED".equalsIgnoreCase(StringUtils.trim(valueOf(event.getStatus()))),
                                   organizerOf(event),
                                   StringUtils.left(CalendarFeedParser.plainText(valueOf(event.getSummary())),
                                                    CalendarFeedParser.MAX_TEXT),
                                   StringUtils.left(CalendarFeedParser.plainText(valueOf(event.getDescription())),
                                                    CalendarFeedParser.MAX_DESCRIPTION),
                                   StringUtils.left(CalendarFeedParser.plainText(valueOf(event.getLocation())),
                                                    CalendarFeedParser.MAX_TEXT),
                                   StringUtils.trimToNull(StringUtils.left(valueOf(event.getUrl()), CalendarFeedParser.MAX_TEXT)),
                                   allDay,
                                   start,
                                   endTime,
                                   zone,
                                   availability,
                                   null,
                                   occurrenceId,
                                   List.of(),
                                   List.of());
  }

  /**
   * The master read, with its series.
   *
   * @param master the master read
   * @param recurrence its rule, null for a single event
   * @param excluded the occurrences it excludes
   * @param overrides the occurrences it overrides
   * @return the event
   */
  private static MailInvitationEvent withSeries(MailInvitationEvent master,
                                                EventRecurrence recurrence,
                                                List<ZonedDateTime> excluded,
                                                List<MailInvitationEvent> overrides) {
    return new MailInvitationEvent(master.uid(),
                                   master.method(),
                                   master.sequence(),
                                   master.cancelled(),
                                   master.organizer(),
                                   master.summary(),
                                   master.description(),
                                   master.location(),
                                   master.url(),
                                   master.allDay(),
                                   master.start(),
                                   master.end(),
                                   master.timeZone(),
                                   master.availability(),
                                   recurrence,
                                   null,
                                   List.copyOf(excluded),
                                   List.copyOf(overrides));
  }

  /**
   * The series' rule as agenda holds it, or null for a single event.
   * <p>
   * The structured fields are filled, not only the raw rule: agenda rebuilds
   * the rule from them when it stores the event, so a rule left in the raw
   * field alone would be stored as another one.
   *
   * @param master the master component
   * @param zone the zone the event is anchored on
   * @return the recurrence, null when the component carries no rule
   * @throws IllegalArgumentException when the component carries several rules,
   *           a rule that cannot be read, repeats more often than daily, by
   *           the hour, minute or second, or builds more candidates per step
   *           than a subscribed feed may
   */
  private static EventRecurrence recurrenceOf(VEvent master, ZoneId zone) {
    List<?> rules = master.getProperties(Property.RRULE);
    if (rules.isEmpty()) {
      return null;
    }
    if (rules.size() > 1) {
      throw new IllegalArgumentException("The invitation's event carries several recurrence rules");
    }
    Recur recur = ((RRule) rules.get(0)).getRecur();
    if (recur == null || recur.getFrequency() == null) {
      throw new IllegalArgumentException("The invitation's recurrence rule has no frequency");
    }
    EventRecurrenceFrequency frequency;
    try {
      frequency = EventRecurrenceFrequency.valueOf(recur.getFrequency().name());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The invitation's recurrence frequency is not one agenda holds", e);
    }
    if (!LANDABLE_FREQUENCIES.contains(frequency) || !recur.getHourList().isEmpty() || !recur.getMinuteList().isEmpty()
        || !recur.getSecondList().isEmpty()) {
      throw new IllegalArgumentException("The invitation repeats more often than daily, which is not landed");
    }
    if (CalendarFeedParser.candidatesPerStep(recur) > CalendarFeedParser.MAX_CANDIDATES_PER_STEP) {
      throw new IllegalArgumentException("The invitation's recurrence rule is too wide to be landed");
    }
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setRrule(recur.toString());
    recurrence.setType(EventRecurrenceType.CUSTOM);
    recurrence.setFrequency(frequency);
    // RFC 5545 makes INTERVAL default to 1; ical4j answers -1 when the rule
    // omits it.
    recurrence.setInterval(recur.getInterval() > 0 ? recur.getInterval() : 1);
    recurrence.setCount(recur.getCount() > 0 ? recur.getCount() : 0);
    if (recur.getUntil() != null) {
      // Agenda holds UNTIL as a day: a rule ending at a time of day ends that
      // day, an occurrence too many rather than one missing.
      recurrence.setUntil(recur.getUntil().toInstant().atZone(zone).toLocalDate());
    }
    recurrence.setByDay(tokens(recur.getDayList()));
    recurrence.setByMonthDay(tokens(recur.getMonthDayList()));
    recurrence.setByYearDay(tokens(recur.getYearDayList()));
    recurrence.setByWeekNo(tokens(recur.getWeekNoList()));
    recurrence.setByMonth(tokens(recur.getMonthList()));
    recurrence.setBySetPos(tokens(recur.getSetPosList()));
    return recurrence;
  }

  /**
   * The occurrences a series' EXDATEs exclude, without repetition.
   *
   * @param master the master component
   * @param zone the zone the event is anchored on
   * @return the occurrences, in the event's zone
   * @throws IllegalArgumentException when they are more than
   *           {@link #MAX_EXCLUSIONS}
   */
  private static List<ZonedDateTime> exclusionsOf(VEvent master, ZoneId zone) {
    Set<ZonedDateTime> excluded = new LinkedHashSet<>();
    for (Object property : master.getProperties(Property.EXDATE)) {
      ExDate exDate = (ExDate) property;
      if (exDate.getDates() == null) {
        continue;
      }
      for (Object date : exDate.getDates()) {
        excluded.add(occurrenceOf((Date) date, zone));
        if (excluded.size() > MAX_EXCLUSIONS) {
          throw new IllegalArgumentException("The invitation excludes more than " + MAX_EXCLUSIONS + " occurrences");
        }
      }
    }
    return new ArrayList<>(excluded);
  }

  /**
   * The occurrence a RECURRENCE-ID names.
   *
   * @param recurrenceId the property
   * @param zone the zone the event is anchored on
   * @return the occurrence, in the event's zone
   */
  private static ZonedDateTime occurrenceOf(DateProperty recurrenceId, ZoneId zone) {
    return occurrenceOf(recurrenceId.getDate(), zone);
  }

  /**
   * An occurrence as agenda names it: the instant in the event's zone, or the
   * day at UTC midnight for an all-day series.
   *
   * @param date the date or date-time
   * @param zone the zone the event is anchored on
   * @return the occurrence
   */
  private static ZonedDateTime occurrenceOf(Date date, ZoneId zone) {
    if (!(date instanceof DateTime)) {
      return utcDate(date).atStartOfDay(ZoneOffset.UTC);
    }
    return ZonedDateTime.ofInstant(instantOf(date), zone);
  }

  /**
   * The SEQUENCE of a component.
   *
   * @param event the component
   * @return the SEQUENCE, 0 when absent or negative
   */
  private static int sequenceOf(VEvent event) {
    return event.getSequence() == null ? 0 : Math.max(event.getSequence().getSequenceNo(), 0);
  }

  /**
   * The organiser's bare address, comparable: the {@code mailto:} scheme
   * dropped, lower-cased.
   *
   * @param event the component
   * @return the address, null when the component names none or names
   *         something that is not a mail address
   */
  private static String organizerOf(VEvent event) {
    String value = StringUtils.trimToNull(valueOf(event.getOrganizer()));
    if (value == null) {
      return null;
    }
    if (StringUtils.startsWithIgnoreCase(value, "mailto:")) {
      value = value.substring("mailto:".length()).trim();
    }
    return bareAddress(value);
  }

  /**
   * An address as a landing compares it: trimmed, lower-cased, refused when it
   * does not look like a mail address.
   *
   * @param address the address, may be null
   * @return the bare address, null when there is none
   */
  public static String bareAddress(String address) {
    String value = StringUtils.trimToNull(address);
    if (value == null || value.length() > MAX_ADDRESS || !ADDRESS.matcher(value).matches()) {
      return null;
    }
    return value.toLowerCase(Locale.ROOT);
  }

  /**
   * The zone a timed event is written in: its TZID when Java knows it, UTC
   * otherwise (a UTC time, a floating time, a zone only the object defines —
   * the instants stay right, ical4j having read them through the object's own
   * VTIMEZONE).
   *
   * @param start the DTSTART property
   * @return the zone
   */
  private static ZoneId zoneOf(DateProperty start) {
    TzId tzId = start.getParameter(Parameter.TZID);
    if (tzId == null || StringUtils.isBlank(tzId.getValue())) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(tzId.getValue().trim());
    } catch (RuntimeException e) {
      return ZoneOffset.UTC;
    }
  }

  /**
   * The instant a date-time stands for, a floating one read at UTC.
   *
   * @param date the date-time
   * @return the instant
   */
  private static Instant instantOf(Date date) {
    if (date instanceof DateTime dateTime && !dateTime.isUtc() && dateTime.getTimeZone() == null) {
      // ical4j reads a floating time in the JVM's zone; its local fields are
      // what the sender wrote
      return java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault())
                                    .toInstant(ZoneOffset.UTC);
    }
    return Instant.ofEpochMilli(date.getTime());
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
   * A rule list as the strings agenda holds, ordinal prefixes kept ("-1SU" is
   * the last Sunday).
   *
   * @param values the parsed list, possibly null
   * @return the values, null when there are none
   */
  private static List<String> tokens(List<?> values) {
    if (values == null || values.isEmpty()) {
      return null; // NOSONAR agenda's model reads null as "no such list"
    }
    List<String> tokens = new ArrayList<>(values.size());
    for (Object value : values) {
      tokens.add(String.valueOf(value));
    }
    return tokens;
  }

  /**
   * The value of a property, null when absent.
   *
   * @param property the property, may be null
   * @return its value
   */
  private static String valueOf(Property property) {
    return property == null ? null : property.getValue();
  }
}
