// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
