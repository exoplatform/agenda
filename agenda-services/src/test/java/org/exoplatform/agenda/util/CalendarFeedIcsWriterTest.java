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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventOccurrence;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;

/**
 * Parses what the feed writes back with ical4j and checks it says what a
 * subscriber may read, and nothing more: title, time, location, description —
 * no person, no address, no link that acts for somebody; and for a private
 * event, only that the time is taken.
 */
class CalendarFeedIcsWriterTest {

  private static final ZonedDateTime CREATED = ZonedDateTime.of(2026, 9, 11, 10, 0, 0, 0, ZoneOffset.UTC);

  private static final String        HOST    = "tribe.example.org";

  /**
   * A timed event is written in UTC with its title, location and description.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void aTimedEventIsWrittenInUtcWithItsDetails() throws Exception {
    Event event = event(11, "Weekly sync", ZonedDateTime.of(2026, 9, 15, 11, 30, 0, 0, ZoneId.of("Europe/Paris")));
    event.setLocation("Room 4");
    event.setDescription("<p>Agenda <b>items</b></p>");

    VEvent vEvent = single(parse(write(List.of(event))));

    assertEquals("Weekly sync", vEvent.getProperty(Property.SUMMARY).getValue());
    assertEquals("Room 4", vEvent.getProperty(Property.LOCATION).getValue());
    assertEquals("Agenda items", vEvent.getProperty(Property.DESCRIPTION).getValue());
    assertEquals("20260915T093000Z", vEvent.getProperty(Property.DTSTART).getValue(), "11:30 Paris is 09:30 UTC");
    assertEquals("20260915T103000Z", vEvent.getProperty(Property.DTEND).getValue());
    assertEquals("agenda-link-11@" + HOST, vEvent.getProperty(Property.UID).getValue());
    assertEquals("OPAQUE", vEvent.getProperty(Property.TRANSP).getValue());
    assertEquals("20260911T100000Z", vEvent.getProperty(Property.DTSTAMP).getValue(), "stamped with the event's own change");
    assertEquals(1, vEvent.getProperties(Property.DTSTAMP).size(), "and stamped once: a second DTSTAMP is invalid iCalendar");
  }

  /**
   * An all-day event is written as dates, its end exclusive.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void anAllDayEventIsWrittenAsDatesWithAnExclusiveEnd() throws Exception {
    Event event = event(12, "Offsite", ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, ZoneOffset.UTC));
    event.setAllDay(true);
    event.setEnd(ZonedDateTime.of(2026, 9, 21, 23, 59, 59, 0, ZoneOffset.UTC));

    VEvent vEvent = single(parse(write(List.of(event))));

    assertEquals(Value.DATE, vEvent.getProperty(Property.DTSTART).getParameter("VALUE"));
    assertEquals("20260920", vEvent.getProperty(Property.DTSTART).getValue());
    assertEquals("20260922", vEvent.getProperty(Property.DTEND).getValue(), "a two-day event ends the day after");
  }

  /**
   * Occurrences of a recurring event get distinct identifiers that do not
   * depend on the refresh.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void occurrencesGetStableDistinctIdentifiers() throws Exception {
    Event first = occurrence(20, ZonedDateTime.of(2026, 9, 16, 8, 0, 0, 0, ZoneOffset.UTC));
    Event second = occurrence(20, ZonedDateTime.of(2026, 9, 23, 8, 0, 0, 0, ZoneOffset.UTC));

    Calendar calendar = parse(write(List.of(first, second)));
    List<VEvent> events = calendar.getComponents(Component.VEVENT);

    assertEquals("agenda-link-20-20260916T080000Z@" + HOST, events.get(0).getProperty(Property.UID).getValue());
    assertNotEquals(events.get(0).getProperty(Property.UID).getValue(), events.get(1).getProperty(Property.UID).getValue());
    assertNull(events.get(0).getProperty(Property.RRULE), "occurrences are written expanded, never as a rule");
  }

  /**
   * Nothing naming or acting for a person reaches the document: no organiser,
   * no attendee, no address, no answer link, no token.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void nothingNamingOrActingForAPersonIsWritten() throws Exception {
    Event event = event(13, "Board", ZonedDateTime.of(2026, 9, 17, 14, 0, 0, 0, ZoneOffset.UTC));
    event.setDescription("<p>Prepare the slides</p>"
        + "<p>Accept: https://tribe.example.org/portal/rest/v1/agenda/events/13/response/send?response=ACCEPTED&amp;token=abc</p>"
        + "<p>Open https://docs.example.org/file?id=4&amp;token=secret</p>"
        + "<p>Reach john.doe@example.org</p>");

    String document = write(List.of(event));
    VEvent vEvent = single(parse(document));

    assertNull(vEvent.getProperty(Property.ORGANIZER));
    assertNull(vEvent.getProperty(Property.ATTENDEE));
    assertFalse(document.toLowerCase().contains("mailto:"), "no calendar user address");
    assertFalse(document.contains("response/send"), "no answer link");
    assertFalse(document.contains("token="), "no URL carrying a token");
    String description = vEvent.getProperty(Property.DESCRIPTION).getValue();
    assertTrue(description.startsWith("Prepare the slides"), "the organiser's own words stay: " + description);
  }

  /**
   * PO decision: a private event is published as a busy block — "Busy", the
   * same start and end, and no description, location, attendee or URL.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void aPrivateEventIsABusyBlock() throws Exception {
    Event secret = event(30, "Salary review with Paul", ZonedDateTime.of(2026, 9, 18, 15, 0, 0, 0, ZoneOffset.UTC));
    secret.setLocation("HR office 2");
    secret.setDescription("<p>Bring the numbers</p>");
    Event open = event(31, "Team lunch", ZonedDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZoneOffset.UTC));

    String document = CalendarFeedIcsWriter.write("Team", List.of(secret, open), event -> event.getId() == 30, HOST);
    List<VEvent> events = parse(document).getComponents(Component.VEVENT);

    VEvent busy = events.get(0);
    assertEquals("Busy", busy.getProperty(Property.SUMMARY).getValue());
    assertEquals("20260918T150000Z", busy.getProperty(Property.DTSTART).getValue(), "the same start");
    assertEquals("20260918T160000Z", busy.getProperty(Property.DTEND).getValue(), "and the same end");
    assertEquals("OPAQUE", busy.getProperty(Property.TRANSP).getValue(), "and it blocks the time");
    assertEquals("PRIVATE", busy.getProperty(Property.CLASS).getValue());
    assertEquals("19700101T000000Z", busy.getProperty(Property.DTSTAMP).getValue(), "and not when it was last edited");
    assertNull(busy.getProperty(Property.DESCRIPTION));
    assertNull(busy.getProperty(Property.LOCATION));
    assertNull(busy.getProperty(Property.ATTENDEE));
    assertNull(busy.getProperty(Property.ORGANIZER));
    assertNull(busy.getProperty(Property.URL));
    assertFalse(document.contains("Salary") || document.contains("HR office") || document.contains("numbers"),
                "nothing of the private event leaks anywhere in the document");
    assertEquals("Team lunch", events.get(1).getProperty(Property.SUMMARY).getValue(), "a non-private event keeps its title");
  }

  /**
   * The document depends on the events only: written twice, it is the same
   * bytes, whatever the time it is written at.
   *
   * @throws Exception never
   */
  @Test
  void theSameEventsWriteTheSameDocument() throws Exception {
    Event event = event(40, "Standup", ZonedDateTime.of(2026, 9, 19, 9, 0, 0, 0, ZoneOffset.UTC));
    String first = write(List.of(event));
    Thread.sleep(1100);

    assertEquals(first, write(List.of(event)));
  }

