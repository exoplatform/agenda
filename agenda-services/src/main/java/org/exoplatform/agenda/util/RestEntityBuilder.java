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

import java.time.*;

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

public class RestEntityBuilder {

  private static final String IDENTITIES_REST_PATH = "/v1/social/identities"; // NOSONAR

  private static final String IDENTITIES_EXPAND    = "all";

  private RestEntityBuilder() {
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

    ZoneId eventZoneId = ZoneId.of(eventEntity.getTimeZoneId());

    if (recurrenceEntity != null) {
      ZonedDateTime untilDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(recurrenceEntity.getUntil(),
                                                                            eventZoneId,
                                                                            false);
      recurrence = new EventRecurrence(recurrenceEntity.getId(),
                                       untilDate == null ? null : untilDate.withZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
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
      occurrence = new EventOccurrence(AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceEntity.getId(), ZoneOffset.UTC));
    }

    ZonedDateTime startDate =
                            eventEntity.getStart() == null ? null
                                                           : eventEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(eventEntity.getStart())
                                                                                    : AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getStart(),
                                                                                                                                  eventZoneId,
                                                                                                                                  false);
    ZonedDateTime endDate = eventEntity.getEnd() == null ? null
                                                         : eventEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(eventEntity.getEnd())
                                                                                  : AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getEnd(),
                                                                                                                                eventZoneId,
                                                                                                                                false);
    return new Event(eventEntity.getId(),
                     eventEntity.getParent() == null ? 0l : eventEntity.getParent().getId(),
                     eventEntity.getCalendar() == null ? 0l : eventEntity.getCalendar().getId(),
                     eventEntity.getCreator() == null ? 0l : Long.parseLong(eventEntity.getCreator().getId()),
                     0l,
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getCreated(), ZoneOffset.UTC),
                     AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getUpdated(), ZoneOffset.UTC),
                     eventEntity.getSummary(),
                     eventEntity.getDescription(),
                     eventEntity.getLocation(),
                     eventEntity.getColor(),
                     eventZoneId,
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

  public static EventDateOption toEventDateOption(EventDateOptionEntity dateOptionEntity, ZoneId userTimeZone) {
    ZonedDateTime startDate =
                            dateOptionEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(dateOptionEntity.getStart())
                                                        : AgendaDateUtils.parseRFC3339ToZonedDateTime(dateOptionEntity.getStart(),
                                                                                                      userTimeZone,
                                                                                                      false);
    ZonedDateTime endDate =
                          dateOptionEntity.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(dateOptionEntity.getEnd())
                                                      : AgendaDateUtils.parseRFC3339ToZonedDateTime(dateOptionEntity.getEnd(),
                                                                                                    userTimeZone,
                                                                                                    false);

    return new EventDateOption(dateOptionEntity.getId(),
                               dateOptionEntity.getEventId(),
                               startDate,
                               endDate,
                               dateOptionEntity.isAllDay(),
                               false,
                               null);
  }

  public static EventReminder toEventReminder(long eventId, EventReminderEntity eventReminderEntity) {
    String beforePeriodTypeName = eventReminderEntity.getBeforePeriodType();
    ReminderPeriodType beforePeriodType = null;
    if (StringUtils.isNotBlank(beforePeriodTypeName)) {
      beforePeriodType = ReminderPeriodType.valueOf(beforePeriodTypeName.toUpperCase());
    }
    return new EventReminder(eventReminderEntity.getId(),
                             eventId,
                             0,
                             eventReminderEntity.getBefore(),
                             beforePeriodType);
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

  public static EventDateOptionEntity fromEventDateOption(ZoneId userTimeZone, EventDateOption dateOption) {
    return new EventDateOptionEntity(dateOption.getId(),
                                     dateOption.getEventId(),
                                     AgendaDateUtils.toRFC3339Date(dateOption.getStart(),
                                                                   userTimeZone,
                                                                   dateOption.isAllDay()),
                                     AgendaDateUtils.toRFC3339Date(dateOption.getEnd(),
                                                                   userTimeZone,
                                                                   dateOption.isAllDay()),
                                     dateOption.isAllDay(),
                                     dateOption.isSelected(),
                                     dateOption.getVoters());
  }

  public static final EventReminderEntity fromEventReminder(EventReminder eventReminder) {
    return new EventReminderEntity(eventReminder.getId(),
                                   eventReminder.getBefore(),
                                   eventReminder.getBeforePeriodType() == null ? null
                                                                               : eventReminder.getBeforePeriodType().name());
  }

  public static final EventSearchResultEntity fromSearchEvent(AgendaCalendarService agendaCalendarService,
                                                              AgendaEventService agendaEventService,
                                                              IdentityManager identityManager,
                                                              EventSearchResult eventSearchResult,
                                                              ZoneId userTimeZone) {
    EventSearchResultEntity eventSearchResultEntity = (EventSearchResultEntity) fromEvent(agendaCalendarService,
                                                                                          agendaEventService,
                                                                                          identityManager,
                                                                                          eventSearchResult,
                                                                                          userTimeZone,
                                                                                          true);
    eventSearchResultEntity.setExcerpts(eventSearchResult.getExcerpts());
    return eventSearchResultEntity;
  }

  public static final EventEntity fromEvent(AgendaCalendarService agendaCalendarService,
                                            AgendaEventService agendaEventService,
                                            IdentityManager identityManager,
                                            Event event,
                                            ZoneId userTimeZone) {
    return fromEvent(agendaCalendarService, agendaEventService, identityManager, event, userTimeZone, false);
  }

  private static final EventEntity fromEvent(AgendaCalendarService agendaCalendarService,
                                             AgendaEventService agendaEventService,
                                             IdentityManager identityManager,
                                             Event event,
                                             ZoneId userTimeZone,
                                             boolean isSearch) {
    EventRecurrence recurrence = event.getRecurrence();
    EventRecurrenceEntity recurrenceEntity = null;
    if (userTimeZone == null) {
      userTimeZone = ZoneOffset.UTC;
    }
    if (recurrence != null) {
      LocalDate until = recurrence.getUntil();
      String untilDateRFC3339 = until == null ? null
                                              : AgendaDateUtils.toRFC3339Date(until.plusDays(1)
                                                                                   .atStartOfDay(userTimeZone)
                                                                                   .minusSeconds(1));
      recurrenceEntity = new EventRecurrenceEntity(recurrence.getId(),
                                                   untilDateRFC3339,
                                                   recurrence.getCount(),
                                                   recurrence.getType(),
                                                   recurrence.getFrequency(),
                                                   recurrence.getInterval(),
                                                   recurrence.getRrule(),
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
      parentEvent = getEventEntity(agendaCalendarService, agendaEventService, identityManager, parentId, userTimeZone);
    }

    if (isSearch) {
      return new EventSearchResultEntity(event.getId(),
                                         parentEvent,
                                         null,
                                         0l,
                                         null,
                                         getCalendarEntity(agendaCalendarService, identityManager, event.getCalendarId()),
                                         getIdentityEntity(identityManager, event.getCreatorId()),
                                         AgendaDateUtils.toRFC3339Date(event.getCreated()),
                                         AgendaDateUtils.toRFC3339Date(event.getUpdated()),
                                         event.getSummary(),
                                         event.getDescription(),
                                         event.getLocation(),
                                         event.getColor(),
                                         null,
                                         AgendaDateUtils.toRFC3339Date(event.getStart(), userTimeZone, event.isAllDay()),
                                         AgendaDateUtils.toRFC3339Date(event.getEnd(), userTimeZone, event.isAllDay()),
                                         event.isAllDay(),
                                         event.getAvailability(),
                                         event.getStatus(),
                                         recurrenceEntity,
                                         occurrenceEntity,
                                         event.getAcl(),
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
                             null,
                             0l,
                             null,
                             getCalendarEntity(agendaCalendarService, identityManager, event.getCalendarId()),
                             getIdentityEntity(identityManager, event.getCreatorId()),
                             AgendaDateUtils.toRFC3339Date(event.getCreated()),
                             AgendaDateUtils.toRFC3339Date(event.getUpdated()),
                             event.getSummary(),
                             event.getDescription(),
                             event.getLocation(),
                             event.getColor(),
                             userTimeZone.getId(),
                             AgendaDateUtils.toRFC3339Date(event.getStart(), userTimeZone, event.isAllDay()),
                             AgendaDateUtils.toRFC3339Date(event.getEnd(), userTimeZone, event.isAllDay()),
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
                                            long eventId,
                                            ZoneId userTimeZone) {
    if (eventId <= 0) {
      return null;
    }
    Event event = agendaEventService.getEventById(eventId);
    return fromEvent(agendaCalendarService, agendaEventService, identityManager, event, userTimeZone);
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
