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
import java.io.StringWriter;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.model.Event;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RefreshInterval;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.validate.ValidationException;

/**
 * Writes the iCalendar document a calendar link serves.
 * <p>
 * <b>What it says about an event: its title, its time, its location and its
 * description — nothing else.</b> The document is read by whoever holds the
 * URL, with no eXo account behind them, so nothing that names or acts for a
 * person goes in: no {@code ORGANIZER}, no {@code ATTENDEE}, no e-mail address,
 * no answer link, no conference or event address. The mail channel
 * ({@link Utils#generateIcsFile}) and the CalDAV copy say more because each is
 * written for one known recipient; this one is written for nobody in
 * particular. <b>A private event says less still</b>: a block of busy time
 * titled "Busy", with its start and end and nothing else.
 * <p>
 * <b>Recurrences arrive already expanded</b>, one {@link Event} per occurrence
 * inside the window, exceptions applied — that is what
 * {@code AgendaEventService.getEvents} answers. Each occurrence is written as a
 * VEVENT of its own, with an identifier derived from its parent and its
 * original start, so it keeps the same {@code UID} across refreshes. Rebuilding
 * an {@code RRULE} with its {@code EXDATE}s and {@code RECURRENCE-ID}s would be
 * a second recurrence engine beside agenda's own; the window bounds the
 * expansion.
 * <p>
 * <b>The same data writes the same bytes.</b> Nothing in the document depends
 * on the moment it is written — {@code DTSTAMP} is the event's own last change,
 * <b>except for a busy block, whose stamp is the constant {@code NO_STAMP}</b> so
 * that it does not tell when the private event was edited — so the entity tag
 * computed over it lets a client that refreshes an unchanged calendar get a
 * 304.
 * <p>
 * <b>Times are written in UTC</b> and all-day events as dates, so no
 * {@code VTIMEZONE} is needed and every client places an event at the same
 * instant.
 */
public final class CalendarFeedIcsWriter {

  /**
   * How often a client is asked to refresh. Both the RFC 7986 property and the
   * older {@code X-PUBLISHED-TTL} that Outlook reads carry it.
   */
  public static final Duration  REFRESH_INTERVAL = Duration.ofHours(4);

  /**
   * Most characters of event data one document carries. A safety stop against
   * a calendar of very long descriptions served to an anonymous caller; events
   * past it are left out, earliest kept.
   */
  public static final int       MAX_DOCUMENT_CHARS = 4 * 1024 * 1024;

  /** The title a private event is published under. */
  public static final String    BUSY_SUMMARY     = "Busy";

  private static final String   PRODUCT_ID       = "-//eXo Platform//Agenda calendar link//EN";

  /**
   * A line of description carrying a link that acts for somebody: an
   * invitation answer link (agenda's own {@code /response/send}, whatever the
   * context path it is served under) or any URL passing a {@code token}
   * parameter. {@link InvitationText#stripFrom} removes the block eXo itself
   * writes; this catches such a link wherever else it sits.
   */
  private static final Pattern  ACTING_LINK_LINE = Pattern.compile("/response/send\\b|[?&]token=",
                                                                   Pattern.CASE_INSENSITIVE);

  private static final Pattern  LINE_BREAK       = Pattern.compile("\\R");

  /**
   * The stamp of a busy block, whatever dates its private event carries, and of
   * an event that carries no date of its own at all.
   */
  private static final ZonedDateTime NO_STAMP     = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private static final DateTimeFormatter OCCURRENCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                                                                             .withZone(ZoneOffset.UTC);

  /**
   * Not instantiable: this class holds no state.
   */
  private CalendarFeedIcsWriter() {
    // Utility class
  }

