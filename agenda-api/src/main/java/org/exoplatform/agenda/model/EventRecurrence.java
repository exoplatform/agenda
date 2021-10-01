// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRecurrence implements Cloneable, Serializable {

  private static final long        serialVersionUID = 5053547947788569270L;

  private long                     id;

  private LocalDate                until;

  private int                      count;

  private EventRecurrenceType      type;

  private EventRecurrenceFrequency frequency;

  private int                      interval;

  private String                   rrule;

  private List<String>             bySecond;

  private List<String>             byMinute;

  private List<String>             byHour;

  private List<String>             byDay;

  private List<String>             byMonthDay;

  private List<String>             byYearDay;

  private List<String>             byWeekNo;

  private List<String>             byMonth;

  private List<String>             bySetPos;

  /**
   * This field is computed from recurrence properties to know what is the first
   * start date of all occurrences of current recurrent event. It's made
   * transient to not retrieve it in REST Object details
   */
  private transient ZonedDateTime  overallStart;

  /**
   * This field is computed from recurrence properties to know what is the first
   * start date of all occurrences of current recurrent event. It's made
   * transient to not retrieve it in REST Object details
   */
  private transient ZonedDateTime  overallEnd;

  public EventRecurrence(long id,
                         LocalDate until,
                         int count,
                         EventRecurrenceType type,
                         EventRecurrenceFrequency frequency,
                         int interval,
                         List<String> bySecond,
                         List<String> byMinute,
                         List<String> byHour,
                         List<String> byDay,
                         List<String> byMonthDay,
                         List<String> byYearDay,
                         List<String> byWeekNo,
                         List<String> byMonth,
                         List<String> bySetPos,
                         ZonedDateTime overallStart,
                         ZonedDateTime overallEnd) {
    this.id = id;
    this.until = until;
    this.count = count;
    this.type = type;
    this.frequency = frequency;
    this.interval = interval;
    this.bySecond = bySecond;
    this.byMinute = byMinute;
    this.byHour = byHour;
    this.byDay = byDay;
    this.byMonthDay = byMonthDay;
    this.byYearDay = byYearDay;
    this.byWeekNo = byWeekNo;
    this.byMonth = byMonth;
    this.bySetPos = bySetPos;
    this.overallStart = overallStart;
    this.overallEnd = overallEnd;
  }

  @Override
  public EventRecurrence clone() { // NOSONAR
    return new EventRecurrence(id,
                               until,
                               count,
                               type,
                               frequency,
                               interval,
                               rrule,
                               bySecond == null ? null : new ArrayList<>(bySecond),
                               byMinute == null ? null : new ArrayList<>(byMinute),
                               byHour == null ? null : new ArrayList<>(byHour),
                               byDay == null ? null : new ArrayList<>(byDay),
                               byMonthDay == null ? null : new ArrayList<>(byMonthDay),
                               byYearDay == null ? null : new ArrayList<>(byYearDay),
                               byWeekNo == null ? null : new ArrayList<>(byWeekNo),
                               byMonth == null ? null : new ArrayList<>(byMonth),
                               bySetPos,
                               overallStart,
                               overallEnd);
  }

}
