/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.util;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Test;

public class UtilsOccurrenceIdTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 11);

  private static final ZonedDateTime EXPECTED = DATE.atStartOfDay(ZoneOffset.UTC);

  private static final String[]  ZONES = { "America/New_York", "Europe/Paris", "UTC" };

  @Test
  public void testAllDayOccurrenceIdOfStartInSeriesZoneIsTheUtcMidnightOfItsDate() {
    for (String zone : ZONES) {
      ZoneId seriesZone = ZoneId.of(zone);
      ZonedDateTime id = Utils.getOccurrenceId(true, DATE.atStartOfDay(seriesZone), seriesZone);
      assertEquals(zone, DATE.atStartOfDay(ZoneOffset.UTC), id);
    }
  }

  @Test
  public void testAllDayOccurrenceIdNormalisationIsIdempotent() {
    for (String zone : ZONES) {
      ZoneId seriesZone = ZoneId.of(zone);
      ZonedDateTime once = Utils.getOccurrenceId(true, DATE.atStartOfDay(seriesZone), seriesZone);
      ZonedDateTime twice = Utils.getOccurrenceId(true, once, seriesZone);
      assertEquals(zone, once, twice);
    }
  }

  @Test
  public void testAllDayOccurrenceIdAlreadyAtUtcMidnightKeepsItsDate() {
    for (String zone : ZONES) {
      ZoneId seriesZone = ZoneId.of(zone);
      ZonedDateTime utcMidnight = DATE.atStartOfDay(ZoneOffset.UTC);
      assertEquals(zone, utcMidnight, Utils.getOccurrenceId(true, utcMidnight, seriesZone));
    }
  }

  @Test
  public void testTimedOccurrenceIdIsTheSameInstantInUtc() {
    ZoneId seriesZone = ZoneId.of("America/New_York");
    ZonedDateTime start = ZonedDateTime.of(DATE, LocalTime.of(9, 30), seriesZone);
    ZonedDateTime id = Utils.getOccurrenceId(false, start, seriesZone);
    assertEquals(start.toInstant(), id.toInstant());
    assertEquals(ZoneOffset.UTC, id.getOffset());
    assertEquals(ZonedDateTime.of(DATE, LocalTime.of(13, 30), ZoneOffset.UTC), id);
  }

  private void assertAllDay(String zone, String input) {
    ZoneId seriesZone = ZoneId.of(zone);
    ZonedDateTime id = Utils.getOccurrenceId(true, ZonedDateTime.parse(input), seriesZone);
    assertEquals(input, EXPECTED, id);
    assertEquals(input, id, Utils.getOccurrenceId(true, id, seriesZone));
    ZonedDateTime parsedAsUtc = ZonedDateTime.parse(input).withZoneSameInstant(ZoneOffset.UTC);
    assertEquals(input, EXPECTED, Utils.getOccurrenceId(true, parsedAsUtc, seriesZone));
  }

  @Test
  public void testAllDayOccurrenceIdParisOffsetMidnight() {
    assertAllDay("Europe/Paris", "2026-10-11T00:00:00+02:00");
  }

  @Test
  public void testAllDayOccurrenceIdTokyoOffsetMidnight() {
    assertAllDay("Asia/Tokyo", "2026-10-11T00:00:00+09:00");
  }

  @Test
  public void testAllDayOccurrenceIdNewYorkOffsetMidnight() {
    assertAllDay("America/New_York", "2026-10-11T00:00:00-04:00");
  }

  @Test
  public void testAllDayOccurrenceIdNewYorkUserFormattedUtcMidnight() {
    assertAllDay("America/New_York", "2026-10-10T20:00:00-04:00");
  }

  @Test
  public void testAllDayOccurrenceIdUtcMidnightForAnyZone() {
    for (String zone : new String[] { "Europe/Paris", "Asia/Tokyo", "America/New_York", "UTC" }) {
      assertAllDay(zone, "2026-10-11T00:00:00Z");
    }
  }

  @Test
  public void testAllDayOccurrenceIdTokyoSeriesZoneMidnight() {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime id = Utils.getOccurrenceId(true, DATE.atStartOfDay(tokyo), tokyo);
    assertEquals(EXPECTED, id);
  }
}