  /**
   * Writes the document.
   *
   * @param calendarName name the subscribing application shows for the
   *          calendar, blank for none
   * @param events the events to publish, occurrences expanded, earliest first
   * @param isPrivate which events are published as busy time only
   * @param uidHost host name used to qualify event identifiers
   * @return the iCalendar document, CRLF line endings, folded
   */
  public static String write(String calendarName, List<Event> events, Predicate<Event> isPrivate, String uidHost) {
    net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
    PropertyList<net.fortuna.ical4j.model.Property> properties = calendar.getProperties();
    properties.add(new ProdId(PRODUCT_ID));
    properties.add(Version.VERSION_2_0);
    properties.add(CalScale.GREGORIAN);
    properties.add(Method.PUBLISH);
    if (StringUtils.isNotBlank(calendarName)) {
      properties.add(new XProperty("X-WR-CALNAME", calendarName));
    }
    ParameterList durationParameter = new ParameterList();
    durationParameter.add(Value.DURATION);
    properties.add(new RefreshInterval(durationParameter, REFRESH_INTERVAL));
    properties.add(new XProperty("X-PUBLISHED-TTL", REFRESH_INTERVAL.toString()));
    String host = StringUtils.isBlank(uidHost) ? "exo" : uidHost;
    Predicate<Event> privateEvent = isPrivate == null ? event -> false : isPrivate;
    long written = 0;
    if (events != null) {
      for (Event event : events) {
        if (event == null || event.getStart() == null) {
          continue;
        }
        VEvent vEvent = privateEvent.test(event) ? toBusyBlock(event, host) : toVEvent(event, host);
        written += vEvent.toString().length();
        if (written > MAX_DOCUMENT_CHARS) {
          break;
        }
        calendar.getComponents().add(vEvent);
      }
    }
    StringWriter writer = new StringWriter();
    try {
      new CalendarOutputter(false).output(calendar, writer);
    } catch (IOException | ValidationException e) {
      throw new IllegalStateException("The calendar link document could not be written", e);
    }
    return writer.toString();
  }

  /**
   * Writes one event, or one occurrence of a recurring event.
   *
   * @param event the event
   * @param host host qualifying the identifier
   * @return the component
   */
  private static VEvent toVEvent(Event event, String host) {
    // Not initialised: the no-argument constructor stamps the component with the
    // current time, which would add a second DTSTAMP and change the document on
    // every fetch.
    VEvent vEvent = new VEvent(false);
    PropertyList<net.fortuna.ical4j.model.Property> properties = vEvent.getProperties();
    ZonedDateTime lastChange = lastChange(event);
    addIdentityAndTime(properties, event, host, lastChange == null ? NO_STAMP : lastChange);
    properties.add(new Summary(StringUtils.defaultString(event.getSummary())));
    if (StringUtils.isNotBlank(event.getLocation())) {
      properties.add(new Location(event.getLocation()));
    }
    String description = description(event.getDescription());
    if (StringUtils.isNotBlank(description)) {
      properties.add(new Description(description));
    }
    properties.add(event.getAvailability() == EventAvailability.FREE ? Transp.TRANSPARENT : Transp.OPAQUE);
    ZonedDateTime modified = lastChange(event);
    if (modified != null) {
      properties.add(new LastModified(utc(modified)));
    }
    return vEvent;
  }

  /**
   * Writes a private event as busy time: its identifier, its start and end, and
   * the title "Busy" — no description, location, attendee or address, and not
   * even its last change: its {@code DTSTAMP} is a constant, so the document does
   * not tell when the private event was edited.
   * <p>
   * Its {@code TRANSP} is read from the event's availability, the same way
   * {@link #toVEvent} reads it (EXO-90327). Masking answers "may a subscriber
   * see what this is"; availability answers "does it take the user's time", and
   * hiding the first must not silently change the second — an event the user
   * marked free stays free once it is also marked private. Until availability
   * became settable there was no event this could tell apart, which is why the
   * constant {@code OPAQUE} it replaces was right before and is not now.
   *
   * @param event the private event
   * @param host host qualifying the identifier
   * @return the component
   */
  private static VEvent toBusyBlock(Event event, String host) {
    // Not initialised: the no-argument constructor stamps the component with the
    // current time, which would add a second DTSTAMP and change the document on
    // every fetch.
    VEvent vEvent = new VEvent(false);
    PropertyList<net.fortuna.ical4j.model.Property> properties = vEvent.getProperties();
    addIdentityAndTime(properties, event, host, NO_STAMP);
    properties.add(new Summary(BUSY_SUMMARY));
    properties.add(Clazz.PRIVATE);
    properties.add(event.getAvailability() == EventAvailability.FREE ? Transp.TRANSPARENT : Transp.OPAQUE);
    return vEvent;
  }

