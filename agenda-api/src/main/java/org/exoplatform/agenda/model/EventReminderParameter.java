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
package org.exoplatform.agenda.model;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.ReminderPeriodType;

import lombok.*;

@EqualsAndHashCode
@NoArgsConstructor
public class EventReminderParameter implements Serializable {

  private static final long serialVersionUID = -2033232204026792697L;

  private static final String SEPARATOR = " ";

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
