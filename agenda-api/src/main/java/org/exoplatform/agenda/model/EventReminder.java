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

import java.time.ZonedDateTime;

import org.exoplatform.agenda.constant.ReminderPeriodType;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventReminder implements Cloneable {

  private long               id;

  private long               eventId;

  private long               receiverId;

  private int                before;

  private ReminderPeriodType beforePeriodType;

  /**
   * Date and time converted to user timezone (Timestamp representation using
   * RFC-3339). This is the computed datetime to send reminder.
   */
  private ZonedDateTime      datetime;

  public EventReminder(long receiverId, int before, ReminderPeriodType beforePeriodType) {
    this.receiverId = receiverId;
    this.before = before;
    this.beforePeriodType = beforePeriodType;
  }

  public EventReminder(long id, long eventId, long receiverId, int before, ReminderPeriodType beforePeriodType) {
    this.id = id;
    this.eventId = eventId;
    this.receiverId = receiverId;
    this.before = before;
    this.beforePeriodType = beforePeriodType;
  }

  @Override
  public EventReminder clone() { // NOSONAR
    return new EventReminder(id, eventId, receiverId, before, beforePeriodType, datetime);
  }

}