  /**
   * Adds what every VEVENT carries: its identifier, its stamp and its time.
   *
   * @param properties the component's properties
   * @param event the event
   * @param host host qualifying the identifier
   * @param stamp the value of its {@code DTSTAMP}
   */
  private static void addIdentityAndTime(PropertyList<net.fortuna.ical4j.model.Property> properties,
                                         Event event,
                                         String host,
                                         ZonedDateTime stamp) {
    properties.add(new Uid(uid(event, host)));
    properties.add(new DtStamp(utc(stamp)));
    if (event.isAllDay()) {
      LocalDate startDay = event.getStart().toLocalDate();
      LocalDate endDay = event.getEnd() == null ? startDay : event.getEnd().toLocalDate();
      // All-day end dates are exclusive in iCalendar; agenda stores the last
      // second of the last day.
      properties.add(new DtStart(date(startDay)));
      properties.add(new DtEnd(date(endDay.plusDays(1))));
    } else {
      properties.add(new DtStart(utc(event.getStart())));
      if (event.getEnd() != null) {
        properties.add(new DtEnd(utc(event.getEnd())));
      }
    }
  }

  /**
   * The organiser's own words as plain text: markup rendered out, the
   * invitation block eXo writes into imported copies taken off, and any
   * remaining line that carries a link acting for somebody dropped.
   *
   * @param html the description as the editor stored it, may be blank
   * @return the text to publish, possibly empty
   */
  static String description(String html) {
    String text = InvitationText.stripFrom(EventIcsBuilder.htmlToPlainText(html));
    if (StringUtils.isBlank(text)) {
      return "";
    }
    return Arrays.stream(LINE_BREAK.split(text))
                 .filter(line -> !ACTING_LINK_LINE.matcher(line).find())
                 .collect(Collectors.joining("\n"))
                 .trim();
  }

  /**
   * The identifier of an event in the document, stable across refreshes.
   *
   * @param event the event or occurrence
   * @param host host qualifying the identifier
   * @return the UID
   */
  static String uid(Event event, String host) {
    if (event.getParentId() > 0 && event.getOccurrence() != null && event.getOccurrence().getId() != null) {
      return "agenda-link-" + event.getParentId() + "-" + OCCURRENCE_FORMAT.format(event.getOccurrence().getId()) + "@"
          + host;
    }
    return "agenda-link-" + event.getId() + "@" + host;
  }

  /**
   * The last change of an event: its update, else its creation.
   *
   * @param event the event
   * @return the instant, null when the event carries neither
   */
  private static ZonedDateTime lastChange(Event event) {
    return event.getUpdated() == null ? event.getCreated() : event.getUpdated();
  }

  /**
   * An instant as a UTC date-time value.
   *
   * @param dateTime the instant
   * @return the value
   */
  private static DateTime utc(ZonedDateTime dateTime) {
    DateTime value = new DateTime(dateTime.toInstant().toEpochMilli());
    value.setUtc(true);
    return value;
  }

  /**
   * A day as a DATE value.
   *
   * @param day the day
   * @return the value
   */
  private static Date date(LocalDate day) {
    try {
      return new Date(day.format(DateTimeFormatter.BASIC_ISO_DATE));
    } catch (ParseException e) {
      throw new IllegalStateException("A calendar day could not be written", e);
    }
  }

}
