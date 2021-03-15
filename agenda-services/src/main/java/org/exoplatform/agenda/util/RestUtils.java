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
import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

public class RestUtils {

  private RestUtils() {
  }

  public static final String getCurrentUser() {
    return ConversationState.getCurrent().getIdentity().getUserId();
  }

  public static final Identity getCurrentUserIdentity(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
  }

  public static final long getCurrentUserIdentityId(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  public static String getBaseRestURI() {
    return getBasePortalURI() + "/" + PortalContainer.getCurrentRestContextName();
  }

  public static String getBasePortalURI() {
    return "/" + PortalContainer.getCurrentPortalContainerName();
  }

  public static String getIdentityId(IdentityEntity identityEntity, IdentityManager identityManager) {
    if (identityEntity == null) {
      return null;
    }
    String identityIdString = identityEntity.getId();
    String remoteId = identityEntity.getRemoteId();
    String providerId = identityEntity.getProviderId();

    if (StringUtils.isNotBlank(identityIdString)) {
      Identity identity = identityManager.getIdentity(identityIdString);
      if (identity == null) {
        // Wrong id, attempt with remoteId and providerId
        identityIdString = null;
      }
    }
    if (StringUtils.isBlank(identityIdString) && StringUtils.isNotBlank(remoteId) && StringUtils.isNotBlank(providerId)) {
      Identity identity = identityManager.getOrCreateIdentity(providerId, remoteId);
      if (identity != null) {
        identityIdString = identity.getId();
      }
    }
    return identityIdString;
  }

  public static Integer getIntegerValue(UriInfo uriInfo, String name) {
    String value = getQueryParam(uriInfo, name);
    if (value == null)
      return null;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static String getQueryParam(UriInfo uriInfo, String name) {
    return uriInfo.getQueryParameters().getFirst(name);
  }

  public static Event createEventEntity(IdentityManager identityManager,
                                        AgendaCalendarService agendaCalendarService,
                                        AgendaEventService agendaEventService,
                                        EventEntity eventEntity,
                                        long userIdentityId,
                                        String timeZoneId) throws AgendaException,
                                                           IllegalAccessException {
    checkCalendar(identityManager, agendaCalendarService, eventEntity);

    cleanupAttachedEntitiesIds(eventEntity);

    List<EventAttendeeEntity> attendeeEntities = eventEntity.getAttendees();
    List<EventAttendee> attendees = null;
    if (attendeeEntities != null && !attendeeEntities.isEmpty()) {
      attendees = new ArrayList<>();
      for (EventAttendeeEntity attendeeEntity : attendeeEntities) {
        IdentityEntity attendeeIdentity = attendeeEntity.getIdentity();
        String attendeeIdString = RestUtils.getIdentityId(attendeeIdentity, identityManager);
        if (StringUtils.isBlank(attendeeIdString)) {
          throw new AgendaException(AgendaExceptionType.ATTENDEE_IDENTITY_NOT_FOUND);
        }
        attendeeIdentity.setId(attendeeIdString);
        attendees.add(RestEntityBuilder.toEventAttendee(identityManager, eventEntity.getId(), attendeeEntity));
      }
    }

    List<EventReminderEntity> reminderEntities = eventEntity.getReminders();
    List<EventReminder> reminders = null;
    if (reminderEntities != null && !reminderEntities.isEmpty()) {
      reminders = new ArrayList<>();
      for (EventReminderEntity reminderEntity : reminderEntities) {
        reminders.add(RestEntityBuilder.toEventReminder(eventEntity.getId(), reminderEntity));
      }
    }

    RemoteEvent remoteEvent = getRemoteEvent(eventEntity, userIdentityId);

    String userTimeZoneId = timeZoneId == null ? eventEntity.getTimeZoneId() : timeZoneId;
    ZoneId userTimeZone = userTimeZoneId == null ? ZoneOffset.UTC : ZoneId.of(userTimeZoneId);

    List<EventDateOptionEntity> dateOptionEntities = eventEntity.getDateOptions();
    List<EventDateOption> dateOptions = dateOptionEntities == null ? Collections.emptyList()
                                                                   : dateOptionEntities.stream()
                                                                                       .map(dateOptionEntity -> RestEntityBuilder.toEventDateOption(dateOptionEntity,
                                                                                                                                                    userTimeZone))
                                                                                       .collect(Collectors.toList());

    return agendaEventService.createEvent(RestEntityBuilder.toEvent(eventEntity),
                                          attendees,
                                          eventEntity.getConferences(),
                                          reminders,
                                          dateOptions,
                                          remoteEvent,
                                          eventEntity.isSendInvitation(),
                                          userIdentityId);
  }

  public static void checkCalendar(IdentityManager identityManager,
                                   AgendaCalendarService agendaCalendarService,
                                   EventEntity eventEntity) throws AgendaException {
    IdentityEntity identityEntity = eventEntity.getCalendar().getOwner();

    String ownerIdString = RestUtils.getIdentityId(identityEntity, identityManager);
    if (StringUtils.isBlank(ownerIdString)) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND);
    }
    identityEntity.setId(ownerIdString);
    Calendar calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(ownerIdString));
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    } else if (eventEntity.getCalendar() == null) {
      eventEntity.setCalendar(RestEntityBuilder.fromCalendar(identityManager, calendar));
    } else {
      eventEntity.getCalendar().setId(calendar.getId());
    }
  }

  public static EventEntity getEventByIdAndUser(IdentityManager identityManager,
                                                AgendaCalendarService agendaCalendarService,
                                                AgendaEventService agendaEventService,
                                                AgendaRemoteEventService agendaRemoteEventService,
                                                AgendaEventDatePollService agendaEventDatePollService,
                                                AgendaEventReminderService agendaEventReminderService,
                                                AgendaEventConferenceService agendaEventConferenceService,
                                                AgendaEventAttendeeService agendaEventAttendeeService,
                                                long eventId,
                                                long identityId,
                                                ZonedDateTime occurrenceId,
                                                ZoneId userTimeZone,
                                                List<String> expandProperties) throws IllegalAccessException {
    Event event = agendaEventService.getEventById(eventId, userTimeZone, identityId);
    return getEventEntity(identityManager,
                          agendaCalendarService,
                          agendaEventService,
                          agendaRemoteEventService,
                          agendaEventDatePollService,
                          agendaEventReminderService,
                          agendaEventConferenceService,
                          agendaEventAttendeeService,
                          event,
                          occurrenceId,
                          userTimeZone,
                          expandProperties);
  }

  public static EventEntity getEventEntity(IdentityManager identityManager,
                                           AgendaCalendarService agendaCalendarService,
                                           AgendaEventService agendaEventService,
                                           AgendaRemoteEventService agendaRemoteEventService,
                                           AgendaEventDatePollService agendaEventDatePollService,
                                           AgendaEventReminderService agendaEventReminderService,
                                           AgendaEventConferenceService agendaEventConferenceService,
                                           AgendaEventAttendeeService agendaEventAttendeeService,
                                           Event event,
                                           ZonedDateTime occurrenceId,
                                           ZoneId userTimeZone,
                                           List<String> expandProperties) {
    if (event == null) {
      return null;
    } else {
      if (userTimeZone == null) {
        if (event.getTimeZoneId() == null) {
          userTimeZone = ZoneOffset.UTC;
        } else {
          userTimeZone = event.getTimeZoneId();
        }
      }
      EventEntity eventEntity = RestEntityBuilder.fromEvent(agendaCalendarService,
                                                            agendaEventService,
                                                            identityManager,
                                                            event,
                                                            userTimeZone);
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
        fillAttendees(identityManager, agendaEventAttendeeService, eventEntity, occurrenceId, 0);
      } else if (expandProperties.contains("response")) {
        fillAttendees(identityManager, agendaEventAttendeeService, eventEntity, occurrenceId, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
        fillConferences(agendaEventConferenceService, eventEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
        fillReminders(agendaEventReminderService, eventEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
        fillDateOptions(agendaEventDatePollService, eventEntity, userTimeZone);
      }
      fillRemoteEvent(agendaRemoteEventService, eventEntity, userIdentityId);
      boolean isComputedOccurrence = isComputedOccurrence(eventEntity);
      EventEntity parentEventEntity = eventEntity.getParent();
      if (parentEventEntity != null) {
        fillRemoteEvent(agendaRemoteEventService, parentEventEntity, userIdentityId);

        if (expandProperties.contains("parentAll") && !isComputedOccurrence) {
          boolean isEventAttendee = agendaEventAttendeeService.isEventAttendee(parentEventEntity.getId(), userIdentityId);
          boolean canUpdateEvent = isEventAttendee && eventEntity.getAcl().isCanEdit();
          parentEventEntity.setAcl(new EventPermission(canUpdateEvent, isEventAttendee));
        }
      }
      if (isComputedOccurrence) {
        cleanupAttachedEntitiesIds(eventEntity);
      }
      return eventEntity;
    }
  }

  public static EventSearchResultEntity getEventSearchResultEntity(IdentityManager identityManager,
                                                                   AgendaCalendarService agendaCalendarService,
                                                                   AgendaEventService agendaEventService,
                                                                   AgendaRemoteEventService agendaRemoteEventService,
                                                                   AgendaEventDatePollService agendaEventDatePollService,
                                                                   AgendaEventReminderService agendaEventReminderService,
                                                                   AgendaEventConferenceService agendaEventConferenceService,
                                                                   AgendaEventAttendeeService agendaEventAttendeeService,
                                                                   EventSearchResult eventSearchResult,
                                                                   ZonedDateTime occurrenceId,
                                                                   ZoneId userTimeZone,
                                                                   List<String> expandProperties) {
    if (eventSearchResult == null) {
      return null;
    } else {
      EventSearchResultEntity eventSearchResultEntity = RestEntityBuilder.fromSearchEvent(agendaCalendarService,
                                                                                          agendaEventService,
                                                                                          identityManager,
                                                                                          eventSearchResult,
                                                                                          userTimeZone);

      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
        fillAttendees(identityManager, agendaEventAttendeeService, eventSearchResultEntity, occurrenceId, 0);
      } else if (expandProperties.contains("response")) {
        fillAttendees(identityManager, agendaEventAttendeeService, eventSearchResultEntity, occurrenceId, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
        fillConferences(agendaEventConferenceService, eventSearchResultEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
        fillReminders(agendaEventReminderService, eventSearchResultEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
        fillDateOptions(agendaEventDatePollService, eventSearchResultEntity, userTimeZone);
      }
      fillRemoteEvent(agendaRemoteEventService, eventSearchResultEntity, userIdentityId);
      if (eventSearchResultEntity.getParent() != null) {
        fillRemoteEvent(agendaRemoteEventService, eventSearchResultEntity.getParent(), userIdentityId);
      }
      if (isComputedOccurrence(eventSearchResultEntity)) {
        cleanupAttachedEntitiesIds(eventSearchResultEntity);
      }
      return eventSearchResultEntity;
    }
  }

  public static void fillAttendees(IdentityManager identityManager,
                                   AgendaEventAttendeeService agendaEventAttendeeService,
                                   EventEntity eventEntity,
                                   ZonedDateTime occurrenceId,
                                   Map<Long, EventAttendeeList> attendeesByParentEventId,
                                   long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    if (computedOccurrence && attendeesByParentEventId.containsKey(eventId)) {
      EventAttendeeList eventAttendeeList = attendeesByParentEventId.get(eventId);
      List<EventAttendee> eventAttendees = eventAttendeeList.getEventAttendees(occurrenceId);
      eventEntity.setAttendees(eventAttendees.stream()
                                             .map(eventAttendee -> RestEntityBuilder.fromEventAttendee(identityManager,
                                                                                                       eventAttendee))
                                             .collect(Collectors.toList()));
    } else {
      EventAttendeeList eventAttendeeList = fillAttendees(identityManager,
                                                          agendaEventAttendeeService,
                                                          eventEntity,
                                                          occurrenceId,
                                                          userIdentityId);
      if (eventEntity.getRecurrence() != null) {
        attendeesByParentEventId.put(eventId, eventAttendeeList);
      }
    }
  }

  private static EventAttendeeList fillAttendees(IdentityManager identityManager,
                                                 AgendaEventAttendeeService agendaEventAttendeeService,
                                                 EventEntity eventEntity,
                                                 ZonedDateTime occurrenceId,
                                                 long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    EventAttendeeList eventAttendeeList = agendaEventAttendeeService.getEventAttendees(eventId);
    if (userIdentityId > 0) {
      EventAttendee eventAttendee = eventAttendeeList.getEventAttendee(userIdentityId, occurrenceId);
      if (eventAttendee == null) {
        eventEntity.setAttendees(Collections.emptyList());
      } else {
        eventEntity.setAttendees(Collections.singletonList(RestEntityBuilder.fromEventAttendee(identityManager,
                                                                                               eventAttendee)));
      }
    } else {
      List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeList.getEventAttendees(occurrenceId)
                                                                         .stream()
                                                                         .map(eventAttendee -> RestEntityBuilder.fromEventAttendee(identityManager,
                                                                                                                                   eventAttendee))
                                                                         .collect(Collectors.toList());
      eventEntity.setAttendees(eventAttendeeEntities);
    }
    return eventAttendeeList;
  }

  public static void fillConferences(AgendaEventConferenceService agendaEventConferenceService,
                                     EventEntity eventEntity,
                                     Map<Long, List<EventConference>> conferencesByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (conferencesByParentEventId.containsKey(eventId)) {
      eventEntity.setConferences(conferencesByParentEventId.get(eventId));
    } else {
      fillConferences(agendaEventConferenceService, eventEntity);
      conferencesByParentEventId.put(eventId, eventEntity.getConferences());
    }
  }

  private static void fillConferences(AgendaEventConferenceService agendaEventConferenceService,
                                      EventEntity eventEntity) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    eventEntity.setConferences(eventConferences);
  }

  public static void fillReminders(AgendaEventReminderService agendaEventReminderService,
                                   EventEntity eventEntity,
                                   long userIdentityId,
                                   Map<Long, List<EventReminder>> remindersByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (remindersByParentEventId.containsKey(eventId)) {
      List<EventReminder> reminders = remindersByParentEventId.get(eventId);
      ZonedDateTime occurrenceId =
                                 AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getOccurrence().getId(), ZoneOffset.UTC);
      reminders = reminders.stream()
                           .filter(reminder -> (reminder.getFromOccurrenceId() == null
                               || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                               || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                               && (reminder.getUntilOccurrenceId() == null
                                   || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                           .collect(Collectors.toList());
      eventEntity.setReminders(reminders.stream().map(RestEntityBuilder::fromEventReminder).collect(Collectors.toList()));
    } else {
      fillReminders(agendaEventReminderService, eventEntity, userIdentityId);
      remindersByParentEventId.put(eventId,
                                   eventEntity.getReminders()
                                              .stream()
                                              .map(reminderEntity -> RestEntityBuilder.toEventReminder(eventId, reminderEntity))
                                              .collect(Collectors.toList()));
    }
  }

  public static void fillDateOptions(AgendaEventDatePollService agendaEventDatePollService,
                                     EventEntity eventEntity,
                                     ZoneId userTimeZone,
                                     Map<Long, List<EventDateOptionEntity>> dateOptionsByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (dateOptionsByParentEventId.containsKey(eventId)) {
      eventEntity.setDateOptions(dateOptionsByParentEventId.get(eventId));
    } else {
      fillDateOptions(agendaEventDatePollService, eventEntity, userTimeZone);
      dateOptionsByParentEventId.put(eventId, eventEntity.getDateOptions());
    }
  }

  public static void fillRemoteEvent(AgendaRemoteEventService agendaRemoteEventService,
                                     EventEntity eventEntity,
                                     long userIdentityId) {
    if (isComputedOccurrence(eventEntity)) {
      return;
    }

    long eventId = eventEntity.getId();
    RemoteEvent remoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId);
    if (remoteEvent != null) {
      eventEntity.setRemoteId(remoteEvent.getRemoteId());
      eventEntity.setRemoteProviderId(remoteEvent.getRemoteProviderId());
      eventEntity.setRemoteProviderName(remoteEvent.getRemoteProviderName());
    }
  }

  public static void fillRemoteEvent(AgendaRemoteEventService agendaRemoteEventService,
                                     EventEntity eventEntity,
                                     long userIdentityId,
                                     Map<Long, RemoteEvent> remoteEventByParentEventId) {
    if (isComputedOccurrence(eventEntity)) {
      return;
    }

    long eventId = eventEntity.getId();
    RemoteEvent remoteEvent = null;
    if (remoteEventByParentEventId.containsKey(eventId)) {
      remoteEvent = remoteEventByParentEventId.get(eventId);
    } else {
      remoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId);
      remoteEventByParentEventId.put(eventId, remoteEvent);
    }
    if (remoteEvent != null) {
      eventEntity.setRemoteId(remoteEvent.getRemoteId());
      eventEntity.setRemoteProviderId(remoteEvent.getRemoteProviderId());
      eventEntity.setRemoteProviderName(remoteEvent.getRemoteProviderName());
    }
  }

  public static RemoteEvent getRemoteEvent(EventEntity eventEntity, long userIdentityId) {
    return new RemoteEvent(0l,
                           eventEntity.getId(),
                           userIdentityId,
                           eventEntity.getRemoteId(),
                           eventEntity.getRemoteProviderId(),
                           eventEntity.getRemoteProviderName());

  }

  public static void fillReminders(AgendaEventReminderService agendaEventReminderService,
                                   EventEntity eventEntity,
                                   long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    if (computedOccurrence) {
      ZonedDateTime occurrenceId =
                                 AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getOccurrence().getId(), ZoneOffset.UTC);
      reminders = reminders.stream()
                           .filter(reminder -> (reminder.getFromOccurrenceId() == null
                               || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                               || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                               && (reminder.getUntilOccurrenceId() == null
                                   || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                           .collect(Collectors.toList());
    }
    List<EventReminderEntity> eventReminderEntities = reminders == null ? null
                                                                        : reminders.stream()
                                                                                   .map(RestEntityBuilder::fromEventReminder)
                                                                                   .collect(Collectors.toList());
    eventEntity.setReminders(eventReminderEntities);
  }

  private static void fillDateOptions(AgendaEventDatePollService agendaEventDatePollService,
                                      EventEntity eventEntity,
                                      ZoneId userTimeZone) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, userTimeZone);
    List<EventDateOptionEntity> dateOptionEntities = dateOptions == null ? Collections.emptyList()
                                                                         : dateOptions.stream()
                                                                                      .map(dateOption -> RestEntityBuilder.fromEventDateOption(userTimeZone,
                                                                                                                                               dateOption))
                                                                                      .collect(Collectors.toList());
    eventEntity.setDateOptions(dateOptionEntities);
  }

  public static boolean isComputedOccurrence(EventEntity eventEntity) {
    return eventEntity.getId() == 0 && eventEntity.getParent() != null;
  }

  public static void cleanupAttachedEntitiesIds(EventEntity eventEntity) {
    List<EventAttendeeEntity> attendees = eventEntity.getAttendees();
    if (attendees != null && !attendees.isEmpty()) {
      attendees = attendees.stream().map(attendee -> {
        attendee = attendee.clone();
        attendee.setId(0);
        return attendee;
      }).collect(Collectors.toList());
      eventEntity.setAttendees(attendees);
    }
    List<EventConference> conferences = eventEntity.getConferences();
    if (conferences != null && !conferences.isEmpty()) {
      conferences = conferences.stream().map(conference -> {
        conference = conference.clone();
        conference.setId(0);
        return conference;
      }).collect(Collectors.toList());
      eventEntity.setConferences(conferences);
    }
    List<EventReminderEntity> reminders = eventEntity.getReminders();
    if (reminders != null && !reminders.isEmpty()) {
      reminders = reminders.stream().map(reminder -> {
        reminder = reminder.clone();
        reminder.setId(0);
        return reminder;
      }).collect(Collectors.toList());
      eventEntity.setReminders(reminders);
    }
  }

}