  /**
   * The budget is a bound, not an estimate: one event far larger than the rest
   * -- the one distribution where a mean size is wrong -- still gives a
   * document within it, the earliest events kept and the later ones left out.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void oneOversizedEventAmongSmallOnesStillStaysWithinTheBudget() throws Exception {
    List<Event> events = new java.util.ArrayList<>();
    Event large = event(60, "Large", ZonedDateTime.of(2026, 9, 1, 9, 0, 0, 0, ZoneOffset.UTC));
    large.setDescription("<p>" + "y".repeat(CalendarFeedIcsWriter.MAX_DOCUMENT_CHARS - 200 * 1024) + "</p>");
    events.add(large);
    for (int i = 0; i < 1500; i++) {
      Event small = event(1000L + i, "Small " + i, ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, ZoneOffset.UTC).plusHours(i));
      small.setDescription("<p>" + "z".repeat(200) + "</p>");
      events.add(small);
    }

    String document = write(events);

    assertTrue(document.length() <= CalendarFeedIcsWriter.MAX_DOCUMENT_CHARS, "size " + document.length());
    List<VEvent> written = parse(document).getComponents(Component.VEVENT);
    assertEquals("Large", written.get(0).getProperty(Property.SUMMARY).getValue(), "the earliest is kept");
    assertTrue(written.size() > 1 && written.size() < events.size(), "some small events kept, the later ones left out: " + written.size());
  }

  /**
   * A calendar of very long descriptions stops at the character budget instead
   * of serving a document of any size to an anonymous caller.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void theDocumentStopsAtItsCharacterBudget() throws Exception {
    String longText = "<p>" + "x".repeat(CalendarFeedIcsWriter.MAX_DOCUMENT_CHARS / 3) + "</p>";
    List<Event> events = List.of(event(51, "A", ZonedDateTime.of(2026, 9, 20, 9, 0, 0, 0, ZoneOffset.UTC)),
                                 event(52, "B", ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC)),
                                 event(53, "C", ZonedDateTime.of(2026, 9, 22, 9, 0, 0, 0, ZoneOffset.UTC)),
                                 event(54, "D", ZonedDateTime.of(2026, 9, 23, 9, 0, 0, 0, ZoneOffset.UTC)));
    events.forEach(event -> event.setDescription(longText));

    String document = write(events);

    List<VEvent> written = parse(document).getComponents(Component.VEVENT);
    assertEquals(2, written.size(), "the events past the budget are left out, earliest kept");
    assertEquals("A", written.get(0).getProperty(Property.SUMMARY).getValue());
    assertTrue(document.length() <= CalendarFeedIcsWriter.MAX_DOCUMENT_CHARS, "size " + document.length());
  }

  /**
   * The calendar is named, published and asks to be refreshed every few hours;
   * a free event does not block time.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void theCalendarCarriesItsNameAndRefreshInterval() throws Exception {
    Event event = event(14, "Focus", ZonedDateTime.of(2026, 9, 18, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setAvailability(EventAvailability.FREE);

    String document = CalendarFeedIcsWriter.write("My calendar", List.of(event), null, HOST);
    Calendar calendar = parse(document);

    assertEquals("PUBLISH", calendar.getProperty(Property.METHOD).getValue());
    assertEquals("My calendar", calendar.getProperty("X-WR-CALNAME").getValue());
    assertEquals("PT4H", calendar.getProperty("X-PUBLISHED-TTL").getValue());
    assertTrue(document.contains("REFRESH-INTERVAL;VALUE=DURATION:PT4H"), document);
    assertEquals("TRANSPARENT", single(calendar).getProperty(Property.TRANSP).getValue());
    assertTrue(document.contains("\r\n"), "iCalendar lines end with CRLF");
  }

  /**
   * An empty calendar is still a valid document.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void anEmptyCalendarIsAValidDocument() throws Exception {
    Calendar calendar = parse(CalendarFeedIcsWriter.write(null, List.of(), null, HOST));

    assertTrue(calendar.getComponents(Component.VEVENT).isEmpty());
    assertNull(calendar.getProperty("X-WR-CALNAME"));
  }

  /**
   * EXO-90327: what a subscriber's calendar does with the time is read from the
   * event's availability. FREE is transparent; BUSY, DEFAULT, and an event that
   * carries none at all are opaque — DEFAULT keeps meaning busy, which is what
   * every event stored before the control existed carries.
   *
   * @param availability the event's availability, "none" for an unset one
   * @param expected the TRANSP the document must carry
   * @throws Exception when the document does not parse
   */
  @ParameterizedTest
  @CsvSource({ "FREE,TRANSPARENT", "BUSY,OPAQUE", "DEFAULT,OPAQUE", "none,OPAQUE" })
  void transparencyIsReadFromTheAvailability(String availability, String expected) throws Exception {
    Event event = event(60, "Conference week", ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC));
    if (!"none".equals(availability)) {
      event.setAvailability(EventAvailability.valueOf(availability));
    }

