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
import lombok.EqualsAndHashCode.Exclude;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EventReminder extends EventReminderParameter implements Cloneable {

  private static final long      serialVersionUID = -7068947528529938343L;

  @Exclude
  private long                   id;

  private long                   eventId;

  private long                   receiverId;

  /**
   * Date and time converted to user timezone (Timestamp representation using
   * RFC-3339). This is the computed datetime to send reminder.
   */
  @Exclude
  private volatile ZonedDateTime datetime;

  private ZonedDateTime          fromOccurrenceId;

  private ZonedDateTime          untilOccurrenceId;

  public EventReminder(long id,
                       long eventId,
                       long receiverId,
                       int before,
                       ReminderPeriodType beforePeriodType,
                       ZonedDateTime datetime,
                       ZonedDateTime fromOccurrenceId,
                       ZonedDateTime untilOccurrenceId) {
    this.id = id;
    this.eventId = eventId;
    this.receiverId = receiverId;
    this.datetime = datetime;
    this.fromOccurrenceId = fromOccurrenceId;
    this.untilOccurrenceId = untilOccurrenceId;
    this.setBefore(before);
    this.setBeforePeriodType(beforePeriodType);
  }

  public EventReminder(long id,
                       long eventId,
                       long receiverId,
                       int before,
                       ReminderPeriodType beforePeriodType,
                       ZonedDateTime datetime) {
    this.id = id;
    this.eventId = eventId;
    this.receiverId = receiverId;
    this.datetime = datetime;
    this.setBefore(before);
    this.setBeforePeriodType(beforePeriodType);
  }

  public EventReminder(long id, long eventId, long receiverId, int before, ReminderPeriodType beforePeriodType) {
    this.id = id;
    this.eventId = eventId;
    this.receiverId = receiverId;
    this.setBefore(before);
    this.setBeforePeriodType(beforePeriodType);
  }

  public EventReminder(long receiverId, int before, ReminderPeriodType beforePeriodType) {
    this.receiverId = receiverId;
    this.setBefore(before);
    this.setBeforePeriodType(beforePeriodType);
  }

  @Override
  public EventReminder clone() { // NOSONAR
    return new EventReminder(id,
                             eventId,
                             receiverId,
                             this.getBefore(),
                             this.getBeforePeriodType(),
                             datetime,
                             fromOccurrenceId,
                             untilOccurrenceId);
  }

}
