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

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventServiceImpl implements AgendaEventService {

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventAttendeeService   attendeeService;

  private AgendaEventAttachmentService attachmentService;

  private AgendaEventConferenceService conferenceService;

  private AgendaEventReminderService   reminderService;

  private AgendaEventStorage           agendaEventStorage;

  private AgendaSearchConnector        agendaSearchConnector;

  private IdentityManager              identityManager;

  private SpaceService                 spaceService;

  private ListenerService              listenerService;

  public AgendaEventServiceImpl(AgendaCalendarService agendaCalendarService,
                                AgendaEventAttendeeService attendeeService,
                                AgendaEventAttachmentService attachmentService,
                                AgendaEventConferenceService conferenceService,
                                AgendaEventReminderService reminderService,
                                AgendaSearchConnector agendaSearchConnector,
                                AgendaEventStorage agendaEventStorage,
                                IdentityManager identityManager,
                                SpaceService spaceService,
                                ListenerService listenerService) {
    this.agendaCalendarService = agendaCalendarService;
    this.attendeeService = attendeeService;
    this.attachmentService = attachmentService;
    this.conferenceService = conferenceService;
    this.reminderService = reminderService;
    this.agendaEventStorage = agendaEventStorage;
    this.agendaSearchConnector = agendaSearchConnector;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventById(long eventId, ZoneId timeZone, String username) throws IllegalAccessException {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      return null;
    }
    if (canAccessEvent(event, username)) {
      adjustEventDatesForRead(event, timeZone);

      Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
      long userIdentityId = Long.parseLong(identity.getId());

      boolean canUpdateEvent = canUpdateEvent(event, username);
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);

      event.setAcl(new Permission(canUpdateEvent, isEventAttendee));
      return event;
    } else {
      throw new IllegalAccessException("User " + username + "is not allowed to access event with id " + eventId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventById(long eventId, ZoneId timeZone, long identityId) throws IllegalAccessException {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      return null;
    }

    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    if (canAccessEvent(event, identityId)) {
      adjustEventDatesForRead(event, timeZone);
      boolean canUpdateEvent = canUpdateEvent(event, identity.getRemoteId());
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
      event.setAcl(new Permission(canUpdateEvent, isEventAttendee));
      return event;
    } else {
      throw new IllegalAccessException("User with identity id " + identityId + "is not allowed to access event with id "
          + eventId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventById(long eventId) {
    return agendaEventStorage.getEventById(eventId);
  }

  @Override
  public Event getEventOccurrence(long parentEventId,
                                  ZonedDateTime occurrenceId,
                                  ZoneId timeZone,
                                  long identityId) throws IllegalAccessException {
    Event recurrentEvent = agendaEventStorage.getEventById(parentEventId);
    if (recurrentEvent == null) {
      return null;
    }

    if (recurrentEvent.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + parentEventId + " is not a recurrent event");
    }

    Event event = null;

    Identity identity = identityManager.getIdentity(String.valueOf(identityId));

    Event exceptionalOccurrenceEvent = agendaEventStorage.getExceptionalOccurrenceEvent(parentEventId, occurrenceId);
    if (exceptionalOccurrenceEvent != null) {
      if (!canAccessEvent(exceptionalOccurrenceEvent, identityId)) {
        throw new IllegalAccessException("");
      }
      event = exceptionalOccurrenceEvent;
    } else {
      List<Event> occurrences = Utils.getOccurrences(recurrentEvent,
                                                     occurrenceId.toLocalDate(),
                                                     occurrenceId.toLocalDate().plusDays(1),
                                                     timeZone,
                                                     1);
      if (occurrences != null && !occurrences.isEmpty()) {
        event = occurrences.get(0);
      }
    }

    if (event != null) {
      adjustEventDatesForRead(event, timeZone);
      boolean canUpdateEvent = canUpdateEvent(event, identity.getRemoteId());
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
      event.setAcl(new Permission(canUpdateEvent, isEventAttendee));
    }
    return event;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getExceptionalOccurrenceEvent(long eventId, ZonedDateTime occurrenceId) {
    return agendaEventStorage.getExceptionalOccurrenceEvent(eventId, occurrenceId);
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
                                    0,
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
    attendeeService.saveEventAttendees(createdEvent,
                                       attendees,
                                       userIdentityId,
                                       sendInvitation,
                                       false,
                                       EventModificationType.ADDED);

    Utils.broadcastEvent(listenerService, Utils.POST_CREATE_AGENDA_EVENT_EVENT, eventId, 0);

    return getEventById(eventId, event.getStart().getZone(), username);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event createEventExceptionalOccurrence(long eventId,
                                                List<EventAttendee> attendees,
                                                List<EventConference> conferences,
                                                List<EventAttachment> attachments,
                                                List<EventReminder> reminders,
                                                ZonedDateTime occurrenceId) throws IllegalAccessException,
                                                                            AgendaException,
                                                                            ObjectNotFoundException {
    Event parentEvent = getEventById(eventId);
    if (parentEvent == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
    if (parentEvent.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + eventId + " isn't a recurrent event");
    }
    if (parentEvent.getRecurrence().getOverallStart().toLocalDate().isAfter(occurrenceId.toLocalDate())) {
      throw new IllegalStateException("Event with id " + eventId + " doesn't have an occurrence with id " + occurrenceId);
    }
    if (parentEvent.getRecurrence().getOverallEnd() != null
        && parentEvent.getRecurrence().getOverallEnd().toLocalDate().isBefore(occurrenceId.toLocalDate())) {
      throw new IllegalStateException("Event with id " + eventId + " doesn't have an occurrence with id " + occurrenceId);
    }
    occurrenceId = occurrenceId.withZoneSameInstant(ZoneOffset.UTC);

    Event exceptionalEvent = parentEvent.clone();
    exceptionalEvent.setId(0);
    exceptionalEvent.setParentId(parentEvent.getId());
    exceptionalEvent.setRecurrence(null);
    exceptionalEvent.setOccurrence(new EventOccurrence(occurrenceId, true));
    exceptionalEvent.setStart(exceptionalEvent.getStart()
                                              .withZoneSameInstant(ZoneOffset.UTC)
                                              .withYear(occurrenceId.getYear())
                                              .withMonth(occurrenceId.getMonthValue())
                                              .withDayOfMonth(occurrenceId.getDayOfMonth()));
    exceptionalEvent.setEnd(exceptionalEvent.getEnd()
                                            .withZoneSameInstant(ZoneOffset.UTC)
                                            .withYear(occurrenceId.getYear())
                                            .withMonth(occurrenceId.getMonthValue())
                                            .withDayOfMonth(occurrenceId.getDayOfMonth()));
    exceptionalEvent = agendaEventStorage.createEvent(exceptionalEvent);
    long originalRecurrentEventCreator = parentEvent.getCreatorId();
    long exceptionalEventId = exceptionalEvent.getId();

    if (attachments != null && !attachments.isEmpty()) {
      attachments.forEach(attachment -> {
        attachment.setId(0);
        attachment.setEventId(exceptionalEventId);
      });
      attachmentService.saveEventAttachments(exceptionalEventId, attachments, originalRecurrentEventCreator);
    }
    if (conferences != null && !conferences.isEmpty()) {
      conferences.forEach(conference -> {
        conference.setId(0);
        conference.setEventId(exceptionalEventId);
      });
      conferenceService.saveEventConferences(exceptionalEventId, conferences);
    }
    if (reminders != null && !reminders.isEmpty()) {
      reminders.forEach(reminder -> reminder.setId(0));
      reminderService.saveEventReminders(exceptionalEvent, reminders, originalRecurrentEventCreator);
    }
    if (attendees != null && !attendees.isEmpty()) {
      attendees.forEach(attendee -> attendee.setId(0));
      attendeeService.saveEventAttendees(exceptionalEvent,
                                         attendees,
                                         originalRecurrentEventCreator,
                                         false,
                                         false,
                                         EventModificationType.ADDED);
    }
    return exceptionalEvent;
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
    if (event.getParentId() == event.getId()) {
      throw new AgendaException(AgendaExceptionType.EVENT_CYCLIC_DEPENDENCY);
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
    attendeeService.saveEventAttendees(updatedEvent,
                                       attendees,
                                       userIdentityId,
                                       sendInvitation,
                                       false,
                                       EventModificationType.UPDATED);

    Utils.broadcastEvent(listenerService, Utils.POST_UPDATE_AGENDA_EVENT_EVENT, eventId, 0);

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
    attendeeService.sendInvitations(eventId, EventModificationType.DELETED);
    agendaEventStorage.deleteEventById(eventId);
    Utils.broadcastEvent(listenerService, Utils.POST_DELETE_AGENDA_EVENT_EVENT, eventId, 0);
  }

  @Override
  public List<Event> getEvents(EventFilter eventFilter,
                               String username,
                               ZoneId userTimeZone) throws IllegalAccessException {
    if (eventFilter == null) {
      throw new IllegalArgumentException("eventFilter is mandatory");
    }

    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);

    List<Long> ownerIds = eventFilter.getOwnerIds();
    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, username)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }
    if (userIdentity != null) {
      long attendeeId = eventFilter.getAttendeeId();
      if (attendeeId > 0) {
        if (!String.valueOf(attendeeId).contentEquals(userIdentity.getId())) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + attendeeId + "'");
        }
        List<Long> attendeeSpaceIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
        eventFilter.setAttendeeWithSpacesIds(attendeeSpaceIds);
      } else if (ownerIds == null) {
        // If no attendee is selected, and no owners, filter events by use
        // spaceIds
        ownerIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
      }

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
      ZonedDateTime start = eventFilter.getStart();
      ZonedDateTime end = eventFilter.getEnd();
      ZonedDateTime startMinusADay = start.minusDays(1);
      ZonedDateTime endPlusADay = end == null ? null : end.plusDays(1);
      int limit = eventFilter.getLimit();
      if (limit > 0) {
        EventFilter maxEndDateFilter = eventFilter.clone();
        maxEndDateFilter.setOwnerIds(ownerIds);
        maxEndDateFilter.setStart(startMinusADay);
        maxEndDateFilter.setEnd(endPlusADay);
        ZonedDateTime maxEndDate = getMaxEndDate(maxEndDateFilter, userTimeZone);
        if (maxEndDate == null) {
          return Collections.emptyList();
        }
        endPlusADay = maxEndDate.plusDays(1);
      }

      EventFilter requestEventFilter = eventFilter.clone();
      requestEventFilter.setOwnerIds(ownerIds);
      requestEventFilter.setStart(startMinusADay);
      requestEventFilter.setEnd(endPlusADay);
      List<Long> eventIds = this.agendaEventStorage.getEventIds(requestEventFilter);
      return computeEventsProperties(eventIds, start, end, userTimeZone, limit, userIdentity, startMinusADay, endPlusADay);
    } else {
      ZonedDateTime start = eventFilter.getStart();
      ZonedDateTime end = eventFilter.getEnd();
      ZonedDateTime startMinusADay = start.minusDays(1);
      ZonedDateTime endPlusADay = end == null ? null : end.plusDays(1);
      int limit = eventFilter.getLimit();
      if (limit > 0) {
        EventFilter maxEndDateFilter = eventFilter.clone();
        maxEndDateFilter.setOwnerIds(ownerIds);
        maxEndDateFilter.setStart(startMinusADay);
        maxEndDateFilter.setEnd(endPlusADay);
        ZonedDateTime maxEndDate = getMaxEndDate(maxEndDateFilter, userTimeZone);
        if (maxEndDate == null) {
          return Collections.emptyList();
        }
        endPlusADay = maxEndDate.plusDays(1);
      }

      EventFilter requestEventFilter = eventFilter.clone();
      requestEventFilter.setOwnerIds(ownerIds);
      requestEventFilter.setStart(startMinusADay);
      requestEventFilter.setEnd(endPlusADay);
      List<Long> eventIds = this.agendaEventStorage.getEventIds(requestEventFilter);
      return computeEventsProperties(eventIds, start, end, userTimeZone, limit, userIdentity, startMinusADay, endPlusADay);
    }
  }

  @Override
  public boolean canAccessEvent(Event event, String username) {
    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    long userIdentityId = Long.parseLong(identity.getId());

    return Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), username)
        || attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
  }

  @Override
  public boolean canAccessEvent(Event event, long identityId) {
    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);

    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    if (identity == null) {
      return false;
    }
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, identity.getProviderId())) {
      return Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), identity.getRemoteId())
          || attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
    } else {
      return attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
    }
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
    if (event.isAllowAttendeeToUpdate()
        && attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId)) {
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

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventSearchResult> search(long userIdentityId, ZoneId userTimeZone, String query, int offset, int limit) {
    if (userTimeZone == null) {
      userTimeZone = Utils.getUserTimezone(identityManager, userIdentityId);
    }

    List<EventSearchResult> searchResults = agendaSearchConnector.search(userIdentityId, userTimeZone, query, offset, limit);
    final ZoneId timeZone = userTimeZone;
    return searchResults.stream().map(event -> {
      if (event.isRecurrent()) {
        Event recurrentEvent = agendaEventStorage.getEventById(event.getId());
        ZonedDateTime today = ZonedDateTime.now().toLocalDate().atStartOfDay(timeZone);
        List<Event> occurrences = getEventOccurrencesInPeriod(recurrentEvent, today, null, timeZone, 10);
        if (occurrences == null || occurrences.isEmpty()) {
          occurrences = getEventOccurrencesInPeriod(recurrentEvent, recurrentEvent.getStart(), today, timeZone, 10);
          Collections.reverse(occurrences);
        }

        if (occurrences != null && !occurrences.isEmpty()) {
          Event occurrenceEvent = occurrences.get(0);
          if (occurrenceEvent.getOccurrence().isExceptional()) {
            event.setSummary(occurrenceEvent.getSummary());
            event.setDescription(occurrenceEvent.getDescription());
            event.setLocation(occurrenceEvent.getLocation());
          }
          event.setStart(occurrenceEvent.getStart());
          event.setEnd(occurrenceEvent.getEnd());
        }
      }
      return event;
    }).collect(Collectors.toList());
  }

  private ZonedDateTime getMaxEndDate(EventFilter eventFilter, ZoneId userTimeZone) {
    int initialSize = 0;
    int storageLimit = eventFilter.getLimit();
    List<Event> events = null;
    do {
      initialSize = events == null ? 0 : events.size();
      storageLimit *= 5;
      List<Long> eventIds = this.agendaEventStorage.getEventIds(eventFilter);
      events = getEventsList(eventIds, eventFilter.getStart(), eventFilter.getEnd(), userTimeZone, storageLimit);
    } while (events.size() > initialSize && events.size() < eventFilter.getLimit());
    return getMaxEndDate(events);
  }

  private ZonedDateTime getMaxEndDate(List<Event> events) {
    if (events != null && !events.isEmpty()) {
      Event eventWithMaxDate = events.stream().max((event1, event2) -> event1.getEnd().compareTo(event2.getEnd())).orElse(null);
      if (eventWithMaxDate != null) {
        return eventWithMaxDate.getEnd();
      }
    }
    return null;
  }

  private List<Event> getEventsList(List<Long> eventIds,
                                    ZonedDateTime startMinusADay,
                                    ZonedDateTime endPlusADay,
                                    ZoneId timeZone,
                                    int limit) {
    List<Event> events = eventIds.stream().map(this::getEventById).collect(Collectors.toList());
    events.forEach(event -> adjustEventDatesForRead(event, timeZone));
    return computeRecurrentEvents(events, startMinusADay, endPlusADay, timeZone, limit);
  }

  private List<Event> computeEventsProperties(List<Long> eventIds,
                                              ZonedDateTime start,
                                              ZonedDateTime end,
                                              ZoneId timeZone,
                                              int limit,
                                              Identity userIdentity,
                                              ZonedDateTime startMinusADay,
                                              ZonedDateTime endPlusADay) {
    if (eventIds == null || eventIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Event> events = getEventsList(eventIds, startMinusADay, endPlusADay, timeZone, limit);
    events = filterEvents(events, start, end, limit);
    if (userIdentity != null) {
      computeEventsAcl(events, userIdentity);
    }
    return events;
  }

  private void computeEventsAcl(List<Event> events, Identity userIdentity) {
    String username = userIdentity.getRemoteId();
    long userIdentityId = Long.parseLong(userIdentity.getId());
    Map<Long, Permission> eventPermissionsMap = new HashMap<>();
    events.forEach(event -> {
      long eventId = getEventIdOrParentId(event);
      Permission permission = eventPermissionsMap.get(eventId);
      if (permission == null) {
        boolean canUpdateEvent = canUpdateEvent(event, username);
        boolean isEventAttendee = attendeeService.isEventAttendee(eventId, userIdentityId);
        permission = new Permission(canUpdateEvent, isEventAttendee);
        eventPermissionsMap.put(eventId, permission);
      }
      event.setAcl(permission);
    });
  }

  private List<Event> filterEvents(List<Event> events, ZonedDateTime start, ZonedDateTime end, int limit) {
    events = events.stream()
                   .filter(event -> (end == null || event.getStart().isBefore(end))
                       && (event.getEnd() == null || event.getEnd().isAfter(start)))
                   .collect(Collectors.toList());
    sortEvents(events);
    if (limit > 0 && events.size() > limit) {
      events = events.subList(0, limit);
    }
    return events;
  }

  private void adjustEventDatesForRead(Event event, ZoneId timeZone) {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    if (timeZone == null) {
      timeZone = ZoneId.systemDefault();
    }

    if (event.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(timeZone);
      end = end.toLocalDate().atStartOfDay(timeZone).plusDays(1).minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(timeZone);
      end = end.withZoneSameInstant(timeZone);
    }
    event.setStart(start);
    event.setEnd(end);

    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence != null && recurrence.getUntil() != null) {
      ZonedDateTime recurrenceUntil = recurrence.getUntil();
      // end of until day in User TimeZone
      recurrenceUntil = ZonedDateTime.now(timeZone)
                                     .withYear(recurrenceUntil.getYear())
                                     .withMonth(recurrenceUntil.getMonthValue())
                                     .withDayOfMonth(recurrenceUntil.getDayOfMonth())
                                     .toLocalDate()
                                     .atStartOfDay(timeZone)
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

  private List<Event> computeRecurrentEvents(List<Event> events,
                                             ZonedDateTime start,
                                             ZonedDateTime end,
                                             ZoneId userTimezone,
                                             int limit) {
    List<Event> computedEvents = new ArrayList<>();
    for (Event event : events) {
      if (event.getRecurrence() == null) {
        computedEvents.add(event);
      } else {
        if (userTimezone == null) {
          userTimezone = ZoneId.systemDefault();
        }
        List<Event> occurrences = getEventOccurrencesInPeriod(event, start, end, userTimezone, limit);
        if (occurrences != null && !occurrences.isEmpty()) {
          computedEvents.addAll(occurrences);
        }
      }
    }
    return computedEvents;
  }

  private List<Event> getEventOccurrencesInPeriod(Event recurrentEvent,
                                                  ZonedDateTime start,
                                                  ZonedDateTime end,
                                                  ZoneId userTimezone,
                                                  int limit) {
    List<Event> occurrences = Utils.getOccurrences(recurrentEvent,
                                                   start.toLocalDate(),
                                                   end == null ? null : end.toLocalDate(),
                                                   userTimezone,
                                                   limit);
    if (occurrences != null && !occurrences.isEmpty()) {
      ZonedDateTime endDateOfOccurrences = end;
      if (endDateOfOccurrences == null) {
        Event eventWithMaxDate = occurrences.stream()
                                            .max((event1, event2) -> event1.getEnd().compareTo(event2.getEnd()))
                                            .orElse(null);
        endDateOfOccurrences = eventWithMaxDate.getEnd(); // NOSONAR
      }
      occurrences = filterExceptionalEvents(recurrentEvent, occurrences, start, endDateOfOccurrences, userTimezone);
    }
    return occurrences;
  }

  private void sortEvents(List<Event> computedEvents) {
    computedEvents.sort((event1, event2) -> event1.getStart().compareTo(event2.getStart()));
  }

  private List<Event> filterExceptionalEvents(Event recurrentEvent,
                                              List<Event> occurrences,
                                              ZonedDateTime start,
                                              ZonedDateTime end,
                                              ZoneId zoneId) {
    List<Long> exceptionalOccurenceEventIds = agendaEventStorage.getExceptionalOccurenceEventIds(recurrentEvent.getId(),
                                                                                                 start,
                                                                                                 end);
    List<Event> exceptionalEvents = exceptionalOccurenceEventIds == null
        || exceptionalOccurenceEventIds.isEmpty() ? Collections.emptyList()
                                                  : exceptionalOccurenceEventIds.stream()
                                                                                .map(this::getEventById)
                                                                                .collect(Collectors.toList());
    return occurrences.stream()
                      .filter(occurrence -> {
                        LocalDate occurrenceDate = occurrence.getOccurrence()
                                                             .getId()
                                                             .toInstant()
                                                             .atZone(zoneId)
                                                             .withZoneSameLocal(ZoneOffset.UTC)
                                                             .toLocalDate();
                        return exceptionalEvents.stream()
                                                .noneMatch(exceptionalOccurence -> {
                                                  LocalDate exceptionalOccurenceDate = exceptionalOccurence.getOccurrence()
                                                                                                           .getId()
                                                                                                           .toInstant()
                                                                                                           .atZone(zoneId)
                                                                                                           .withZoneSameLocal(ZoneOffset.UTC)
                                                                                                           .toLocalDate();
                                                  return occurrenceDate.isEqual(exceptionalOccurenceDate);
                                                });
                      })
                      .collect(Collectors.toList());
  }

  private long getEventIdOrParentId(Event event) {
    if (event != null) {
      if (event.getId() > 0) {
        return event.getId();
      } else if (event.getParentId() > 0) {
        return event.getParentId();
      }
    }
    return 0;
  }

}