    VEvent written = single(parse(write(List.of(event))));

    assertEquals(expected, written.getProperty(Property.TRANSP).getValue());
  }

  /**
   * EXO-90327 meeting EXO-90322: masking an event's content must not silently
   * take its time back. A private event marked free is still a busy block with
   * no title — and still transparent, so a subscriber's calendar does not read
   * the user as booked.
   *
   * @throws Exception when the document does not parse
   */
  @Test
  void aPrivateFreeEventIsMaskedWithoutBlockingTheTime() throws Exception {
    Event event = event(61, "Conference week", ZonedDateTime.of(2026, 9, 22, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setAvailability(EventAvailability.FREE);

    VEvent busy = single(parse(CalendarFeedIcsWriter.write("Team", List.of(event), e -> true, HOST)));

    assertEquals("Busy", busy.getProperty(Property.SUMMARY).getValue(), "its content is still masked");
    assertEquals("PRIVATE", busy.getProperty(Property.CLASS).getValue());
    assertEquals("TRANSPARENT", busy.getProperty(Property.TRANSP).getValue(), "and it does not block the time");
  }

  /**
   * Writes events with no private one.
   *
   * @param events the events
   * @return the document
   */
  private String write(List<Event> events) {
    return CalendarFeedIcsWriter.write("Team", events, event -> false, HOST);
  }

  /**
   * Parses a document.
   *
   * @param document the iCalendar text
   * @return the parsed calendar
   * @throws Exception when it does not parse
   */
  private Calendar parse(String document) throws Exception {
    return new CalendarBuilder().build(new StringReader(document));
  }

  /**
   * The only event of a calendar.
   *
   * @param calendar the calendar
   * @return its event
   */
  private VEvent single(Calendar calendar) {
    List<VEvent> events = calendar.getComponents(Component.VEVENT);
    assertEquals(1, events.size());
    return events.get(0);
  }

  /**
   * A one-hour event.
   *
   * @param id identifier
   * @param summary title
   * @param start start
   * @return the event
   */
  private Event event(long id, String summary, ZonedDateTime start) {
    Event event = new Event();
    event.setId(id);
    event.setSummary(summary);
    event.setStart(start);
    event.setEnd(start.plusHours(1));
    event.setCreated(CREATED);
    return event;
  }

  /**
   * An occurrence of a recurring event, as the event service expands it.
   *
   * @param parentId the recurring event
   * @param start the occurrence start
   * @return the occurrence
   */
  private Event occurrence(long parentId, ZonedDateTime start) {
    Event event = event(0, "Standup", start);
    event.setParentId(parentId);
    event.setOccurrence(new EventOccurrence(start));
    return event;
  }

}
