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

import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;

public class EntityMapper {

  private EntityMapper() {
  }

  public static Calendar fromEntity(CalendarEntity calendarEntity) {
    if (calendarEntity == null) {
      return null;
    }
    return new Calendar(calendarEntity.getId(),
                        calendarEntity.getOwnerId(),
                        calendarEntity.isSystem(),
                        null,
                        calendarEntity.getDescription(),
                        calendarEntity.getCreatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getCreatedDate()),
                        calendarEntity.getUpdatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getUpdatedDate()),
                        calendarEntity.getColor(),
                        null);
  }

  public static CalendarEntity toEntity(Calendar calendar) {
    if (calendar == null) {
      return null;
    }
    CalendarEntity calendarEntity = new CalendarEntity();
    if (calendar.getId() != 0) {
      calendarEntity.setId(calendar.getId());
    }
    if (calendar.getOwnerId() != 0) {
      calendarEntity.setOwnerId(calendar.getOwnerId());
    }
    calendarEntity.setColor(calendar.getColor());
    if (calendar.getCreated() != null) {
      calendarEntity.setCreatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getCreated()));
    }
    if (calendar.getUpdated() != null) {
      calendarEntity.setUpdatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getUpdated()));
    }
    calendarEntity.setDescription(calendar.getDescription());
    calendarEntity.setSystem(calendar.isSystem());
    return calendarEntity;
  }

  public static EventReminder fromEntity(EventReminderEntity eventReminderEntity) {
    return new EventReminder(eventReminderEntity.getId(),
                             eventReminderEntity.getReceiverId(),
                             eventReminderEntity.getType(),
                             eventReminderEntity.getMinutes(),
                             AgendaDateUtils.fromDate(eventReminderEntity.getTriggerDate()));
  }

  public static EventReminderEntity toEntity(EventReminder eventReminder) {
    EventReminderEntity eventReminderEntity = new EventReminderEntity();
    eventReminderEntity.setId(eventReminder.getId());
    eventReminderEntity.setMinutes(eventReminder.getMinutes());
    eventReminderEntity.setReceiverId(eventReminder.getReceiverId());
    eventReminderEntity.setTriggerDate(AgendaDateUtils.toDate(eventReminder.getDatetime()));
    eventReminderEntity.setType(eventReminder.getType());
    return eventReminderEntity;
  }

  public static Event fromEntity(EventEntity eventEntity) {
    // TODO Auto-generated method stub
    return null;
  }

  public static EventEntity toEntity(Event event) {
    // TODO Auto-generated method stub
    return null;
  }

  public static EventRecurrenceEntity toEntity(Event event, EventRecurrence recurrence) {
    EventRecurrenceEntity eventRecurrenceEntity = new EventRecurrenceEntity();
    eventRecurrenceEntity.setCount(recurrence.getCount());
    // Store in Recurrence properties the start and end dates of the event as
    // given by the user
    eventRecurrenceEntity.setStartDate(AgendaDateUtils.toDate(event.getStart()));
    eventRecurrenceEntity.setEndDate(AgendaDateUtils.toDate(event.getEnd()));
    eventRecurrenceEntity.setFrequency(recurrence.getFrequency());
    eventRecurrenceEntity.setUntilDate(AgendaDateUtils.toDate(recurrence.getUntil()));
    eventRecurrenceEntity.setId(recurrence.getId());
    eventRecurrenceEntity.setInterval(recurrence.getInterval());
    return eventRecurrenceEntity;
  }

  public static EventAttendee fromEntity(EventAttendeeEntity eventAttendeeEntity) {
    return new EventAttendee(eventAttendeeEntity.getId(), eventAttendeeEntity.getIdentityId(), eventAttendeeEntity.getResponse());
  }

  public static EventAttendeeEntity toEntity(EventAttendee eventAttendee) {
    EventAttendeeEntity eventAttendeeEntity = new EventAttendeeEntity();
    eventAttendeeEntity.setId(eventAttendee.getId());
    eventAttendeeEntity.setIdentityId(eventAttendee.getIdentityId());
    eventAttendeeEntity.setResponse(eventAttendee.getResponse());
    return eventAttendeeEntity;
  }

  public static EventAttachment fromEntity(EventAttachmentEntity eventAttachmentEntity) {
    return new EventAttachment(eventAttachmentEntity.getId(),
                               eventAttachmentEntity.getFileId(),
                               eventAttachmentEntity.getEvent().getId());
  }

  public static EventConference fromEntity(EventConferenceEntity eventConferenceEntity) {
    return new EventConference(eventConferenceEntity.getId(),
                               eventConferenceEntity.getEvent().getId(),
                               eventConferenceEntity.getType(),
                               eventConferenceEntity.getUri(),
                               eventConferenceEntity.getPhone(),
                               eventConferenceEntity.getAccessCode(),
                               eventConferenceEntity.getDescription());
  }

  public static EventConferenceEntity toEntity(EventConference eventConference) {
    EventConferenceEntity eventConferenceEntity = new EventConferenceEntity();
    eventConferenceEntity.setId(eventConference.getId());
    eventConferenceEntity.setAccessCode(eventConference.getAccessCode());
    eventConferenceEntity.setDescription(eventConference.getDescription());
    eventConferenceEntity.setPhone(eventConference.getPhone());
    eventConferenceEntity.setType(eventConference.getType());
    eventConferenceEntity.setUri(eventConference.getUri());
    return eventConferenceEntity;
  }

}
