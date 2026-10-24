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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.model.MailInvitationEvent;

/**
 * Reads the sender's iCalendar object the way a landing needs it, and refuses
 * what it must not land (EXO-90866).
 */
class MailInvitationReaderTest {

  private static final String UID = "abc-123@partner.example";

  /**
   * The master's text, times, organiser, SEQUENCE and transparency are read;
   * markup in the text is reduced to plain text and the organiser to a bare,
   * lower-cased address.
   */
  @Test
  void readsTheMaster() {
    MailInvitationEvent read = MailInvitationReader.read(ics("METHOD:REQUEST\r\n",
                                                             "SEQUENCE:4\r\nTRANSP:TRANSPARENT\r\n"
                                                                 + "ORGANIZER;CN=Boss:MAILTO:Boss@Partner.example\r\n"
                                                                 + "DESCRIPTION:<b>Agenda</b><br>bring the deck\r\n"
                                                                 + "LOCATION:Room 1\r\n"),
                                                         UID);

    assertEquals("REQUEST", read.method());
    assertEquals(4, read.sequence());
    assertEquals("boss@partner.example", read.organizer());
    assertEquals("Agenda\nbring the deck", read.description());
    assertEquals("Room 1", read.location());
    assertEquals(EventAvailability.FREE, read.availability());
    assertFalse(read.allDay());
    assertEquals(ZonedDateTime.of(2030, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC).toInstant(), read.start().toInstant());
    assertEquals(ZonedDateTime.of(2030, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant(), read.end().toInstant());
    assertNull(read.recurrence());
  }

  /**
   * An event in a named zone keeps that zone; an all-day event spans its days,
   * the last one inclusive, as agenda holds it.
   */
  @Test
  void readsZonesAndAllDayEvents() {
    String zoned = ics("", "").replace("DTSTART:20301015T090000Z", "DTSTART;TZID=Europe/Paris:20301015T090000")
                              .replace("DTEND:20301015T100000Z", "DTEND;TZID=Europe/Paris:20301015T100000");
    MailInvitationEvent read = MailInvitationReader.read(zoned, UID);
    assertEquals(ZoneId.of("Europe/Paris"), read.timeZone());
    assertEquals(9, read.start().getHour());

    String allDay = ics("", "").replace("DTSTART:20301015T090000Z", "DTSTART;VALUE=DATE:20301015")
                               .replace("DTEND:20301015T100000Z", "DTEND;VALUE=DATE:20301017");
    MailInvitationEvent day = MailInvitationReader.read(allDay, UID);
    assertTrue(day.allDay());
    assertEquals(LocalDate.of(2030, 10, 15), day.start().toLocalDate());
    assertEquals(LocalDate.of(2030, 10, 16), day.end().toLocalDate());
  }

  /**
   * A series' rule fills agenda's structured fields — INTERVAL defaulting to
   * 1, the ordinal of BYDAY kept — and its EXDATEs and overrides are read as
   * the occurrences they name.
   */
  @Test
  void readsASeries() {
    String object = ics("", "RRULE:FREQ=MONTHLY;BYDAY=-1FR;UNTIL=20311231T000000Z\r\nEXDATE:20301122T090000Z\r\n")
        .replace("END:VCALENDAR", "BEGIN:VEVENT\r\nUID:" + UID + "\r\nRECURRENCE-ID:20301025T090000Z\r\n"
            + "DTSTAMP:20300101T000000Z\r\nDTSTART:20301025T130000Z\r\nDTEND:20301025T140000Z\r\nSUMMARY:Moved\r\n"
            + "END:VEVENT\r\nEND:VCALENDAR");
    MailInvitationEvent read = MailInvitationReader.read(object, UID);

    assertEquals(EventRecurrenceFrequency.MONTHLY, read.recurrence().getFrequency());
    assertEquals(1, read.recurrence().getInterval());
    assertEquals(List.of("-1FR"), read.recurrence().getByDay());
    assertEquals(LocalDate.of(2031, 12, 31), read.recurrence().getUntil());
    assertEquals(List.of(ZonedDateTime.of(2030, 11, 22, 9, 0, 0, 0, ZoneOffset.UTC).toInstant()),
                 read.excludedOccurrences().stream().map(ZonedDateTime::toInstant).toList());
    assertEquals(1, read.overrides().size());
    assertEquals("Moved", read.overrides().get(0).summary());
    assertEquals(ZonedDateTime.of(2030, 10, 25, 9, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                 read.overrides().get(0).occurrenceId().toInstant());
  }

  /**
   * What a landing must not read: another event than the one shown, several
   * events, one occurrence alone, an object that is not iCalendar or too long,
   * a series repeating by the hour or too wide to expand.
   */
  @Test
  void refusesWhatItMustNotLand() {
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(ics("", ""), "another@partner.example"));
    String twoEvents = ics("", "").replace("END:VCALENDAR",
                                           "BEGIN:VEVENT\r\nUID:other@partner.example\r\nDTSTAMP:20300101T000000Z\r\n"
                                               + "DTSTART:20301015T090000Z\r\nEND:VEVENT\r\nEND:VCALENDAR");
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(twoEvents, UID));
    String occurrenceOnly = ics("", "RECURRENCE-ID:20301015T090000Z\r\n");
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(occurrenceOnly, UID));
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read("hello", UID));
    assertThrows(IllegalArgumentException.class,
                 () -> MailInvitationReader.read(ics("", "X-PAD:" + "x".repeat(MailInvitationReader.MAX_CHARS) + "\r\n"), UID));
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(ics("", "RRULE:FREQ=HOURLY\r\n"), UID));
    assertThrows(IllegalArgumentException.class,
                 () -> MailInvitationReader.read(ics("", "RRULE:FREQ=DAILY;BYHOUR=1,2,3\r\n"), UID));
    assertThrows(IllegalArgumentException.class,
                 () -> MailInvitationReader.read(ics("", "RRULE:FREQ=YEARLY;BYMONTH=1,2,3,4,5,6,7,8,9,10,11,12;"
                     + "BYMONTHDAY=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20;BYDAY=MO,TU,WE,TH,FR\r\n"), UID));
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(ics("", ""), "x".repeat(300)));
  }

  /**
   * More excluded dates than the cap is refused, not read in part.
   */
  @Test
  void refusesTooManyExclusions() {
    StringBuilder exclusions = new StringBuilder();
    for (int day = 0; day <= MailInvitationReader.MAX_EXCLUSIONS; day++) {
      exclusions.append("EXDATE:")
                .append(LocalDate.of(2030, 10, 16).plusDays(day).toString().replace("-", ""))
                .append("T090000Z\r\n");
    }
    String object = ics("", "RRULE:FREQ=DAILY\r\n" + exclusions);
    assertThrows(IllegalArgumentException.class, () -> MailInvitationReader.read(object, UID));
  }

  /**
   * Only a mail address is kept as one.
   */
  @Test
  void keepsOnlyMailAddresses() {
    assertEquals("a@b.example", MailInvitationReader.bareAddress(" A@B.example "));
    assertNull(MailInvitationReader.bareAddress("not an address"));
    assertNull(MailInvitationReader.bareAddress(null));
  }

  /**
   * An invitation object.
   *
   * @param calendarProperties lines inside VCALENDAR, before the event
   * @param eventProperties lines inside the event
   * @return the object
   */
  private static String ics(String calendarProperties, String eventProperties) {
    return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Partner//Test//EN\r\n" + calendarProperties + "BEGIN:VEVENT\r\nUID:" + UID
        + "\r\nDTSTAMP:20300101T000000Z\r\nDTSTART:20301015T090000Z\r\nDTEND:20301015T100000Z\r\nSUMMARY:Sync\r\n"
        + eventProperties + "END:VEVENT\r\nEND:VCALENDAR";
  }
}
