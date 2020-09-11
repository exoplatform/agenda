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
package org.exoplatform.agenda.service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventServiceImpl implements AgendaEventService {

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventAttendeeService   attendeeService;

  private AgendaEventAttachmentService attachmentService;

  private AgendaEventConferenceService conferenceService;

  private AgendaEventReminderService   reminderService;

  private AgendaEventStorage           agendaEventStorage;

  private IdentityManager              identityManager;

  private SpaceService                 spaceService;

  public AgendaEventServiceImpl(AgendaCalendarService agendaCalendarService,
                                AgendaEventAttendeeService attendeeService,
                                AgendaEventAttachmentService attachmentService,
                                AgendaEventConferenceService conferenceService,
                                AgendaEventReminderService reminderService,
                                AgendaEventStorage agendaEventStorage,
                                IdentityManager identityManager,
                                SpaceService spaceService) {
    this.agendaCalendarService = agendaCalendarService;
    this.attendeeService = attendeeService;
    this.attachmentService = attachmentService;
    this.conferenceService = conferenceService;
    this.reminderService = reminderService;
    this.agendaEventStorage = agendaEventStorage;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventById(long eventId, String username) throws IllegalAccessException {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      return null;
    }
    if (canAccessEvent(event, username)) {
      TimeZone userTimezone = AgendaDateUtils.getUserTimezone(username);
      adjustEventDatesForRead(event, userTimezone);
      event.setAcl(new Permission(canUpdateEvent(event, username)));
      return event;
    } else {
      throw new IllegalAccessException("User " + username + "is not allowed to access event with id " + eventId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventById(long eventId) {
    return agendaEventStorage.getEventById(eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event createEvent(Event event,
                           List<EventAttendee> attendees,
                           List<EventConference> conferences,
                           List<EventAttachment> attachments,
                           List<EventReminder> reminders,
                           boolean sendInvitation,
                           String username) throws IllegalAccessException, AgendaException {
    if (StringUtils.isBlank(username)) {
      throw new IllegalArgumentException("username is null");
    }
    if (event == null) {
      throw new IllegalArgumentException("Event is null");
    }
    if (event.getId() > 0) {
      throw new IllegalArgumentException("Event id must be null");
    }
    if (event.getStart() == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_MANDATORY);
    }
    if (event.getEnd() == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_END_DATE_MANDATORY);
    }
    if (event.getStart().isAfter(event.getEnd())) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_BEFORE_END_DATE);
    }
    if (event.getAvailability() == null) {
      event.setAvailability(EventAvailability.DEFAULT);
    }
    if (event.getStatus() == null) {
      event.setStatus(EventStatus.CONFIRMED);
    }
    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence != null) {
      if (recurrence.getFrequency() == null) {
        throw new AgendaException(AgendaExceptionType.EVENT_RECURRENCE_FREQUENCY_MANDATORY);
      }
      if (recurrence.getInterval() <= 0) {
        throw new AgendaException(AgendaExceptionType.EVENT_RECURRENCE_INTERVAL_MANDATORY);
      }
    }

    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId, username);
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    }

    boolean canCreateCalendarEvents = canCreateEvent(calendar, username);
    if (!canCreateCalendarEvents) {
      throw new IllegalAccessException("User '" + username + "' can't create an event in calendar " + calendar.getTitle());
    }

    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + username + "' doesn't exist");
    }
    long userIdentityId = Long.parseLong(userIdentity.getId());

    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
      recurrence = null;
      event.setRecurrence(null);
    }

    adjustEventDatesForWrite(event);

    Event eventToCreate = new Event(0,
                                    event.getParentId(),
                                    event.getRemoteId(),
                                    event.getRemoteProviderId(),
                                    calendarId,
                                    userIdentityId,
                                    userIdentityId,
                                    ZonedDateTime.now(),
                                    null,
                                    event.getSummary(),
                                    event.getDescription(),
                                    event.getLocation(),
                                    event.getColor(),
                                    event.getStart(),
                                    event.getEnd(),
                                    event.isAllDay(),
                                    event.getAvailability(),
                                    event.getStatus(),
                                    recurrence,
                                    occurrence,
                                    null,
                                    event.isAllowAttendeeToUpdate(),
                                    event.isAllowAttendeeToInvite());

    Event createdEvent = agendaEventStorage.createEvent(eventToCreate);
    long eventId = createdEvent.getId();
    attachmentService.saveEventAttachments(eventId, attachments, userIdentityId);
    conferenceService.saveEventConferences(eventId, conferences);
    reminderService.saveEventReminders(createdEvent, reminders, userIdentityId);
    attendeeService.saveEventAttendees(createdEvent, attendees, userIdentityId, sendInvitation, false);

    return getEventById(createdEvent.getId(), username);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event updateEvent(Event event,
                           List<EventAttendee> attendees,
                           List<EventConference> conferences,
                           List<EventAttachment> attachments,
                           List<EventReminder> reminders,
                           boolean sendInvitation,
                           String username) throws AgendaException, IllegalAccessException {
    if (StringUtils.isBlank(username)) {
      throw new IllegalArgumentException("username is null");
    }
    if (event == null) {
      throw new IllegalArgumentException("Event is null");
    }
    if (event.getId() <= 0) {
      throw new IllegalArgumentException("Event id must not be null");
    }
    if (event.getStart() == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_MANDATORY);
    }
    if (event.getEnd() == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_END_DATE_MANDATORY);
    }
    if (event.getStart().isAfter(event.getEnd())) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_BEFORE_END_DATE);
    }
    if (event.getAvailability() == null) {
      event.setAvailability(EventAvailability.DEFAULT);
    }
    if (event.getStatus() == null) {
      event.setStatus(EventStatus.CONFIRMED);
    }

    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + username + "' doesn't exist");
    }
    long userIdentityId = Long.parseLong(userIdentity.getId());
    long eventId = event.getId();
    Event storedEvent = getEventById(eventId);
    if (storedEvent == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND);
    }

    if (!canUpdateEvent(storedEvent, username)) {
      throw new IllegalAccessException("User '" + username + "' can't update event " + eventId);
    }

    EventRecurrence recurrence = event.getRecurrence();
    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
      recurrence = null;
      event.setRecurrence(null);
    }

    adjustEventDatesForWrite(event);

    boolean allowAttendeeToUpdate = storedEvent.getCreatorId() == userIdentityId ? event.isAllowAttendeeToUpdate()
                                                                                 : storedEvent.isAllowAttendeeToUpdate();
    boolean allowAttendeeToInvite = allowAttendeeToUpdate
        || (storedEvent.getCreatorId() == userIdentityId ? event.isAllowAttendeeToInvite()
                                                         : storedEvent.isAllowAttendeeToInvite());

    Event eventToUpdate = new Event(event.getId(),
                                    event.getParentId(),
                                    event.getRemoteId(),
                                    event.getRemoteProviderId(),
                                    event.getCalendarId(),
                                    storedEvent.getCreatorId(),
                                    userIdentityId,
                                    storedEvent.getCreated(),
                                    ZonedDateTime.now(),
                                    event.getSummary(),
                                    event.getDescription(),
                                    event.getLocation(),
                                    event.getColor(),
                                    event.getStart(),
                                    event.getEnd(),
                                    event.isAllDay(),
                                    event.getAvailability(),
                                    event.getStatus(),
                                    recurrence,
                                    occurrence,
                                    null,
                                    allowAttendeeToUpdate,
                                    allowAttendeeToInvite);

    Event updatedEvent = agendaEventStorage.updateEvent(eventToUpdate);
    attachmentService.saveEventAttachments(eventId, attachments, userIdentityId);
    conferenceService.saveEventConferences(eventId, conferences);
    reminderService.saveEventReminders(updatedEvent, reminders, userIdentityId);
    attendeeService.saveEventAttendees(updatedEvent, attendees, userIdentityId, sendInvitation, false);

    return updatedEvent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException {
    if (StringUtils.isBlank(username)) {
      throw new IllegalArgumentException("username is null");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId must be positive");
    }
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " is not found");
    }
    if (!canUpdateEvent(event, username)) {
      throw new IllegalAccessException("User " + username + " hasnt enough privileges to delete event with id " + eventId);
    }
    agendaEventStorage.deleteEventById(eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, String username) {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    List<Long> calendarOwnerIds = getCalendarOwnersOfUser(userIdentity);
    return getEventsByOwners(start, end, userIdentity, calendarOwnerIds.toArray(new Long[0]));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEventsByOwners(List<Long> ownerIds,
                                       ZonedDateTime start,
                                       ZonedDateTime end,
                                       String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    for (Long ownerId : ownerIds) {
      if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, username)) {
        throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
            + ownerIds + "'");
      }
    }
    return getEventsByOwners(start, end, userIdentity, ownerIds.toArray(new Long[0]));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEventsByAttendee(long attendeeIdentityId,
                                         ZonedDateTime start,
                                         ZonedDateTime end,
                                         String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User with name " + username + " doesn't exist");
    }
    if (Long.parseLong(userIdentity.getId()) != attendeeIdentityId) {
      throw new IllegalAccessException("User with id " + userIdentity.getId() + " isn't allowed to access events of user with id "
          + attendeeIdentityId);
    }

    // Get spaces ids and user id, to search on them as attendee only
    List<Long> attendeeIds = getCalendarOwnersOfUser(userIdentity);
    return getEventsByAttendees(start, end, userIdentity, Collections.emptyList(), attendeeIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEventsByOwnersAndAttendee(long attendeeIdentityId,
                                                  List<Long> ownerIds,
                                                  ZonedDateTime start,
                                                  ZonedDateTime end,
                                                  String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User with name " + username + " doesn't exist");
    }
    if (Long.parseLong(userIdentity.getId()) != attendeeIdentityId) {
      throw new IllegalAccessException("User with id " + userIdentity.getId() + " isn't allowed to access events of user with id "
          + attendeeIdentityId);
    }
    for (Long ownerId : ownerIds) {
      if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, username)) {
        throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
            + ownerIds + "'");
      }
    }
    List<Long> eventAttendeeIds = getCalendarOwnersOfUser(userIdentity);
    return getEventsByAttendees(start, end, userIdentity, ownerIds, eventAttendeeIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RemoteProvider> getRemoteProviders() {
    return agendaEventStorage.getRemoteProviders();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    return agendaEventStorage.saveRemoteProvider(remoteProvider);
  }

  private List<Event> getEventsByOwners(ZonedDateTime start, ZonedDateTime end, Identity userIdentity, Long... calendarOwnerIds) {
    // Retrieve events minus a day and plus a day to include all day events
    // That could transit due to timezone of user. Then filter resulted events
    // at the end to get only events that are between orginal start and end
    // dates. Example: given:
    // - an all day event of 2020-09-02 is stored in UTC in DB with information
    // (start = 2020-09-02T00:00:00Z, end = 2020-09-02T23:59:59Z )
    // - the user has a timezone +03:00
    // - the search is made on events between 2020-09-02T00:00:00+03:00, end =
    // 2020-09-02T02:00:00+03:00
    // The event isn't retrieved with the query dates because in DB, the start
    // and end dates are different, using user timezone (start =
    // 2020-09-02T03:00:00+03:00, end = 2020-09-03T02:59:59+03:00 ). Thus the
    // event will not be retrieved
    ZonedDateTime startMinusADay = start.minusDays(1);
    ZonedDateTime endPlusADay = end.plusDays(1);

    List<Long> eventIds = this.agendaEventStorage.getEventIdsByOwnerIds(startMinusADay, endPlusADay, calendarOwnerIds);
    return computeEventsProperties(start, end, userIdentity, startMinusADay, endPlusADay, eventIds);
  }

  private List<Event> getEventsByAttendees(ZonedDateTime start,
                                           ZonedDateTime end,
                                           Identity userIdentity,
                                           List<Long> calendarOwnerIds,
                                           List<Long> eventAttendeeIds) {
    // Retrieve events minus a day and plus a day to include all day events
    // That could transit due to timezone of user. Then filter resulted events
    // at the end to get only events that are between orginal start and end
    // dates. Example: given:
    // - an all day event of 2020-09-02 is stored in UTC in DB with information
    // (start = 2020-09-02T00:00:00Z, end = 2020-09-02T23:59:59Z )
    // - the user has a timezone +03:00
    // - the search is made on events between 2020-09-02T00:00:00+03:00, end =
    // 2020-09-02T02:00:00+03:00
    // The event isn't retrieved with the query dates because in DB, the start
    // and end dates are different, using user timezone (start =
    // 2020-09-02T03:00:00+03:00, end = 2020-09-03T02:59:59+03:00 ). Thus the
    // event will not be retrieved
    ZonedDateTime startMinusADay = start.minusDays(1);
    ZonedDateTime endPlusADay = end.plusDays(1);

    List<Long> eventIds = this.agendaEventStorage.getEventIdsByAttendeeIds(start, end, calendarOwnerIds, eventAttendeeIds);
    return computeEventsProperties(start, end, userIdentity, startMinusADay, endPlusADay, eventIds);
  }

  private List<Event> computeEventsProperties(ZonedDateTime start,
                                              ZonedDateTime end,
                                              Identity userIdentity,
                                              ZonedDateTime startMinusADay,
                                              ZonedDateTime endPlusADay,
                                              List<Long> eventIds) {
    if (eventIds == null || eventIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Event> events = eventIds.stream().map(this::getEventById).collect(Collectors.toList());

    TimeZone userTimezone = AgendaDateUtils.getUserTimezone(userIdentity);
    String username = userIdentity.getRemoteId();

    // Compute ACL and Dates before Recurrent occurrences computing
    events.forEach(event -> {
      adjustEventDatesForRead(event, userTimezone);
      event.setAcl(new Permission(canUpdateEvent(event, username)));
    });

    events = computeRecurrentEvents(events, startMinusADay, endPlusADay, userTimezone);

    // filter events using original start and end dates
    return filterEvents(events, start, end);
  }

  private List<Event> filterEvents(List<Event> events, ZonedDateTime start, ZonedDateTime end) {
    return events.stream()
                 .filter(event -> event.getStart().isBefore(end)
                     && (event.getEnd() == null || event.getEnd().isAfter(start)))
                 .collect(Collectors.toList());
  }

  private void adjustEventDatesForRead(Event event, TimeZone userTimezone) {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    ZoneId zoneId = userTimezone.toZoneId();
    if (event.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(zoneId);
      end = end.toLocalDate().atStartOfDay(zoneId).plusDays(1).minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(zoneId);
      end = end.withZoneSameInstant(zoneId);
    }
    event.setStart(start);
    event.setEnd(end);

    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence != null && recurrence.getUntil() != null) {
      ZonedDateTime recurrenceUntil = recurrence.getUntil();
      // end of until day in User TimeZone
      recurrenceUntil = ZonedDateTime.now(zoneId)
                                     .withYear(recurrenceUntil.getYear())
                                     .withMonth(recurrenceUntil.getMonthValue())
                                     .withDayOfMonth(recurrenceUntil.getDayOfMonth())
                                     .toLocalDate()
                                     .atStartOfDay(zoneId)
                                     .plusDays(1)
                                     .minusSeconds(1);
      recurrence.setUntil(recurrenceUntil);
    }
  }

  private void adjustEventDatesForWrite(Event event) {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    if (event.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(ZoneOffset.UTC);
      end = end.withZoneSameInstant(ZoneOffset.UTC);
    }
    event.setStart(start);
    event.setEnd(end);

    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence != null && recurrence.getUntil() != null) {
      ZonedDateTime recurrenceUntil = recurrence.getUntil();
      // end of until day in UTC
      recurrenceUntil = ZonedDateTime.now(ZoneOffset.UTC)
                                     .withYear(recurrenceUntil.getYear())
                                     .withMonth(recurrenceUntil.getMonthValue())
                                     .withDayOfMonth(recurrenceUntil.getDayOfMonth())
                                     .toLocalDate()
                                     .atStartOfDay(ZoneOffset.UTC)
                                     .plusDays(1)
                                     .minusSeconds(1);
      recurrence.setUntil(recurrenceUntil);
    }
  }

  private List<Long> getCalendarOwnersOfUser(Identity userIdentity) {
    List<Long> calendarOwners = new ArrayList<>();
    String userIdentityId = userIdentity.getId();
    calendarOwners.add(Long.parseLong(userIdentityId));
    try {
      Utils.addUserSpacesIdentities(spaceService, identityManager, userIdentity.getRemoteId(), calendarOwners);
    } catch (Exception e) {
      throw new IllegalStateException("Error while retrieving spaces of user with id: " + userIdentityId, e);
    }
    return calendarOwners;
  }

  @Override
  public boolean canAccessEvent(Event event, String username) {
    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    return Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), username)
        || isEventAttendee(event.getId(), username);
  }

  @Override
  public boolean canUpdateEvent(Event event, String username) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    long userIdentityId = Long.parseLong(identity.getId());
    Calendar calendar = null;
    if (userIdentityId == event.getCreatorId()) {
      // Check if creator can always access to calendar or not
      calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
      if (Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), username)) {
        return true;
      }
    }
    if (event.isAllowAttendeeToUpdate() && isEventAttendee(event.getId(), username)) {
      return true;
    }
    if (calendar == null) {
      calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    }
    return Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), username);
  }

  @Override
  public boolean canCreateEvent(Calendar calendar, String username) {
    return Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), username);
  }

  private List<Event> computeRecurrentEvents(List<Event> events, ZonedDateTime start, ZonedDateTime end, TimeZone userTimezone) {
    List<Event> computedEvents = new ArrayList<>();
    for (Event event : events) {
      if (event.getRecurrence() == null) {
        computedEvents.add(event);
      } else {
        List<Event> occurrences = Utils.getOccurrences(event, start.toLocalDate(), end.toLocalDate(), userTimezone);
        occurrences = filterExceptionalEvents(event, occurrences, start, end);
        if (!occurrences.isEmpty()) {
          computedEvents.addAll(occurrences);
        }
      }
    }
    sortEvents(computedEvents);
    return computedEvents;
  }

  private void sortEvents(List<Event> computedEvents) {
    computedEvents.sort((event1, event2) -> event1.getStart().compareTo(event2.getStart()));
  }

  private List<Event> filterExceptionalEvents(Event recurrentEvent,
                                              List<Event> occurrences,
                                              ZonedDateTime start,
                                              ZonedDateTime end) {
    List<Long> exceptionalOccurenceEventIds = agendaEventStorage.getExceptionalOccurenceEventIds(recurrentEvent.getId(),
                                                                                                 start,
                                                                                                 end);
    List<Event> exceptionalEvents = exceptionalOccurenceEventIds == null
        || exceptionalOccurenceEventIds.isEmpty() ? Collections.emptyList()
                                                  : exceptionalOccurenceEventIds.stream()
                                                                                .map(this::getEventById)
                                                                                .collect(Collectors.toList());
    return occurrences.stream()
                      .filter(occurrence -> exceptionalEvents.stream()
                                                             .noneMatch(exceptionalOccurence -> occurrence.getOccurrence()
                                                                                                          .getId()
                                                                                                          .isEqual(exceptionalOccurence.getOccurrence()
                                                                                                                                       .getId())))
                      .collect(Collectors.toList());
  }

  private boolean isEventAttendee(long eventId, String username) {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    long userIdentityId = Long.parseLong(userIdentity.getId());

    List<EventAttendee> eventAttendees = attendeeService.getEventAttendees(eventId);
    return eventAttendees != null
        && eventAttendees.stream().anyMatch(eventAttendee -> {
          if (userIdentityId == eventAttendee.getIdentityId()) {
            return true;
          } else {
            Identity identity = identityManager.getIdentity(String.valueOf(eventAttendee.getIdentityId()));
            if (StringUtils.equals(identity.getProviderId(), SpaceIdentityProvider.NAME)) {
              if (spaceService.isSuperManager(username)) {
                return true;
              } else {
                Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
                return spaceService.isMember(space, username);
              }
            } else {
              return false;
            }
          }
        });
  }

}
