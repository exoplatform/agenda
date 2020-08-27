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

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

public class EntityBuilder {

  private static final Log    LOG                  = ExoLogger.getLogger(EntityBuilder.class);

  private static final String IDENTITIES_REST_PATH = "/v1/social/identities";                 // NOSONAR

  private static final String IDENTITIES_EXPAND    = "all";

  private EntityBuilder() {
  }

  public static final Calendar toCalendar(CalendarEntity calendarEntity) {
    return new Calendar(calendarEntity.getId(),
                        Long.parseLong(calendarEntity.getOwner().getId()),
                        calendarEntity.isSystem(),
                        calendarEntity.getTitle(),
                        calendarEntity.getDescription(),
                        calendarEntity.getCreated(),
                        calendarEntity.getUpdated(),
                        calendarEntity.getColor(),
                        calendarEntity.getAcl());
  }

  public static final CalendarEntity fromCalendar(Calendar calendar) {
    return new CalendarEntity(calendar.getId(),
                              getIdentityEntity(calendar.getOwnerId()),
                              calendar.isSystem(),
                              calendar.getTitle(),
                              calendar.getDescription(),
                              calendar.getCreated(),
                              calendar.getUpdated(),
                              calendar.getColor(),
                              calendar.getAcl());
  }

  public static final Event toEvent(EventEntity eventEntity) {
    EventRecurrenceEntity recurrenceEntity = eventEntity.getRecurrence();
    EventRecurrence recurrence = null;
    if (recurrenceEntity != null) {
      recurrence = new EventRecurrence(recurrenceEntity.getId(),
                                       AgendaDateUtils.parseRFC3339ToZonedDateTime(recurrenceEntity.getUntil()),
                                       recurrenceEntity.getCount(),
                                       recurrenceEntity.getFrequency(),
                                       recurrenceEntity.getInterval(),
                                       recurrenceEntity.getBySecond(),
                                       recurrenceEntity.getByMinute(),
                                       recurrenceEntity.getByHour(),
                                       recurrenceEntity.getByDay(),
                                       recurrenceEntity.getByMonthDay(),
                                       recurrenceEntity.getByYearDay(),
                                       recurrenceEntity.getByWeekNo(),
                                       recurrenceEntity.getByMonth(),
                                       null,
                                       null);
    }
    return new Event(eventEntity.getId(),
                     eventEntity.getParent() == null ? 0l : eventEntity.getParent().getId(),
                     eventEntity.getRemoteId(),
                     eventEntity.getRemoteProviderId(),
                     eventEntity.getCalendar() == null ? 0l : eventEntity.getCalendar().getId(),
                     eventEntity.getCreator() == null ? 0l : Long.parseLong(eventEntity.getCreator().getId()),
                     0l,
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getCreated()),
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getUpdated()),
                     eventEntity.getSummary(),
                     eventEntity.getDescription(),
                     eventEntity.getLocation(),
                     eventEntity.getColor(),
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getStart()),
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getEnd()),
                     eventEntity.isAllDay(),
                     eventEntity.getAvailability(),
                     eventEntity.getStatus(),
                     recurrence,
                     eventEntity.getOccurrence(),
                     eventEntity.getAcl());
  }

  public static EventReminder toEventReminder(EventReminderEntity eventReminderEntity) {
    return new EventReminder(eventReminderEntity.getId(),
                             eventReminderEntity.getReceiverId(),
                             eventReminderEntity.getType(),
                             eventReminderEntity.getMinutes(),
                             AgendaDateUtils.parseRFC3339ToZonedDateTime(eventReminderEntity.getDatetime()));
  }

  public static EventAttachment toEventAttachment(EventAttachmentEntity attachmentEntity) {
    return attachmentEntity.getUploadId() == null ? new EventAttachment(attachmentEntity.getId(),
                                                                        attachmentEntity.getFileId(),
                                                                        0l)
                                                  : new EventAttachmentUpload(attachmentEntity.getId(),
                                                                              attachmentEntity.getFileId(),
                                                                              0l,
                                                                              attachmentEntity.getUploadId());
  }

  public static EventAttendee toEventAttendee(EventAttendeeEntity attendeeEntity) throws AgendaException {
    long identityId = 0;
    IdentityEntity attendeeIdentityEntity = attendeeEntity.getIdentity();
    String providerId = attendeeIdentityEntity.getProviderId();
    String remoteId = attendeeIdentityEntity.getRemoteId();
    if (StringUtils.isNotBlank(providerId) && StringUtils.isNotBlank(remoteId)) {
      IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
      Identity identity = identityManager.getOrCreateIdentity(providerId, remoteId);
      identityId = Long.parseLong(identity.getId());
    } else if (StringUtils.isNotBlank(attendeeIdentityEntity.getId())) {
      identityId = Long.parseLong(attendeeIdentityEntity.getId());
    } else {
      throw new AgendaException(AgendaExceptionType.WRONG_EVENT_ATTENDEE_ID);
    }
    return new EventAttendee(attendeeEntity.getId(), identityId, attendeeEntity.getResponse());
  }

  public static final EventAttendeeEntity fromEventAttendee(EventAttendee eventAttendee) {
    return new EventAttendeeEntity(eventAttendee.getId(),
                                   getIdentityEntity(eventAttendee.getIdentityId()),
                                   eventAttendee.getResponse());
  }

  public static final EventAttachmentEntity fromEventAttachment(EventAttachment eventAttachment) {
    FileService fileService = ExoContainerContext.getService(FileService.class);
    FileItem file = null;
    try {
      file = fileService.getFile(eventAttachment.getFileId());
    } catch (FileStorageException e) {
      LOG.warn("Can't find file with id '{}'", eventAttachment.getFileId());
    }
    if (file == null) {
      return null;
    }
    String name = file.getFileInfo().getName();
    String mimeType = file.getFileInfo().getMimetype();
    long size = file.getFileInfo().getSize();

    return new EventAttachmentEntity(eventAttachment.getId(),
                                     eventAttachment.getFileId(),
                                     null,
                                     null,
                                     name,
                                     mimeType,
                                     size);
  }

  public static final EventReminderEntity fromEventReminder(EventReminder eventReminder) {
    return new EventReminderEntity(eventReminder.getId(),
                                   eventReminder.getReceiverId(),
                                   eventReminder.getType(),
                                   eventReminder.getMinutes(),
                                   AgendaDateUtils.toRFC3339Date(eventReminder.getDatetime()));
  }

  public static final EventEntity fromEvent(Event event) {
    EventRecurrence recurrence = event.getRecurrence();
    EventRecurrenceEntity recurrenceEntity = null;
    if (recurrence != null) {
      recurrenceEntity = new EventRecurrenceEntity(recurrence.getId(),
                                                   AgendaDateUtils.toRFC3339Date(recurrence.getUntil()),
                                                   recurrence.getCount(),
                                                   recurrence.getFrequency(),
                                                   recurrence.getInterval(),
                                                   recurrence.getBySecond(),
                                                   recurrence.getByMinute(),
                                                   recurrence.getByHour(),
                                                   recurrence.getByDay(),
                                                   recurrence.getByMonthDay(),
                                                   recurrence.getByYearDay(),
                                                   recurrence.getByWeekNo(),
                                                   recurrence.getByMonth());
    }
    return new EventEntity(event.getId(),
                           getEventEntity(event.getParentId()),
                           event.getRemoteId(),
                           event.getRemoteProviderId(),
                           getCalendarEntity(event.getCalendarId()),
                           getIdentityEntity(event.getCreatorId()),
                           AgendaDateUtils.toRFC3339Date(event.getCreated()),
                           AgendaDateUtils.toRFC3339Date(event.getUpdated()),
                           event.getSummary(),
                           event.getDescription(),
                           event.getLocation(),
                           event.getColor(),
                           AgendaDateUtils.toRFC3339Date(event.getStart()),
                           AgendaDateUtils.toRFC3339Date(event.getEnd()),
                           event.isAllDay(),
                           event.getAvailability(),
                           event.getStatus(),
                           recurrenceEntity,
                           event.getOccurrence(),
                           event.getAcl(),
                           null,
                           null,
                           null,
                           null,
                           false);
  }

  private static CalendarEntity getCalendarEntity(long calendarId) {
    if (calendarId <= 0) {
      return null;
    }
    AgendaCalendarService agendaCalendarService = ExoContainerContext.getService(AgendaCalendarService.class);
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    return fromCalendar(calendar);
  }

  private static EventEntity getEventEntity(long eventId) {
    if (eventId <= 0) {
      return null;
    }
    AgendaEventService agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    Event event = agendaEventService.getEventById(eventId);
    return fromEvent(event);
  }

  private static IdentityEntity getIdentityEntity(long ownerId) {
    Identity identity = getIdentity(ownerId);
    if (identity == null) {
      return null;
    }
    return org.exoplatform.social.rest.api.EntityBuilder.buildEntityIdentity(identity, IDENTITIES_REST_PATH, IDENTITIES_EXPAND);
  }

  private static final Identity getIdentity(long identityId) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    return identityManager.getIdentity(String.valueOf(identityId));
  }

}
