/*
 * Copyright (C) 2020 eXo Platform SAS.
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

public class AgendaDateUtils {
  private static final String           ALL_DAY_FORMAT          = "yyyy-MM-dd";

  private static final String           HOUR_FORMAT             = "hh";

  public static final DateTimeFormatter RFC_3339_FORMATTER      = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter ALL_DAY_FORMATTER       = DateTimeFormatter.ofPattern(ALL_DAY_FORMAT)
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter OCCURRENCE_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXXX")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter TIMEZONE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);


  private AgendaDateUtils() {
  }

  public static ZonedDateTime parseAllDayDateToZonedDateTime(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    return LocalDate.parse(dateString.substring(0, 10), AgendaDateUtils.ALL_DAY_FORMATTER)
                    .atStartOfDay(ZoneOffset.UTC);
  }

  public static ZonedDateTime parseRFC3339ToZonedDateTime(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    return ZonedDateTime.parse(dateString, RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime) {
    if (zonedDateTime == null) {
      return null;
    }
    return zonedDateTime.format(RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime, boolean allDay) {
    if (zonedDateTime == null) {
      return null;
    }
    if (allDay) {
      return zonedDateTime.format(ALL_DAY_FORMATTER);
    } else {
      return zonedDateTime.format(RFC_3339_FORMATTER);
    }
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime, ZoneOffset zoneOffset) {
    if (zonedDateTime == null) {
      return null;
    }
    return zonedDateTime.withZoneSameInstant(zoneOffset).format(RFC_3339_FORMATTER);
  }

  public static Date parseRFC3339Date(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateString, RFC_3339_FORMATTER);
    return Date.from(zonedDateTime.toInstant());
  }

  public static String toRFC3339Date(Date dateTime) {
    if (dateTime == null) {
      return null;
    }
    ZonedDateTime zonedDateTime = ZonedDateTime.from(dateTime.toInstant().atOffset(ZoneOffset.UTC));
    return zonedDateTime.format(RFC_3339_FORMATTER);
  }

  public static Date toDate(ZonedDateTime datetime) {
    if (datetime == null) {
      return null;
    }
    return new Date(datetime.toEpochSecond() * 1000);
  }

  public static ZonedDateTime fromDate(Date date) {
    if (date == null) {
      return null;
    }
    return date.toInstant().atZone(ZoneOffset.UTC);
  }

  public static String buildOccurrenceId(Date formTime) {
    if (formTime == null) {
      return null;
    }
    return OCCURRENCE_ID_FORMATTER.format(formTime.toInstant().atOffset(ZoneOffset.UTC));
  }

  public static String formatDateTimeWithSeconds(TemporalAccessor dateTime) {
    if (dateTime == null) {
      return null;
    }
    return TIMEZONE_DATE_FORMATTER.format(dateTime);
  }

  public static ZonedDateTime buildOccurrenceDateTime(String occurrenceId) {
    if (StringUtils.isBlank(occurrenceId)) {
      return null;
    }
    return ZonedDateTime.parse(occurrenceId, OCCURRENCE_ID_FORMATTER);
  }

  public static String toHourFormat(ZonedDateTime zonedDateTime) {
    return DateTimeFormatter.ofPattern(HOUR_FORMAT).format(zonedDateTime);
  }


}
