// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.rest.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRecurrenceEntity implements Cloneable, Serializable {

  private static final long        serialVersionUID = 5053547947788569270L;

  private long                     id;

  private String                   until;

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

  @Override
  public EventRecurrenceEntity clone() { // NOSONAR
    return new EventRecurrenceEntity(id,
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
                                     bySetPos);
  }
}
