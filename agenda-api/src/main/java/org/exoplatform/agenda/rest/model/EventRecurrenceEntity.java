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
