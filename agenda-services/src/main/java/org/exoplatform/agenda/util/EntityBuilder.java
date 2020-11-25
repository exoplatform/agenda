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

import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

public class EntityBuilder {

  private static final String IDENTITIES_REST_PATH = "/v1/social/identities"; // NOSONAR

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

  public static final CalendarEntity fromCalendar(IdentityManager identityManager, Calendar calendar) {
    return new CalendarEntity(calendar.getId(),
                              getIdentityEntity(identityManager, calendar.getOwnerId()),
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
                                       recurrenceEntity.getType(),
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
                                       recurrenceEntity.getBySetPos(),
                                       null,
                                       null);
    }
    EventOccurrenceEntity occurrenceEntity = eventEntity.getOccurrence();
    EventOccurrence occurrence = null;
    if (occurrenceEntity != null) {
      occurrence = new EventOccurrence(AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceEntity.getId()),
                                       occurrenceEntity.isExceptional());
    }

    ZonedDateTime startDate = eventEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(eventEntity.getStart())
                                                     : AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getStart());
    ZonedDateTime endDate = eventEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(eventEntity.getEnd())
                                                   : AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getEnd());
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
                     startDate,
                     endDate,
                     eventEntity.isAllDay(),
                     eventEntity.getAvailability(),
                     eventEntity.getStatus(),
                     recurrence,
                     occurrence,
                     eventEntity.getAcl(),
                     eventEntity.isAllowAttendeeToUpdate(),
                     eventEntity.isAllowAttendeeToInvite());
  }

  public static EventReminder toEventReminder(EventReminderEntity eventReminderEntity) {
    String beforePeriodTypeName = eventReminderEntity.getBeforePeriodType();
    ReminderPeriodType beforePeriodType = null;
    if (StringUtils.isNotBlank(beforePeriodTypeName)) {
      beforePeriodType = ReminderPeriodType.valueOf(beforePeriodTypeName.toUpperCase());
    }
    return new EventReminder(eventReminderEntity.getId(),
                             eventReminderEntity.getReceiverId(),
                             eventReminderEntity.getBefore(),
                             beforePeriodType,
                             AgendaDateUtils.parseRFC3339ToZonedDateTime(eventReminderEntity.getDatetime()));
  }

  public static EventAttachment toEventAttachment(EventAttachmentEntity attachmentEntity) {
    return new EventAttachment(attachmentEntity.getId(),
                               attachmentEntity.getFileId(),
                               0l);
  }

  public static EventAttendee toEventAttendee(IdentityManager identityManager,
                                              long eventId,
                                              EventAttendeeEntity attendeeEntity) throws AgendaException {
    long identityId = 0;
    IdentityEntity attendeeIdentityEntity = attendeeEntity.getIdentity();
    String providerId = attendeeIdentityEntity.getProviderId();
    String remoteId = attendeeIdentityEntity.getRemoteId();
    if (StringUtils.isNotBlank(providerId) && StringUtils.isNotBlank(remoteId)) {
      Identity identity = identityManager.getOrCreateIdentity(providerId, remoteId);
      identityId = Long.parseLong(identity.getId());
    } else if (StringUtils.isNotBlank(attendeeIdentityEntity.getId())) {
      identityId = Long.parseLong(attendeeIdentityEntity.getId());
    } else {
      throw new AgendaException(AgendaExceptionType.WRONG_EVENT_ATTENDEE_ID);
    }
    return new EventAttendee(attendeeEntity.getId(), eventId, identityId, attendeeEntity.getResponse());
  }

  public static final EventAttendeeEntity fromEventAttendee(IdentityManager identityManager, EventAttendee eventAttendee) {
    return new EventAttendeeEntity(eventAttendee.getId(),
                                   getIdentityEntity(identityManager, eventAttendee.getIdentityId()),
                                   eventAttendee.getResponse());
  }

  public static final EventAttachmentEntity fromEventAttachment(EventAttachment eventAttachment) {
    return new EventAttachmentEntity(eventAttachment.getId(),
                                     eventAttachment.getFileId());
  }

  public static final EventReminderEntity fromEventReminder(EventReminder eventReminder) {
    return new EventReminderEntity(eventReminder.getId(),
                                   eventReminder.getReceiverId(),
                                   eventReminder.getBefore(),
                                   eventReminder.getBeforePeriodType() == null ? null
                                                                               : eventReminder.getBeforePeriodType().name(),
                                   AgendaDateUtils.toRFC3339Date(eventReminder.getDatetime()));
  }

  public static final EventSearchResultEntity fromSearchEvent(AgendaCalendarService agendaCalendarService,
                                                              AgendaEventService agendaEventService,
                                                              IdentityManager identityManager,
                                                              EventSearchResult eventSearchResult) {
    EventSearchResultEntity eventSearchResultEntity = (EventSearchResultEntity) fromEvent(agendaCalendarService,
                                                                                          agendaEventService,
                                                                                          identityManager,
                                                                                          eventSearchResult,
                                                                                          true);
    eventSearchResultEntity.setExcerpts(eventSearchResult.getExcerpts());
    return eventSearchResultEntity;
  }

  public static final EventEntity fromEvent(AgendaCalendarService agendaCalendarService,
                                            AgendaEventService agendaEventService,
                                            IdentityManager identityManager,
                                            Event event) {
    return fromEvent(agendaCalendarService, agendaEventService, identityManager, event, false);
  }

  private static final EventEntity fromEvent(AgendaCalendarService agendaCalendarService,
                                             AgendaEventService agendaEventService,
                                             IdentityManager identityManager,
                                             Event event,
                                             boolean isSearch) {
    EventRecurrence recurrence = event.getRecurrence();
    EventRecurrenceEntity recurrenceEntity = null;
    if (recurrence != null) {
      recurrenceEntity = new EventRecurrenceEntity(recurrence.getId(),
                                                   AgendaDateUtils.toRFC3339Date(recurrence.getUntil()),
                                                   recurrence.getCount(),
                                                   recurrence.getType(),
                                                   recurrence.getFrequency(),
                                                   recurrence.getInterval(),
                                                   recurrence.getBySecond(),
                                                   recurrence.getByMinute(),
                                                   recurrence.getByHour(),
                                                   recurrence.getByDay(),
                                                   recurrence.getByMonthDay(),
                                                   recurrence.getByYearDay(),
                                                   recurrence.getByWeekNo(),
                                                   recurrence.getByMonth(),
                                                   recurrence.getBySetPos());
    }
    EventOccurrence occurrence = event.getOccurrence();
    EventOccurrenceEntity occurrenceEntity = null;
    if (occurrence != null) {
      occurrenceEntity = new EventOccurrenceEntity(AgendaDateUtils.toRFC3339Date(occurrence.getId()), occurrence.isExceptional());
    }
    long parentId = event.getParentId();
    EventEntity parentEvent = null;
    if (parentId > 0 && parentId != event.getId()) {
      parentEvent = getEventEntity(agendaCalendarService, agendaEventService, identityManager, parentId);
    }

    if (isSearch) {
      return new EventSearchResultEntity(event.getId(),
                                         parentEvent,
                                         event.getRemoteId(),
                                         event.getRemoteProviderId(),
                                         getCalendarEntity(agendaCalendarService, identityManager, event.getCalendarId()),
                                         getIdentityEntity(identityManager, event.getCreatorId()),
                                         AgendaDateUtils.toRFC3339Date(event.getCreated()),
                                         AgendaDateUtils.toRFC3339Date(event.getUpdated()),
                                         event.getSummary(),
                                         event.getDescription(),
                                         event.getLocation(),
                                         event.getColor(),
                                         AgendaDateUtils.toRFC3339Date(event.getStart(), event.isAllDay()),
                                         AgendaDateUtils.toRFC3339Date(event.getEnd(), event.isAllDay()),
                                         event.isAllDay(),
                                         event.getAvailability(),
                                         event.getStatus(),
                                         recurrenceEntity,
                                         occurrenceEntity,
                                         event.getAcl(),
                                         null,
                                         null,
                                         null,
                                         null,
                                         event.isAllowAttendeeToUpdate(),
                                         event.isAllowAttendeeToInvite(),
                                         false,
                                         null);
    } else {
      return new EventEntity(event.getId(),
                             parentEvent,
                             event.getRemoteId(),
                             event.getRemoteProviderId(),
                             getCalendarEntity(agendaCalendarService, identityManager, event.getCalendarId()),
                             getIdentityEntity(identityManager, event.getCreatorId()),
                             AgendaDateUtils.toRFC3339Date(event.getCreated()),
                             AgendaDateUtils.toRFC3339Date(event.getUpdated()),
                             event.getSummary(),
                             event.getDescription(),
                             event.getLocation(),
                             event.getColor(),
                             AgendaDateUtils.toRFC3339Date(event.getStart(), event.isAllDay()),
                             AgendaDateUtils.toRFC3339Date(event.getEnd(), event.isAllDay()),
                             event.isAllDay(),
                             event.getAvailability(),
                             event.getStatus(),
                             recurrenceEntity,
                             occurrenceEntity,
                             event.getAcl(),
                             null,
                             null,
                             null,
                             null,
                             event.isAllowAttendeeToUpdate(),
                             event.isAllowAttendeeToInvite(),
                             false);
    }
  }

  private static CalendarEntity getCalendarEntity(AgendaCalendarService agendaCalendarService,
                                                  IdentityManager identityManager,
                                                  long calendarId) {
    if (calendarId <= 0) {
      return null;
    }
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    return fromCalendar(identityManager, calendar);
  }

  private static EventEntity getEventEntity(AgendaCalendarService agendaCalendarService,
                                            AgendaEventService agendaEventService,
                                            IdentityManager identityManager,
                                            long eventId) {
    if (eventId <= 0) {
      return null;
    }
    Event event = agendaEventService.getEventById(eventId);
    return fromEvent(agendaCalendarService, agendaEventService, identityManager, event);
  }

  private static IdentityEntity getIdentityEntity(IdentityManager identityManager, long ownerId) {
    Identity identity = getIdentity(identityManager, ownerId);
    if (identity == null) {
      return null;
    }
    return org.exoplatform.social.rest.api.EntityBuilder.buildEntityIdentity(identity, IDENTITIES_REST_PATH, IDENTITIES_EXPAND);
  }

  private static final Identity getIdentity(IdentityManager identityManager, long identityId) {
    return identityManager.getIdentity(String.valueOf(identityId));
  }

}
