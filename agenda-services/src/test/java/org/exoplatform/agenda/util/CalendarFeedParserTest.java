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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.util.CalendarFeedParser.ImportedEvent;
import org.exoplatform.agenda.util.CalendarFeedParser.ParsedCalendar;

/**
 * Pins how a subscribed feed is read (EXO-90278), on documents written the way
 * Google, Outlook and eXo's own Publish write them: time zones, all-day events,
 * recurrences with exceptions and overrides, the window, the limits, the text
 * kept and the identities that let a refresh find an occurrence again.
 */
class CalendarFeedParserTest {

  private static final Instant FROM = Instant.parse("2026-02-01T00:00:00Z");

  private static final Instant TO   = Instant.parse("2027-02-01T00:00:00Z");

  /**
   * A document around its components.
   *
   * @param lines the lines inside VCALENDAR
   * @return the bytes
   */
  private static byte[] calendar(String... lines) {
    return ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n" + String.join("\r\n", lines) + "\r\nEND:VCALENDAR\r\n")
                                                                                                                                   .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Reads a document in the test window.
   *
   * @param body the document
   * @return what was read
   * @throws CalendarFeedException when refused
   */
  private static ParsedCalendar parse(byte[] body) throws CalendarFeedException {
    return CalendarFeedParser.parse(body, FROM, TO, 2000);
  }

  /**
   * A weekly series in Europe/Paris keeps its local time across the change to
   * summer time, loses its excluded instance, and gives the moved instance its
   * override's times under the identity of the instance it replaces.
   *
   * @throws Exception when refused
   */
  @Test
  void aSeriesWithAnExclusionAndAnOverrideIsExpandedInItsTimeZone() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:weekly@test",
                                           "DTSTART;TZID=Europe/Paris:20260302T100000",
                                           "DTEND;TZID=Europe/Paris:20260302T110000",
                                           "RRULE:FREQ=WEEKLY;COUNT=6",
                                           "EXDATE;TZID=Europe/Paris:20260316T100000",
                                           "SUMMARY:Weekly",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:weekly@test",
                                           "RECURRENCE-ID;TZID=Europe/Paris:20260323T100000",
                                           "DTSTART;TZID=Europe/Paris:20260324T140000",
                                           "DTEND;TZID=Europe/Paris:20260324T150000",
                                           "SUMMARY:Moved",
                                           "END:VEVENT"));

    List<ImportedEvent> events = parsed.events();
    assertEquals(List.of(Instant.parse("2026-03-02T09:00:00Z"),
                         Instant.parse("2026-03-09T09:00:00Z"),
                         Instant.parse("2026-03-24T13:00:00Z"),
                         Instant.parse("2026-03-30T08:00:00Z"),
                         Instant.parse("2026-04-06T08:00:00Z")),
                 events.stream().map(event -> event.start().toInstant()).toList());
    assertEquals(ZoneId.of("Europe/Paris"), events.get(0).timeZone());
    ImportedEvent moved = events.get(2);
    assertEquals("Moved", moved.summary());
    assertEquals("weekly@test|" + Instant.parse("2026-03-23T09:00:00Z").toEpochMilli(), moved.key(),
                 "the override keeps the identity of the instance it replaces");
    assertEquals(5, events.stream().map(ImportedEvent::key).distinct().count(), "every occurrence has its own identity");
  }

  /**
   * An instance cancelled by an override is left out.
   *
   * @throws Exception when refused
   */
  @Test
  void aCancelledInstanceIsLeftOut() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:daily@test",
                                           "DTSTART:20260601T080000Z",
                                           "DTEND:20260601T083000Z",
                                           "RRULE:FREQ=DAILY;COUNT=3",
                                           "SUMMARY:Standup",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:daily@test",
                                           "RECURRENCE-ID:20260602T080000Z",
                                           "DTSTART:20260602T080000Z",
                                           "DTEND:20260602T083000Z",
                                           "STATUS:CANCELLED",
                                           "SUMMARY:Standup",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:gone@test",
                                           "DTSTART:20260605T080000Z",
                                           "DTEND:20260605T090000Z",
                                           "STATUS:CANCELLED",
                                           "END:VEVENT"));

    assertEquals(List.of(Instant.parse("2026-06-01T08:00:00Z"), Instant.parse("2026-06-03T08:00:00Z")),
                 parsed.events().stream().map(event -> event.start().toInstant()).toList());
  }

  /**
   * An Outlook time zone Java does not know is read through the VTIMEZONE the
   * document carries: the instants are right, and the event is expressed in UTC.
   *
   * @throws Exception when refused
   */
  @Test
  void aWindowsTimeZoneIsReadThroughItsVtimezone() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VTIMEZONE",
                                           "TZID:W. Europe Standard Time",
                                           "BEGIN:STANDARD",
                                           "DTSTART:16010101T030000",
                                           "TZOFFSETFROM:+0200",
                                           "TZOFFSETTO:+0100",
                                           "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10",
                                           "END:STANDARD",
                                           "BEGIN:DAYLIGHT",
                                           "DTSTART:16010101T020000",
                                           "TZOFFSETFROM:+0100",
                                           "TZOFFSETTO:+0200",
                                           "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3",
                                           "END:DAYLIGHT",
                                           "END:VTIMEZONE",
                                           "BEGIN:VEVENT",
                                           "UID:outlook@test",
                                           "DTSTART;TZID=W. Europe Standard Time:20260710T090000",
                                           "DTEND;TZID=W. Europe Standard Time:20260710T100000",
                                           "SUMMARY:Outlook",
                                           "END:VEVENT"));

    ImportedEvent event = parsed.events().get(0);
    assertEquals(Instant.parse("2026-07-10T07:00:00Z"), event.start().toInstant());
    assertEquals(Instant.parse("2026-07-10T08:00:00Z"), event.end().toInstant());
    assertEquals(ZoneOffset.UTC, event.timeZone());
  }

  /**
   * All-day events keep their days: a one-day holiday, a three-day one with an
   * exclusive DTEND, and a yearly one.
   *
   * @throws Exception when refused
   */
  @Test
  void allDayEventsKeepTheirDays() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:may@test",
                                           "DTSTART;VALUE=DATE:20260501",
                                           "DTEND;VALUE=DATE:20260502",
                                           "RRULE:FREQ=YEARLY",
                                           "SUMMARY:May day",
                                           "TRANSP:TRANSPARENT",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:long@test",
                                           "DTSTART;VALUE=DATE:20260810",
                                           "DTEND;VALUE=DATE:20260813",
                                           "SUMMARY:Three days",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:noend@test",
                                           "DTSTART;VALUE=DATE:20260901",
                                           "SUMMARY:No end",
                                           "END:VEVENT"));

    List<ImportedEvent> events = parsed.events();
    assertEquals(3, events.size(), "May 2026, August, September: May 2027 falls after the window");
    ImportedEvent may = events.get(0);
    assertTrue(may.allDay());
    assertEquals(LocalDate.of(2026, 5, 1), may.start().toLocalDate());
    assertEquals(LocalDate.of(2026, 5, 1), may.end().toLocalDate(), "a one-day event ends on its own day");
    assertEquals(EventAvailability.FREE, may.availability(), "a transparent event is free time");
    assertEquals("may@test|2026-05-01", may.key());
    ImportedEvent three = events.get(1);
    assertEquals(LocalDate.of(2026, 8, 10), three.start().toLocalDate());
    assertEquals(LocalDate.of(2026, 8, 12), three.end().toLocalDate(), "DTEND of an all-day event is exclusive");
    assertEquals(EventAvailability.BUSY, three.availability());
    assertEquals(LocalDate.of(2026, 9, 1), events.get(2).end().toLocalDate(), "an all-day event without DTEND lasts its day");
    assertEquals(2, CalendarFeedParser.parse(calendar("BEGIN:VEVENT",
                                                      "UID:may@test",
                                                      "DTSTART;VALUE=DATE:20260501",
                                                      "DTEND;VALUE=DATE:20260502",
                                                      "RRULE:FREQ=YEARLY",
                                                      "END:VEVENT"),
                                             FROM,
                                             Instant.parse("2027-06-01T00:00:00Z"),
                                             2000)
                                      .events()
                                      .size(),
                 "a yearly all-day event gives one day per year of the window");
  }

  /**
   * A floating time is read in the calendar's X-WR-TIMEZONE, whatever the JVM's
   * zone.
   *
   * @throws Exception when refused
   */
  @Test
  void aFloatingTimeIsReadInTheCalendarTimeZone() throws Exception {
    ParsedCalendar parsed = parse(calendar("X-WR-TIMEZONE:America/New_York",
                                           "BEGIN:VEVENT",
                                           "UID:floating@test",
                                           "DTSTART:20260401T090000",
                                           "DTEND:20260401T100000",
                                           "SUMMARY:Floating",
                                           "END:VEVENT"));

    assertEquals(Instant.parse("2026-04-01T13:00:00Z"), parsed.events().get(0).start().toInstant());
    assertEquals(ZoneId.of("America/New_York"), parsed.events().get(0).timeZone());
  }

  /**
   * Occurrences outside the window are left out; one spanning its start is kept.
   *
   * @throws Exception when refused
   */
  @Test
  void theWindowBoundsTheOccurrences() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:before@test",
                                           "DTSTART:20260110T090000Z",
                                           "DTEND:20260110T100000Z",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:spanning@test",
                                           "DTSTART:20260131T230000Z",
                                           "DTEND:20260201T010000Z",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:after@test",
                                           "DTSTART:20270301T090000Z",
                                           "DTEND:20270301T100000Z",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:forever@test",
                                           "DTSTART:20250101T090000Z",
                                           "DTEND:20250101T100000Z",
                                           "RRULE:FREQ=MONTHLY",
                                           "END:VEVENT"));

    Set<String> uids = parsed.events().stream().map(event -> event.key().split("\\|")[0]).collect(Collectors.toSet());
    assertEquals(Set.of("spanning@test", "forever@test"), uids);
    assertEquals(12, parsed.events().stream().filter(event -> event.key().startsWith("forever@test")).count(),
                 "a series started long ago gives the instances inside the window only");
  }

  /**
   * Only the earliest occurrences are kept past the limit, and the document says
   * it was cut.
   *
   * @throws Exception when refused
   */
  @Test
  void theEarliestOccurrencesAreKeptPastTheLimit() throws Exception {
    ParsedCalendar parsed = CalendarFeedParser.parse(calendar("BEGIN:VEVENT",
                                                              "UID:daily@test",
                                                              "DTSTART:20260201T090000Z",
                                                              "DTEND:20260201T093000Z",
                                                              "RRULE:FREQ=DAILY",
                                                              "END:VEVENT"),
                                                     FROM,
                                                     TO,
                                                     10);

    assertTrue(parsed.truncated());
    assertEquals(10, parsed.events().size());
    assertEquals(Instant.parse("2026-02-01T09:00:00Z"), parsed.events().get(0).start().toInstant());
    assertEquals(Instant.parse("2026-02-10T09:00:00Z"), parsed.events().get(9).start().toInstant());
    assertFalse(parse(calendar("BEGIN:VEVENT", "UID:one@test", "DTSTART:20260301T090000Z", "END:VEVENT")).truncated());
  }

  /**
   * A rule firing every second or minute is not expanded.
   *
   * @throws Exception when refused
   */
  @Test
  void aRuleFiringEverySecondIsNotExpanded() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:bomb@test",
                                           "DTSTART:20260301T090000Z",
                                           "RRULE:FREQ=SECONDLY",
                                           "END:VEVENT",
                                           "BEGIN:VEVENT",
                                           "UID:minutes@test",
                                           "DTSTART:20260301T090000Z",
                                           "RRULE:FREQ=MINUTELY",
                                           "END:VEVENT"));

    assertTrue(parsed.events().isEmpty());
  }

  /**
   * Markup in a description is read as plain text, kept with its line breaks,
   * and escaped again when it becomes agenda's description.
   *
   * @throws Exception when refused
   */
  @Test
  void aDescriptionIsPlainTextEscapedForDisplay() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:html@test",
                                           "DTSTART:20260301T090000Z",
                                           "SUMMARY:<b>Title</b>",
                                           "DESCRIPTION:<p>Hello <script>alert(1)</script>world</p><br>Tom &amp; Jerry &#60;3",
                                           "LOCATION:Room 1",
                                           "END:VEVENT"));

    ImportedEvent event = parsed.events().get(0);
    assertEquals("Title", event.summary());
    assertEquals("Hello alert(1)world\n\nTom & Jerry <3", event.description());
    assertEquals("Room 1", event.location());
    assertEquals("Hello alert(1)world<br><br>Tom &amp; Jerry &lt;3", CalendarFeedParser.descriptionMarkup(event.description()));
    assertEquals("a &lt;script&gt; &amp; &quot;q&quot;", CalendarFeedParser.descriptionMarkup("a <script> & \"q\""));
  }

  /**
   * Nothing about people is imported: an occurrence carries no organizer, no
   * attendee and no alarm, whatever the document holds.
   *
   * @throws Exception when refused
   */
  @Test
  void noParticipantIsImported() throws Exception {
    ParsedCalendar parsed = parse(calendar("BEGIN:VEVENT",
                                           "UID:meeting@test",
                                           "DTSTART:20260301T090000Z",
                                           "DTEND:20260301T100000Z",
                                           "SUMMARY:Meeting",
                                           "ORGANIZER;CN=Boss:mailto:boss@example.org",
                                           "ATTENDEE;CN=Alice;PARTSTAT=NEEDS-ACTION:mailto:alice@example.org",
                                           "BEGIN:VALARM",
                                           "ACTION:DISPLAY",
                                           "TRIGGER:-PT15M",
                                           "END:VALARM",
                                           "END:VEVENT"));

    assertEquals(1, parsed.events().size());
    Set<String> fields = Arrays.stream(ImportedEvent.class.getRecordComponents()).map(component -> component.getName()).collect(Collectors.toSet());
    assertEquals(Set.of("key", "summary", "description", "location", "allDay", "start", "end", "timeZone", "availability"), fields,
                 "an occurrence holds no field a participant, an organizer or an alarm could travel in");
    assertFalse(parsed.events().get(0).description() != null && parsed.events().get(0).description().contains("alice"));
  }

  /**
   * The same document read twice gives the same identities and digests, and a
   * changed title changes only that occurrence's digest.
   *
   * @throws Exception when refused
   */
  @Test
  void identitiesAndDigestsAreStable() throws Exception {
    byte[] first = calendar("BEGIN:VEVENT", "UID:a@test", "DTSTART:20260301T090000Z", "SUMMARY:A", "END:VEVENT",
                            "BEGIN:VEVENT", "UID:b@test", "DTSTART:20260302T090000Z", "SUMMARY:B", "END:VEVENT");
    byte[] changed = calendar("BEGIN:VEVENT", "UID:a@test", "DTSTART:20260301T090000Z", "SUMMARY:A2", "END:VEVENT",
                              "BEGIN:VEVENT", "UID:b@test", "DTSTART:20260302T090000Z", "SUMMARY:B", "END:VEVENT");

    List<ImportedEvent> once = parse(first).events();
    List<ImportedEvent> twice = parse(first).events();
    List<ImportedEvent> after = parse(changed).events();

    assertEquals(once.stream().map(ImportedEvent::key).toList(), twice.stream().map(ImportedEvent::key).toList());
    assertEquals(once.stream().map(ImportedEvent::contentHash).toList(), twice.stream().map(ImportedEvent::contentHash).toList());
    assertEquals(once.get(0).key(), after.get(0).key());
    assertFalse(once.get(0).contentHash().equals(after.get(0).contentHash()));
    assertEquals(once.get(1).contentHash(), after.get(1).contentHash());
  }

  /**
   * The calendar's name and its refresh interval are read.
   *
   * @throws Exception when refused
   */
  @Test
  void theNameAndTheRefreshIntervalAreRead() throws Exception {
    ParsedCalendar weekly = parse(calendar("X-WR-CALNAME:Public holidays", "REFRESH-INTERVAL;VALUE=DURATION:P1W"));
    assertEquals("Public holidays", weekly.name());
    assertEquals(Duration.ofDays(7), weekly.refreshInterval());
    assertEquals(Duration.ofHours(2), parse(calendar("X-PUBLISHED-TTL:PT2H")).refreshInterval());
    ParsedCalendar none = parse(calendar("PRODID:-//none//EN"));
    assertNull(none.name());
    assertNull(none.refreshInterval());
  }

  /**
   * What is not a calendar is refused as such, a byte-order mark and blank lines
   * ahead of one are tolerated, and a document that cannot be read is refused as
   * malformed.
   *
   * @throws Exception when refused
   */
  @Test
  void whatIsNotACalendarIsRefused() throws Exception {
    assertEquals(CalendarFeedException.NOT_A_CALENDAR,
                 assertThrows(CalendarFeedException.class,
                              () -> parse("<html><body>Sign in</body></html>".getBytes(StandardCharsets.UTF_8))).getReason());
    assertEquals(CalendarFeedException.NOT_A_CALENDAR,
                 assertThrows(CalendarFeedException.class, () -> parse(new byte[0])).getReason());
    assertEquals(CalendarFeedException.MALFORMED_CALENDAR,
                 assertThrows(CalendarFeedException.class,
                              () -> parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:2026".getBytes(StandardCharsets.UTF_8))).getReason());
    byte[] withBom = ("﻿\r\n" + new String(calendar("X-WR-CALNAME:Bom"), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    assertEquals("Bom", parse(withBom).name());
  }

}
