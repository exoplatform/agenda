// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

public class AgendaDateUtils {
  private static final String           ALL_DAY_FORMAT          = "yyyy-MM-dd";

  private static final String           TIME_FORMAT             = "HH:mm";

  public static final DateTimeFormatter RFC_3339_FORMATTER      = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter ALL_DAY_FORMATTER       = DateTimeFormatter.ofPattern(ALL_DAY_FORMAT)
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter OCCURRENCE_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXXX")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter TIMEZONE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter TIME_FORMATTER          = DateTimeFormatter.ofPattern(TIME_FORMAT)
                                                                                   .withResolverStyle(ResolverStyle.LENIENT);

  public static final DateTimeFormatter FULL_TIME_FORMATTER     = DateTimeFormatter.ofPattern("EEE MMM dd, yyyy")
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

  public static ZonedDateTime parseRFC3339ToZonedDateTime(String dateString, ZoneId zoneId) {
    return parseRFC3339ToZonedDateTime(dateString, zoneId, true);
  }

  public static ZonedDateTime parseRFC3339ToZonedDateTime(String dateString, ZoneId zoneId, boolean parseTimeZone) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    if (!parseTimeZone) {
      return LocalDateTime.parse(dateString, RFC_3339_FORMATTER).atZone(ZoneId.systemDefault()).withZoneSameLocal(zoneId);
    } else if (dateString.length() > 20) {
      return ZonedDateTime.parse(dateString, RFC_3339_FORMATTER).withZoneSameInstant(zoneId);
    } else {
      return LocalDateTime.parse(dateString, RFC_3339_FORMATTER).atZone(zoneId);
    }
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime) {
    if (zonedDateTime == null) {
      return null;
    }
    return zonedDateTime.format(RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime, ZoneId zoneOffset, boolean allDay) {
    if (zonedDateTime == null) {
      return null;
    }
    if (zoneOffset == null) {
      zoneOffset = ZoneOffset.UTC;
    }
    if (allDay) {
      return zonedDateTime.withZoneSameLocal(zoneOffset).format(ALL_DAY_FORMATTER);
    } else {
      return zonedDateTime.withZoneSameInstant(zoneOffset).format(RFC_3339_FORMATTER);
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
    return Date.from(datetime.toInstant());
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

  public static String buildOccurrenceId(ZonedDateTime formTime) {
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

  public static String formatWithHoursAndMinutes(ZonedDateTime zonedDateTime) {
    return zonedDateTime.format(TIME_FORMATTER);
  }

  public static String formatWithYearAndMonth(ZonedDateTime zonedDateTime) {
    return zonedDateTime.format(FULL_TIME_FORMATTER);
  }

  public static final String getDayNameFromDayAbbreviation(List<String> dayNames) {
    List<String> daysFinal = new ArrayList<>();
    for (String name : dayNames) {
      switch (name) { // NOSONAR
        case "MO":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(1))));
          break;
        case "TU":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(2))));
          break;
        case "WE":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(3))));
          break;
        case "TH":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(4))));
          break;
        case "FR":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(5))));
          break;
        case "SA":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(6))));
          break;
        case "SU":
          daysFinal.add(StringUtils.lowerCase(String.valueOf(DayOfWeek.of(7))));
          break;
      }
    }
    return StringUtils.join(daysFinal, ",");
  }

}
