// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.ReminderPeriodType;

import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@EqualsAndHashCode
@NoArgsConstructor
public class EventReminderParameter implements Serializable {

  private static final long   serialVersionUID = -2033232204026792697L;

  private static final String SEPARATOR        = " ";

  @Getter
  @Setter
  private int                 before;

  @Setter
  private ReminderPeriodType  beforePeriodType;

  /**
   * A field added to be able to inject Object by kernel configuration. ( No
   * supported enum type )
   */
  @Setter
  @Exclude
  private String              periodType;

  public EventReminderParameter(int before, ReminderPeriodType beforePeriodType) {
    this.before = before;
    this.beforePeriodType = beforePeriodType;
  }

  public ReminderPeriodType getBeforePeriodType() {
    if (this.beforePeriodType == null && StringUtils.isNotBlank(periodType)) {
      this.beforePeriodType = ReminderPeriodType.valueOf(periodType);
    }
    return beforePeriodType;
  }

  @Override
  public String toString() {
    return before + SEPARATOR + beforePeriodType;
  }

  public static EventReminderParameter fromString(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String[] values = value.split(SEPARATOR);
    EventReminderParameter reminderParameter = new EventReminderParameter();
    reminderParameter.setBefore(Integer.parseInt(values[0]));
    reminderParameter.setBeforePeriodType(ReminderPeriodType.valueOf(values[1]));
    return reminderParameter;
  }
}
