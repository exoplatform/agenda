/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.rest.model.EventRecurrenceEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * The last day of a series, as the clients write it, the last second of that
 * day in the zone the event is read and saved in, and as it is stored, the
 * same second in the event's zone.
 */
class EventRecurrenceEndMappingTest {

  private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 5);

  /**
   * The form's end date, and the day before the dates "delete this and
   * following" removes, are written as the last second of that day: the
   * series ends that day, west of UTC as east of it.
   */
  @ParameterizedTest
  @ValueSource(strings = { "America/New_York", "Pacific/Honolulu", "UTC", "Europe/Paris", "Asia/Tokyo", "Pacific/Kiritimati" })
  void aSeriesWrittenToEndOnADayEndsThatDay(String zone) {
    EventEntity payload = payload(zone);
    payload.getRecurrence().setUntil("2026-10-05T23:59:59");

    assertEquals(LAST_DAY, RestEntityBuilder.toEvent(payload).getRecurrence().getUntil());
  }

  /**
   * The end a read writes, sent back unchanged, as the form does when the end
   * is not touched, is the same day.
   */
  @ParameterizedTest
  @ValueSource(strings = { "America/New_York", "Pacific/Honolulu", "UTC", "Europe/Paris", "Asia/Tokyo", "Pacific/Kiritimati" })
  void aSeriesEndAReadWritesIsTheSameDaySentBack(String zone) {
    ZoneId zoneId = ZoneId.of(zone);
    EventEntity read = RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class),
                                                   mock(AgendaEventService.class),
                                                   mock(IdentityManager.class),
                                                   series(zoneId),
                                                   zoneId);

    assertEquals(LAST_DAY, RestEntityBuilder.toEvent(read).getRecurrence().getUntil());
  }

  /**
   * The end stored is read back as the same day whatever the server's zone,
   * so that a series read and saved back keeps it: in the server's zone, a
   * series behind it read a day later on each save.
   */
  @ParameterizedTest
  @CsvSource({ "America/New_York,UTC", "America/New_York,Africa/Tunis", "Pacific/Honolulu,UTC", "Europe/Paris,UTC",
      "Asia/Tokyo,UTC", "Asia/Tokyo,America/Los_Angeles", "Pacific/Kiritimati,Pacific/Honolulu", "UTC,Asia/Tokyo" })
  void aSeriesEndStoredIsReadBackAsTheSameDayWhateverTheServersZone(String zone, String serverZone) {
    TimeZone defaultZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
    try {
      ZoneId zoneId = ZoneId.of(zone);
      Event series = series(zoneId);
      org.exoplatform.agenda.entity.EventEntity stored = new org.exoplatform.agenda.entity.EventEntity();
      stored.setTimeZoneId(zone);
      stored.setStartDate(AgendaDateUtils.toDate(series.getStart()));
      stored.setEndDate(AgendaDateUtils.toDate(series.getEnd()));

      org.exoplatform.agenda.entity.EventRecurrenceEntity storedRecurrence = EntityMapper.toEntity(series, series.getRecurrence());
      storedRecurrence.setId(3L);

      EventRecurrence readBack = EntityMapper.fromEntity(storedRecurrence, stored);

      assertEquals(LAST_DAY, readBack.getUntil());
    } finally {
      TimeZone.setDefault(defaultZone);
    }
  }

  /**
   * A rule saved before 7.3 holds its end as a floating time, the last second
   * of that day as written in the event's zone, and a rule may hold a day
   * alone: either is read back as that day, whatever the server's zone.
   */
  @ParameterizedTest
  @CsvSource({ "Europe/Paris,UTC,20261005T235959", "Asia/Tokyo,UTC,20261005T235959", "Asia/Tokyo,America/Los_Angeles,20261005T235959",
      "America/New_York,UTC,20261005T235959", "America/New_York,Asia/Tokyo,20261005T235959", "Europe/Paris,UTC,20261005",
      "America/New_York,Asia/Tokyo,20261005", "Asia/Tokyo,America/Los_Angeles,20261005" })
  void aSeriesEndStoredByAnEarlierVersionIsReadBackAsTheSameDay(String zone, String serverZone, String until) {
    TimeZone defaultZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
    try {
      Event series = series(ZoneId.of(zone));
      org.exoplatform.agenda.entity.EventEntity stored = new org.exoplatform.agenda.entity.EventEntity();
      stored.setTimeZoneId(zone);
      stored.setStartDate(AgendaDateUtils.toDate(series.getStart()));
      stored.setEndDate(AgendaDateUtils.toDate(series.getEnd()));
      org.exoplatform.agenda.entity.EventRecurrenceEntity storedRecurrence = EntityMapper.toEntity(series, series.getRecurrence());
      storedRecurrence.setId(3L);
      storedRecurrence.setRrule("FREQ=DAILY;UNTIL=" + until + ";INTERVAL=1");

      assertEquals(LAST_DAY, EntityMapper.fromEntity(storedRecurrence, stored).getUntil());
    } finally {
      TimeZone.setDefault(defaultZone);
    }
  }

  private Event series(ZoneId zoneId) {
    Event series = new Event();
    series.setId(7);
    series.setTimeZoneId(zoneId);
    series.setStart(ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, zoneId));
    series.setEnd(series.getStart().plusHours(1));
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setType(EventRecurrenceType.DAILY);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);
    recurrence.setUntil(LAST_DAY);
    series.setRecurrence(recurrence);
    return series;
  }

  private EventEntity payload(String zone) {
    EventEntity payload = new EventEntity();
    payload.setId(7);
    payload.setTimeZoneId(zone);
    payload.setStart("2026-10-01T10:00:00");
    payload.setEnd("2026-10-01T11:00:00");
    EventRecurrenceEntity recurrence = new EventRecurrenceEntity();
    recurrence.setType(EventRecurrenceType.DAILY);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);
    payload.setRecurrence(recurrence);
    return payload;
  }

}
