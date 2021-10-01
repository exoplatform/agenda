// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.service;

import java.time.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventServiceImpl implements AgendaEventService {

  private static final Log             LOG = ExoLogger.getLogger(AgendaEventServiceImpl.class);

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventAttendeeService   attendeeService;

  private AgendaEventConferenceService conferenceService;

  private AgendaEventReminderService   reminderService;

  private AgendaRemoteEventService     remoteEventService;

  private AgendaEventDatePollService   datePollService;

  private AgendaEventStorage           agendaEventStorage;

  private AgendaSearchConnector        agendaSearchConnector;

  private IdentityManager              identityManager;

  private SpaceService                 spaceService;

  private ListenerService              listenerService;

  public AgendaEventServiceImpl(AgendaCalendarService agendaCalendarService,
                                AgendaEventAttendeeService attendeeService,
                                AgendaEventConferenceService conferenceService,
                                AgendaEventReminderService reminderService,
                                AgendaRemoteEventService remoteEventService,
                                AgendaEventDatePollService datePollService,
                                AgendaSearchConnector agendaSearchConnector,
                                AgendaEventStorage agendaEventStorage,
                                IdentityManager identityManager,
                                SpaceService spaceService,
                                ListenerService listenerService) {
    this.agendaCalendarService = agendaCalendarService;
    this.attendeeService = attendeeService;
    this.conferenceService = conferenceService;
    this.reminderService = reminderService;
    this.remoteEventService = remoteEventService;
    this.datePollService = datePollService;
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
  public Event getEventById(long eventId, ZoneId timeZone, long userIdentityId) throws IllegalAccessException {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      return null;
    }

    if (canAccessEvent(event, userIdentityId)) {
      adjustEventDatesForRead(event, timeZone);
      boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
      event.setAcl(new EventPermission(canUpdateEvent, isEventAttendee));
      return event;
    } else {
      throw new IllegalAccessException("User with identity id " + userIdentityId + "is not allowed to access event with id "
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

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getEventOccurrence(long parentEventId,
                                  ZonedDateTime occurrenceId,
                                  ZoneId timeZone,
                                  long userIdentityId) throws IllegalAccessException {
    Event recurrentEvent = agendaEventStorage.getEventById(parentEventId);
    if (recurrentEvent == null) {
      return null;
    }

    if (recurrentEvent.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + parentEventId + " is not a recurrent event");
    }

    Event event = null;

    Event exceptionalOccurrenceEvent = agendaEventStorage.getExceptionalOccurrenceEvent(parentEventId, occurrenceId);
    if (exceptionalOccurrenceEvent != null) {
      if (!canAccessEvent(exceptionalOccurrenceEvent, userIdentityId)) {
        throw new IllegalAccessException("");
      }
      event = exceptionalOccurrenceEvent;
    } else {
      List<Event> occurrences = Utils.getOccurrences(recurrentEvent,
                                                     occurrenceId.toLocalDate().minusDays(1),
                                                     occurrenceId.toLocalDate().plusDays(1),
                                                     3);
      ZonedDateTime occurrenceIdUTC = occurrenceId.withZoneSameInstant(ZoneOffset.UTC);
      event = occurrences.stream()
                         .filter(occ -> occ.getOccurrence()
                                           .getId()
                                           .withZoneSameInstant(ZoneOffset.UTC)
                                           .equals(occurrenceIdUTC))
                         .findFirst()
                         .orElse(null);
      if (event == null) {
        event = occurrences.stream()
                           .filter(occ -> occ.getOccurrence()
                                             .getId()
                                             .withZoneSameInstant(ZoneOffset.UTC)
                                             .toLocalDate()
                                             .equals(occurrenceIdUTC.toLocalDate()))
                           .findFirst()
                           .orElse(null);
      }
    }

    if (event != null) {
      adjustEventDatesForRead(event, timeZone);
      boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
      event.setAcl(new EventPermission(canUpdateEvent, isEventAttendee));
    }
    return event;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getExceptionalOccurrenceEvents(long parentEventId,
                                                    ZoneId timeZone,
                                                    long userIdentityId) throws IllegalAccessException {
    Event parentEvent = agendaEventStorage.getEventById(parentEventId);
    if (parentEvent == null) {
      return Collections.emptyList();
    }
    if (!canAccessEvent(parentEvent, userIdentityId)) {
      throw new IllegalAccessException("User " + userIdentityId + "is not allowed to access event with id " + parentEventId);
    }
    List<Long> exceptionalOccurenceIds = agendaEventStorage.getExceptionalOccurenceIds(parentEventId);
    return exceptionalOccurenceIds.stream()
                                  .map(eventId -> {
                                    try {
                                      return this.getEventById(eventId, timeZone, userIdentityId);
                                    } catch (IllegalAccessException e) {
                                      // Allow to user to access other
                                      // exceptional events
                                      LOG.debug("User is not allowed to access exceptional event {}. Ignore retrieving this exceptional event",
                                                eventId,
                                                e);
                                      return null;
                                    }
                                  })
                                  .filter(event -> event != null)
                                  .collect(Collectors.toList());
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
                           List<EventReminder> reminders,
                           List<EventDateOption> dateOptions,
                           RemoteEvent remoteEvent,
                           boolean sendInvitation,
                           long userIdentityId) throws IllegalAccessException, AgendaException {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId is mandatory");
    }
    if (event == null) {
      throw new IllegalArgumentException("Event is mandatory");
    }
    if (event.getId() > 0) {
      throw new IllegalArgumentException("Event id must be null");
    }
    long calendarId = event.getCalendarId();
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Event calendar id must be positive");
    }

    // Ensure that dateOptions is modifiable
    if (dateOptions != null) {
      dateOptions = new ArrayList<>(dateOptions);

      checkAndComputeDateOptions(event, dateOptions);
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

    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + userIdentityId + "' doesn't exist");
    }

    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    }

    boolean canCreateCalendarEvents = canCreateEvent(calendar, userIdentityId);
    if (!canCreateCalendarEvents) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't create an event in calendar " + calendar.getTitle());
    }

    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
      event.setRecurrence(null);
    }

    adjustEventDatesForWrite(event);

    Event eventToCreate = new Event(0,
                                    event.getParentId(),
                                    calendarId,
                                    userIdentityId,
                                    0,
                                    ZonedDateTime.now(),
                                    null,
                                    event.getSummary(),
                                    event.getDescription(),
                                    event.getLocation(),
                                    event.getColor(),
                                    event.getTimeZoneId(),
                                    event.getStart(),
                                    event.getEnd(),
                                    event.isAllDay(),
                                    event.getAvailability(),
                                    event.getStatus(),
                                    event.getRecurrence(),
                                    event.getOccurrence(),
                                    null,
                                    event.isAllowAttendeeToUpdate(),
                                    event.isAllowAttendeeToInvite());

    Event createdEvent = agendaEventStorage.createEvent(eventToCreate);
    long eventId = createdEvent.getId();
    createdEvent = getEventById(eventId, event.getTimeZoneId(), userIdentityId);

    AgendaEventModification eventModifications =
                                               new AgendaEventModification(eventId,
                                                                           createdEvent.getCalendarId(),
                                                                           userIdentityId,
                                                                           Collections.singleton(AgendaEventModificationType.ADDED));

    if (conferences != null && !conferences.isEmpty()) {
      conferenceService.saveEventConferences(eventId, conferences);
    }
    if (dateOptions != null && !dateOptions.isEmpty()) {
      datePollService.createEventPoll(eventId, dateOptions, userIdentityId);
    }
    if (reminders != null) {
      reminderService.saveEventReminders(createdEvent, reminders, userIdentityId);
    }
    if (remoteEvent != null) {
      remoteEvent.setIdentityId(userIdentityId);
      remoteEvent.setEventId(createdEvent.getId());
      remoteEventService.saveRemoteEvent(remoteEvent);
    }
    if (attendees != null && !attendees.isEmpty()) {
      attendeeService.saveEventAttendees(createdEvent,
                                         attendees,
                                         userIdentityId,
                                         sendInvitation,
                                         false,
                                         eventModifications);
    }

    if (createdEvent.getStatus() == EventStatus.TENTATIVE) {
      Utils.broadcastEvent(listenerService, Utils.POST_CREATE_AGENDA_EVENT_POLL, eventModifications, null);
    } else if (createdEvent.getStatus() == EventStatus.CONFIRMED) {
      Utils.broadcastEvent(listenerService, Utils.POST_CREATE_AGENDA_EVENT_EVENT, eventModifications, null);
    } else if (createdEvent.getStatus() == EventStatus.CANCELLED) {
      Utils.broadcastEvent(listenerService, Utils.POST_DELETE_AGENDA_EVENT_EVENT, eventModifications, null);
    }
    return createdEvent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event saveEventExceptionalOccurrence(long eventId, ZonedDateTime occurrenceId) throws AgendaException {
    Event exceptionalOccurrenceEvent = getExceptionalOccurrenceEvent(eventId, occurrenceId);
    if (exceptionalOccurrenceEvent != null) {
      return exceptionalOccurrenceEvent;
    }

    List<EventAttendee> attendees = attendeeService.getEventAttendees(eventId).getEventAttendees(occurrenceId);
    cleanupAttendeeIds(attendees);
    List<EventConference> conferences = conferenceService.getEventConferences(eventId);
    cleanupConferenceIds(conferences);
    List<EventReminder> reminders = reminderService.getEventReminders(eventId);
    cleanupReminderIds(reminders);

    return createEventExceptionalOccurrence(eventId,
                                            attendees,
                                            conferences,
                                            reminders,
                                            occurrenceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event createEventExceptionalOccurrence(long eventId,
                                                List<EventAttendee> attendees,
                                                List<EventConference> conferences,
                                                List<EventReminder> reminders,
                                                ZonedDateTime occurrenceId) throws AgendaException {
    Event parentEvent = agendaEventStorage.getEventById(eventId);
    if (parentEvent == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND);
    }
    if (parentEvent.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + eventId + " isn't a recurrent event");
    }

    boolean allDay = parentEvent.isAllDay();
    occurrenceId = Utils.getOccurrenceId(allDay, occurrenceId, parentEvent.getTimeZoneId());
    LocalDate occurrenceDateUTC = occurrenceId.toLocalDate();
    LocalDate overallStartDate = parentEvent.getRecurrence()
                                            .getOverallStart()
                                            .withZoneSameInstant(ZoneOffset.UTC)
                                            .toLocalDate();
    if (overallStartDate.minusDays(1).isAfter(occurrenceDateUTC)) {
      throw new IllegalStateException("Event with id " + eventId + " doesn't have an occurrence with id " + occurrenceDateUTC
          + ". Recurrent Event overall start equals to " + overallStartDate);
    }
    ZonedDateTime overallEnd = parentEvent.getRecurrence().getOverallEnd();
    LocalDate overAllEndDate = overallEnd == null ? null
                                                  : overallEnd.withZoneSameInstant(ZoneOffset.UTC)
                                                              .toLocalDate();
    if (overAllEndDate != null && overAllEndDate.isBefore(occurrenceDateUTC)) {
      throw new IllegalStateException("Event with id " + eventId + " doesn't have an occurrence with id " + occurrenceId);
    }

    Event exceptionalEvent = parentEvent.clone();
    exceptionalEvent.setId(0);
    exceptionalEvent.setParentId(parentEvent.getId());
    exceptionalEvent.setRecurrence(null);
    exceptionalEvent.setOccurrence(new EventOccurrence(occurrenceId, true, false));
    ZonedDateTime start = exceptionalEvent.getStart();
    ZonedDateTime end = exceptionalEvent.getEnd();
    long diffInSeconds = end.toEpochSecond() - start.toEpochSecond();

    ZonedDateTime occurrenceStart = null;
    if (allDay) {
      ZonedDateTime occurrenceStartTime = occurrenceId.withZoneSameInstant(parentEvent.getTimeZoneId());
      occurrenceStart = start.withYear(occurrenceStartTime.getYear())
                             .withMonth(occurrenceStartTime.getMonthValue())
                             .withDayOfMonth(occurrenceStartTime.getDayOfMonth());
    } else {
      ZonedDateTime startStartTime = start.withZoneSameInstant(parentEvent.getTimeZoneId());
      occurrenceStart = startStartTime.withYear(occurrenceId.getYear())
                                      .withMonth(occurrenceId.getMonthValue())
                                      .withDayOfMonth(occurrenceId.getDayOfMonth())
                                      .withHour(startStartTime.getHour())
                                      .withMinute(startStartTime.getMinute());
    }
    ZonedDateTime occurrenceEnd = occurrenceStart.plusSeconds(diffInSeconds);
    exceptionalEvent.setStart(occurrenceStart);
    exceptionalEvent.setEnd(occurrenceEnd);
    adjustEventDatesForWrite(exceptionalEvent);
    exceptionalEvent = agendaEventStorage.createEvent(exceptionalEvent);
    long exceptionalEventId = exceptionalEvent.getId();

    if (conferences != null && !conferences.isEmpty()) {
      conferences.forEach(conference -> {
        conference.setId(0);
        conference.setEventId(exceptionalEventId);
      });
      conferenceService.saveEventConferences(exceptionalEventId, conferences);
    }
    if (reminders != null && !reminders.isEmpty()) {
      reminders.forEach(reminder -> {
        reminder.setId(0);
        reminder.setEventId(exceptionalEventId);
      });

      reminderService.saveEventReminders(exceptionalEvent, reminders);
    }
    if (attendees != null && !attendees.isEmpty()) {
      attendees.forEach(attendee -> {
        attendee.setId(0);
        attendee.setEventId(exceptionalEventId);
      });
      attendeeService.saveEventAttendees(exceptionalEvent,
                                         attendees,
                                         0,
                                         false,
                                         exceptionalEvent.getStatus() != EventStatus.CONFIRMED,
                                         null);
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
                           List<EventReminder> reminders,
                           List<EventDateOption> dateOptions,
                           RemoteEvent remoteEvent,
                           boolean sendInvitation,
                           long userIdentityId) throws AgendaException, IllegalAccessException {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId is mandatory");
    }
    if (event == null) {
      throw new IllegalArgumentException("Event is null");
    }
    if (event.getId() <= 0) {
      throw new IllegalArgumentException("Event id must not be null");
    }
    long calendarId = event.getCalendarId();
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Event calendar id must be positive");
    }

    if (dateOptions != null) {
      // Ensure that dateOptions is modifiable
      dateOptions = new ArrayList<>(dateOptions);

      checkAndComputeDateOptions(event, dateOptions);
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

    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence != null) {
      if (recurrence.getFrequency() == null) {
        throw new AgendaException(AgendaExceptionType.EVENT_RECURRENCE_FREQUENCY_MANDATORY);
      }
      if (recurrence.getInterval() <= 0) {
        throw new AgendaException(AgendaExceptionType.EVENT_RECURRENCE_INTERVAL_MANDATORY);
      }
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + userIdentityId + "' doesn't exist");
    }

    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    }

    long eventId = event.getId();
    Event storedEvent = getEventById(eventId);
    if (storedEvent == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND);
    }

    if (!canUpdateEvent(storedEvent, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't update event " + eventId);
    }

    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
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
                                    event.getCalendarId(),
                                    storedEvent.getCreatorId(),
                                    userIdentityId,
                                    storedEvent.getCreated(),
                                    ZonedDateTime.now(),
                                    event.getSummary(),
                                    event.getDescription(),
                                    event.getLocation(),
                                    event.getColor(),
                                    event.getTimeZoneId(),
                                    event.getStart(),
                                    event.getEnd(),
                                    event.isAllDay(),
                                    event.getAvailability(),
                                    event.getStatus(),
                                    event.getRecurrence(),
                                    event.getOccurrence(),
                                    null,
                                    allowAttendeeToUpdate,
                                    allowAttendeeToInvite);

    // Delete exceptional occurrences when updating the whole recurrent event
    if (eventToUpdate.getRecurrence() != null || storedEvent.getRecurrence() != null) {
      agendaEventStorage.deleteExceptionalOccurences(eventToUpdate.getId());
    }

    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.UPDATED);
    Utils.detectEventModifiedFields(event, storedEvent, eventModifications);
    if (eventToUpdate.getOccurrence() != null && eventModifications.hasModifiedDate()) {
      eventToUpdate.getOccurrence().setDatesModified(true);
    }
    Event updatedEvent = agendaEventStorage.updateEvent(eventToUpdate);

    Set<AgendaEventModificationType> conferenceModifications = conferenceService.saveEventConferences(eventId, conferences);
    eventModifications.addModificationTypes(conferenceModifications);

    Set<AgendaEventModificationType> reminderModifications = reminderService.saveEventReminders(updatedEvent,
                                                                                                reminders,
                                                                                                userIdentityId);
    eventModifications.addModificationTypes(reminderModifications);

    remoteEventService.saveRemoteEvent(eventId, remoteEvent, userIdentityId);

    boolean resetResponses =
                           (updatedEvent.getStatus() == EventStatus.TENTATIVE || storedEvent.getStatus() == EventStatus.TENTATIVE)
                               && updatedEvent.getStatus() != storedEvent.getStatus();
    Set<AgendaEventModificationType> attendeeModifications = attendeeService.saveEventAttendees(updatedEvent,
                                                                                                attendees,
                                                                                                userIdentityId,
                                                                                                sendInvitation,
                                                                                                resetResponses,
                                                                                                eventModifications);
    eventModifications.addModificationTypes(attendeeModifications);

    if (eventModifications.hasModification(AgendaEventModificationType.START_DATE_UPDATED)) {
      List<EventReminder> allReminders = reminderService.getEventReminders(eventId);
      reminderService.saveEventReminders(updatedEvent, allReminders);
    }

    if (dateOptions != null && !dateOptions.isEmpty()) {
      Set<AgendaEventModificationType> dateOptionModifications = datePollService.updateEventDateOptions(eventId, dateOptions);
      eventModifications.addModificationTypes(dateOptionModifications);
      eventModifications.removeModification(AgendaEventModificationType.START_DATE_UPDATED);
      eventModifications.removeModification(AgendaEventModificationType.END_DATE_UPDATED);
    }

    Utils.broadcastEvent(listenerService, Utils.POST_UPDATE_AGENDA_EVENT_EVENT, eventModifications, null);

    return updatedEvent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateEventFields(long eventId,
                                Map<String, List<String>> fields,
                                boolean updateAllOccurrences,
                                boolean sendInvitations,
                                long userIdentityId) throws IllegalAccessException, ObjectNotFoundException, AgendaException {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId is mandatory");
    }
    if (fields == null || fields.isEmpty()) {
      throw new IllegalArgumentException("fields is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("Event id must not be null");
    }
    Event event = getEventById(eventId);
    if (event == null) {
      throw new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND);
    }

    Event originalEvent = event.clone();

    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + userIdentityId + "' doesn't exist");
    }

    if (!canUpdateEvent(event, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't update event " + eventId);
    }

    Set<Entry<String, List<String>>> fieldsEntrySet = fields.entrySet();
    for (Entry<String, List<String>> entry : fieldsEntrySet) {
      String fieldName = entry.getKey();
      String fieldValue = null;
      List<String> fieldValues = entry.getValue();
      if (fieldValues != null) {
        if (fieldValues.size() > 1) {
          throw new AgendaException(AgendaExceptionType.EVENT_FIELD_VALUE_NOT_MULTIVALUED);
        } else if (!fieldValues.isEmpty()) {
          fieldValue = fieldValues.get(0);
        }
      }
      updateEventField(event, fieldName, fieldValue);
    }

    if (event.getStart().isAfter(event.getEnd())) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_BEFORE_END_DATE);
    }

    // Delete exceptional occurrences when updating the whole recurrent event
    if (updateAllOccurrences && event.getRecurrence() != null) {
      agendaEventStorage.deleteExceptionalOccurences(event.getId());
    }

    event.setModifierId(Long.parseLong(userIdentity.getId()));
    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.UPDATED);
    Utils.detectEventModifiedFields(event, originalEvent, eventModifications);
    if (event.getOccurrence() != null && eventModifications.hasModifiedDate()) {
      event.getOccurrence().setDatesModified(true);
    }
    event = agendaEventStorage.updateEvent(event);

    if (fields.containsKey("start")) {
      List<EventReminder> reminders = reminderService.getEventReminders(event.getId());
      reminderService.saveEventReminders(event, reminders);
    }

    if (sendInvitations) {
      List<EventAttendee> eventAttendees = attendeeService.getEventAttendees(eventId).getEventAttendees();
      attendeeService.sendInvitations(event, eventAttendees, eventModifications);
    }

    Utils.broadcastEvent(listenerService, Utils.POST_UPDATE_AGENDA_EVENT_EVENT, eventModifications, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event deleteEventById(long eventId, long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId must be positive");
    }
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " is not found");
    }
    if (!canUpdateEvent(event, userIdentityId)) {
      throw new IllegalAccessException("User " + userIdentityId + " hasn't enough privileges to delete event with id " + eventId);
    }
    EventAttendeeList eventAttendeeList = attendeeService.getEventAttendees(event.getId());

    agendaEventStorage.deleteEventById(eventId);

    event.setModifierId(userIdentityId);

    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.DELETED);
    attendeeService.sendInvitations(event, eventAttendeeList.getEventAttendees(), eventModifications);
    Utils.broadcastEvent(listenerService, Utils.POST_DELETE_AGENDA_EVENT_EVENT, eventModifications, null);
    return event;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEvents(EventFilter eventFilter,
                               ZoneId userTimeZone,
                               long userIdentityId) throws IllegalAccessException {
    if (eventFilter == null) {
      throw new IllegalArgumentException("eventFilter is mandatory");
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalAccessException("User with name " + userIdentityId + " doesn't exist");
    }

    List<Long> ownerIds = eventFilter.getOwnerIds();
    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }

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
    return computeEventsProperties(eventIds, start, end, userTimeZone, false, limit, userIdentity, startMinusADay, endPlusADay);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getParentRecurrentEvents(ZonedDateTime start, ZonedDateTime end, ZoneId timeZone) {
    List<Event> events = this.agendaEventStorage.getParentRecurrentEventIds(start, end);
    events.forEach(event -> adjustEventDatesForRead(event, timeZone));
    return events;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canAccessEvent(Event event, long identityId) {
    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar.isDeleted()) {
      return false;
    }

    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    if (identity == null) {
      return false;
    }
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, identity.getProviderId())) {
      return Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), identityId)
          || attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
    } else {
      return attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canUpdateEvent(Event event, long userIdentityId) {
    Calendar calendar = null;
    if (userIdentityId == event.getCreatorId()) {
      // Check if creator can always access to calendar or not
      calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
      if (calendar.isDeleted()) {
        return false;
      }
      if (Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
        return true;
      }
    }
    if (event.isAllowAttendeeToUpdate()
        && attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId)) {
      return true;
    }
    if (calendar == null) {
      calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
      if (calendar.isDeleted()) {
        return false;
      }
    }
    return Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canCreateEvent(Calendar calendar, long userIdentityId) {
    return Utils.canCreateEvent(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventSearchResult> search(long userIdentityId, ZoneId userTimeZone, String query, int offset, int limit) {
    if (userTimeZone == null) {
      userTimeZone = ZoneOffset.UTC;
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

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEventOccurrencesInPeriod(Event recurrentEvent,
                                                 ZonedDateTime start,
                                                 ZonedDateTime end,
                                                 ZoneId timezone,
                                                 int limit) {
    if (recurrentEvent == null) {
      throw new IllegalArgumentException("recurrentEvent is mandatory");
    }
    if (start == null) {
      throw new IllegalArgumentException("start is mandatory");
    }
    if (timezone == null) {
      throw new IllegalArgumentException("timezone is mandatory");
    }
    if (end == null && limit == 0) {
      throw new IllegalArgumentException("whether use end or limit");
    }

    LocalDate startDate = start.withZoneSameInstant(timezone).toLocalDate();
    LocalDate endDate = end == null ? null : end.withZoneSameInstant(timezone).toLocalDate();

    List<Event> occurrences = Utils.getOccurrences(recurrentEvent,
                                                   startDate,
                                                   endDate,
                                                   limit);
    if (!occurrences.isEmpty()) {
      ZonedDateTime endDateOfOccurrences = endDate == null ? null : endDate.atStartOfDay(ZoneOffset.UTC);
      if (endDateOfOccurrences == null) {
        Event eventWithMaxDate = occurrences.stream()
                                            .max((event1, event2) -> event1.getEnd().compareTo(event2.getEnd()))
                                            .orElse(null);
        endDateOfOccurrences = eventWithMaxDate.getEnd(); // NOSONAR
        endDateOfOccurrences = endDateOfOccurrences.withZoneSameInstant(timezone)
                                                   .toLocalDate()
                                                   .atStartOfDay(ZoneOffset.UTC)
                                                   .plusDays(1);
      }
      ZonedDateTime startOfDay = startDate.atStartOfDay(ZoneOffset.UTC);
      ZonedDateTime endOfDay = endDateOfOccurrences;
      occurrences = filterExceptionalEvents(recurrentEvent, occurrences, startOfDay, endOfDay.plusDays(1));
    }
    occurrences.forEach(occurrence -> adjustEventDatesForRead(occurrence, timezone));
    return limit > 0 && occurrences.size() > limit ? occurrences.subList(0, limit) : occurrences;
  }

  @Override
  public void selectEventDateOption(long eventId, long dateOptionId, long userIdentityId) throws ObjectNotFoundException,
                                                                                          IllegalAccessException {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " not found");
    }
    if (event.getStatus() != EventStatus.TENTATIVE) {
      throw new IllegalStateException("Event with id " + eventId + " has a different event status than 'TENTATIVE': "
          + event.getStatus());
    }

    if (!canUpdateEvent(event, userIdentityId)) {
      throw new IllegalAccessException("User " + userIdentityId + " can't update event with id " + eventId);
    }

    EventDateOption dateOption = datePollService.getEventDateOption(dateOptionId, ZoneOffset.UTC);
    if (dateOption == null) {
      throw new ObjectNotFoundException("Event Date Option with id " + dateOptionId + " not found");
    }
    if (dateOption.getEventId() != eventId) {
      throw new IllegalStateException("Event Date Option with id " + dateOptionId + " has different event id than " + eventId);
    }
    event.setStart(dateOption.getStart());
    event.setEnd(dateOption.getEnd());
    event.setAllDay(dateOption.isAllDay());
    event.setStatus(EventStatus.CONFIRMED);
    event.setModifierId(userIdentityId);

    Event updatedEvent = agendaEventStorage.updateEvent(event);

    List<EventReminder> allReminders = reminderService.getEventReminders(eventId);
    reminderService.saveEventReminders(updatedEvent, allReminders);

    EventAttendeeList eventAttendeeList = attendeeService.getEventAttendees(eventId);
    List<EventAttendee> eventAttendees = eventAttendeeList.getEventAttendees();
    for (EventAttendee eventAttendee : eventAttendees) {
      if (eventAttendee.getIdentityId() != userIdentityId) {
        attendeeService.sendEventResponse(eventId, eventAttendee.getIdentityId(), EventAttendeeResponse.NEEDS_ACTION);
      }
    }
    attendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.ACCEPTED);
    datePollService.selectEventDateOption(dateOptionId);

    Set<AgendaEventModificationType> modificationTypes = new HashSet<>();
    modificationTypes.add(AgendaEventModificationType.UPDATED);
    modificationTypes.add(AgendaEventModificationType.DATE_OPTION_SELECTED);
    modificationTypes.add(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT);
    AgendaEventModification eventModifications = new AgendaEventModification(eventId,
                                                                             event.getCalendarId(),
                                                                             userIdentityId,
                                                                             modificationTypes);
    attendeeService.sendInvitations(event, eventAttendees, eventModifications);
    Utils.broadcastEvent(listenerService, Utils.POST_UPDATE_AGENDA_EVENT_EVENT, eventModifications, null);
  }

  @Override
  public List<Event> getPendingEvents(List<Long> ownerIds,
                                      long userIdentityId,
                                      ZoneId userTimeZone,
                                      int offset,
                                      int limit) throws IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with identity id " + userIdentityId + " doesn't exist");
    }

    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }

    List<Long> attendeeIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
    List<Long> eventIds = this.agendaEventStorage.getPendingEventIds(userIdentityId,
                                                                     ownerIds,
                                                                     attendeeIds,
                                                                     offset,
                                                                     limit);
    return computeEventsProperties(eventIds, null, null, userTimeZone, true, limit, userIdentity, null, null);
  }

  @Override
  public long countPendingEvents(List<Long> ownerIds, long userIdentityId) throws IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with identity id " + userIdentityId + " doesn't exist");
    }

    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }
    List<Long> attendeeIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
    return this.agendaEventStorage.countPendingEvents(userIdentityId, ownerIds, attendeeIds);
  }

  @Override
  public List<Event> getEventDatePolls(EventFilter eventFilter,
                                       ZoneId userTimeZone,
                                       long userIdentityId) throws IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with identity id " + userIdentityId + " doesn't exist");
    }

    List<Long> ownerIds = eventFilter.getOwnerIds();
    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }

    List<Long> attendeeIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
    List<Long> eventIds = null;
    if (eventFilter.isUseDates()) {
      eventIds = this.agendaEventStorage.getEventDatePollIds(userIdentityId,
                                                             ownerIds,
                                                             attendeeIds,
                                                             eventFilter.getStart(),
                                                             eventFilter.getEnd());
    } else {
      eventIds = this.agendaEventStorage.getEventDatePollIds(userIdentityId,
                                                             ownerIds,
                                                             attendeeIds,
                                                             eventFilter.getOffset(),
                                                             eventFilter.getLimit());
    }
    return computeEventsProperties(eventIds, null, null, userTimeZone, true, eventFilter.getLimit(), userIdentity, null, null);
  }

  @Override
  public long countEventDatePolls(List<Long> ownerIds, long userIdentityId) throws IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with identity id " + userIdentityId + " doesn't exist");
    }

    if (ownerIds != null) {
      for (Long ownerId : ownerIds) {
        if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
          throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
              + ownerIds + "'");
        }
      }
    }
    List<Long> attendeeIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
    return this.agendaEventStorage.countEventDatePolls(ownerIds, attendeeIds);
  }

  private void checkAndComputeDateOptions(Event event, List<EventDateOption> dateOptions) throws AgendaException {
    if (dateOptions != null && dateOptions.size() == 1) {
      EventDateOption eventDateOption = dateOptions.get(0);
      event.setStart(eventDateOption.getStart());
      event.setEnd(eventDateOption.getEnd());
      event.setAllDay(eventDateOption.isAllDay());

      dateOptions.clear();
    }

    if (dateOptions == null || dateOptions.isEmpty()) {
      if (event.getStart() == null) {
        throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_MANDATORY);
      }
      if (event.getEnd() == null) {
        throw new AgendaException(AgendaExceptionType.EVENT_END_DATE_MANDATORY);
      }
      if (event.getStart().isAfter(event.getEnd())) {
        throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_BEFORE_END_DATE);
      }

      if (event.getStatus() == null || event.getStatus() == EventStatus.TENTATIVE) {
        event.setStatus(EventStatus.CONFIRMED);
      }
    } else {
      event.setStart(getMinOptionStartDate(dateOptions));
      event.setEnd(getMaxOptionEndDate(dateOptions));
      event.setStatus(EventStatus.TENTATIVE);

      for (EventDateOption dateOption : dateOptions) {
        if (dateOption.getStart() == null) {
          throw new AgendaException(AgendaExceptionType.EVENT_DATE_OPTION_START_DATE_MANDATORY);
        }
        if (dateOption.getEnd() == null) {
          throw new AgendaException(AgendaExceptionType.EVENT_DATE_OPTION_END_DATE_MANDATORY);
        }
        if (dateOption.getStart().isAfter(dateOption.getEnd())) {
          throw new AgendaException(AgendaExceptionType.EVENT_DATE_OPTION_START_DATE_BEFORE_END_DATE);
        }
      }
    }
  }

  private ZonedDateTime getMaxEndDate(EventFilter eventFilter, ZoneId userTimeZone) {
    int initialSize = 0;
    int storageLimit = eventFilter.getLimit();
    List<Event> events = null;
    do {
      initialSize = events == null ? 0 : events.size();
      storageLimit *= 5;
      List<Long> eventIds = this.agendaEventStorage.getEventIds(eventFilter);
      events = getEventsList(eventIds, eventFilter.getStart(), eventFilter.getEnd(), userTimeZone, false, storageLimit);
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
                                    boolean ignoreComputeOccurrences,
                                    int limit) {
    List<Event> events = eventIds.stream().map(this::getEventById).collect(Collectors.toList());
    events.forEach(event -> {
      if (event.getRecurrence() == null || event.getTimeZoneId() == null) {
        // Adjust event for recurrent events after computing
        // List of occurrences
        adjustEventDatesForRead(event, timeZone);
      } else {
        // Adjust recurrent event date with original timeZone
        adjustEventDatesForRead(event, event.getTimeZoneId());
      }
    });
    return computeRecurrentEvents(events, startMinusADay, endPlusADay, timeZone, ignoreComputeOccurrences, limit);
  }

  private List<Event> computeEventsProperties(List<Long> eventIds,
                                              ZonedDateTime start,
                                              ZonedDateTime end,
                                              ZoneId timeZone,
                                              boolean ignoreComputeOccurrences,
                                              int limit,
                                              Identity userIdentity,
                                              ZonedDateTime startMinusADay,
                                              ZonedDateTime endPlusADay) {
    if (eventIds == null || eventIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Event> events = getEventsList(eventIds, startMinusADay, endPlusADay, timeZone, ignoreComputeOccurrences, limit);
    if (start != null && (end != null || limit > 0)) {
      events = filterEvents(events, start, end, limit);
    }
    computeEventsAcl(events, userIdentity);
    return events;
  }

  private void computeEventsAcl(List<Event> events, Identity userIdentity) {
    long userIdentityId = Long.parseLong(userIdentity.getId());
    Map<Long, EventPermission> eventPermissionsMap = new HashMap<>();
    events.forEach(event -> {
      long eventId = getEventIdOrParentId(event);
      EventPermission permission = eventPermissionsMap.get(eventId);
      if (permission == null) {
        boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
        boolean isEventAttendee = attendeeService.isEventAttendee(eventId, userIdentityId);
        permission = new EventPermission(canUpdateEvent, isEventAttendee);
        eventPermissionsMap.put(eventId, permission);
      }
      event.setAcl(permission);
    });
  }

  private List<Event> filterEvents(List<Event> events, ZonedDateTime start, ZonedDateTime end, int limit) {
    events = events.stream()
                   .filter(event -> {
                     if ((end == null || event.getStart().isBefore(end))
                         && (event.getEnd() == null || event.getEnd().isAfter(start))) {
                       Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
                       return calendar != null && !calendar.isDeleted();
                     }
                     return false;
                   })
                   .collect(Collectors.toList());
    sortEvents(events);
    if (limit > 0 && events.size() > limit) {
      events = events.subList(0, limit);
    }
    return events;
  }

  private void updateEventField(Event event, String fieldName, String fieldValue) throws AgendaException {
    switch (fieldName) {
      case "calendarId":
        long calendarId = Long.parseLong(fieldValue);
        if (calendarId <= 0) {
          throw new IllegalArgumentException("Event calendar id must be positive");
        }
        Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
        if (calendar == null) {
          throw new IllegalArgumentException("Event calendar with id " + calendarId + " wasn't found");
        }
        event.setCalendarId(calendarId);
        break;
      case "summary":
        event.setSummary(fieldValue);
        break;
      case "description":
        event.setDescription(fieldValue);
        break;
      case "location":
        event.setLocation(fieldValue);
        break;
      case "color":
        event.setColor(fieldValue);
        break;
      case "timeZoneId":
        if (StringUtils.isBlank(fieldValue)) {
          throw new IllegalArgumentException("Event timeZoneId is mandatory");
        }
        event.setTimeZoneId(ZoneId.of(fieldValue));
        break;
      case "start":
        if (StringUtils.isBlank(fieldValue)) {
          throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_MANDATORY);
        }
        ZonedDateTime startDate = event.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(fieldValue)
                                                   : AgendaDateUtils.parseRFC3339ToZonedDateTime(fieldValue,
                                                                                                 event.getTimeZoneId(),
                                                                                                 false);
        event.setStart(startDate);
        break;
      case "end":
        if (StringUtils.isBlank(fieldValue)) {
          throw new AgendaException(AgendaExceptionType.EVENT_END_DATE_MANDATORY);
        }
        ZonedDateTime endDate = event.isAllDay() ? AgendaDateUtils.parseAllDayDateToZonedDateTime(fieldValue)
                                                 : AgendaDateUtils.parseRFC3339ToZonedDateTime(fieldValue,
                                                                                               event.getTimeZoneId(),
                                                                                               false);
        event.setEnd(endDate);
        break;
      case "allDay":
        boolean allDay = Boolean.parseBoolean(fieldValue);
        event.setAllDay(allDay);
        break;
      case "availability":
        if (StringUtils.isBlank(fieldValue)) {
          event.setAvailability(EventAvailability.DEFAULT);
        } else {
          event.setAvailability(EventAvailability.valueOf(fieldValue.toUpperCase()));
        }
        break;
      case "status":
        if (StringUtils.isBlank(fieldValue)) {
          event.setStatus(EventStatus.CONFIRMED);
        } else {
          event.setStatus(EventStatus.valueOf(fieldValue.toUpperCase()));
        }
        break;
      case "allowAttendeeToUpdate":
        event.setAllowAttendeeToUpdate(Boolean.parseBoolean(fieldValue));
        break;
      case "allowAttendeeToInvite":
        event.setAllowAttendeeToInvite(Boolean.parseBoolean(fieldValue));
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  private void adjustEventDatesForRead(Event event, ZoneId timeZone) {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    ZoneId eventTimeZoneId = event.getTimeZoneId();
    if (timeZone == null) {
      if (eventTimeZoneId == null) {
        timeZone = ZoneOffset.UTC;
      } else {
        timeZone = eventTimeZoneId;
      }
    }

    if (start != null && end != null) {
      if (event.isAllDay()) {
        start = start.withZoneSameLocal(ZoneOffset.UTC)
                     .toLocalDate()
                     .atStartOfDay(timeZone);
        end = end.withZoneSameLocal(ZoneOffset.UTC)
                 .toLocalDate()
                 .atStartOfDay(timeZone)
                 .plusDays(1)
                 .minusSeconds(1);
      } else {
        start = start.withZoneSameInstant(timeZone);
        end = end.withZoneSameInstant(timeZone);
      }
      event.setStart(start);
      event.setEnd(end);
    }

    if (event.getStatus() == EventStatus.CONFIRMED) {
      EventRecurrence recurrence = event.getRecurrence();
      if (recurrence != null) {
        if (recurrence.getUntil() != null) {
          LocalDate recurrenceUntil = recurrence.getUntil();
          // end of until day in User TimeZone
          recurrenceUntil = LocalDate.of(recurrenceUntil.getYear(),
                                         recurrenceUntil.getMonthValue(),
                                         recurrenceUntil.getDayOfMonth());
          recurrence.setUntil(recurrenceUntil);
        }
        ZonedDateTime overallStart = recurrence.getOverallStart();
        ZonedDateTime overallEnd = recurrence.getOverallEnd();
        if (event.isAllDay()) {
          overallStart = overallStart.withZoneSameInstant(eventTimeZoneId)
                                     .toLocalDate()
                                     .atStartOfDay(timeZone);
          overallEnd = overallEnd == null ? null
                                          : overallEnd.withZoneSameInstant(eventTimeZoneId)
                                                      .toLocalDate()
                                                      .atStartOfDay(timeZone)
                                                      .plusDays(1)
                                                      .minusSeconds(1);
        } else {
          overallStart = overallStart.withZoneSameInstant(timeZone);
          overallEnd = overallEnd == null ? null
                                          : overallEnd.withZoneSameInstant(timeZone);
        }
        recurrence.setOverallStart(overallStart);
        recurrence.setOverallEnd(overallEnd);
      }
    }
  }

  private void adjustEventDatesForWrite(Event event) {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    if (start != null && end != null) {
      if (event.isAllDay()) {
        start = start.toLocalDate().atStartOfDay(ZoneOffset.UTC);
        end = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
      } else {
        start = start.withZoneSameInstant(ZoneOffset.UTC);
        end = end.withZoneSameInstant(ZoneOffset.UTC);
      }
      event.setStart(start);
      event.setEnd(end);
    }
  }

  private List<Event> computeRecurrentEvents(List<Event> events,
                                             ZonedDateTime start,
                                             ZonedDateTime end,
                                             ZoneId userTimezone,
                                             boolean ignoreComputeOccurrences,
                                             int limit) {
    List<Event> computedEvents = new ArrayList<>();
    for (Event event : events) {
      if (ignoreComputeOccurrences || event.getRecurrence() == null || event.getStatus() != EventStatus.CONFIRMED) {
        computedEvents.add(event);
      } else {
        if (userTimezone == null) {
          userTimezone = ZoneOffset.UTC;
        }
        List<Event> occurrences = getEventOccurrencesInPeriod(event, start, end, userTimezone, limit);
        if (occurrences != null && !occurrences.isEmpty()) {
          computedEvents.addAll(occurrences);
        }
      }
    }
    return computedEvents;
  }

  private void sortEvents(List<Event> computedEvents) {
    computedEvents.sort((event1, event2) -> ObjectUtils.compare(event1.getStart(), event2.getStart()));
  }

  private List<Event> filterExceptionalEvents(Event recurrentEvent,
                                              List<Event> occurrences,
                                              ZonedDateTime start,
                                              ZonedDateTime end) {
    List<Long> exceptionalOccurenceEventIds = agendaEventStorage.getExceptionalOccurenceIdsByPeriod(recurrentEvent.getId(),
                                                                                                    start,
                                                                                                    end);
    List<Event> exceptionalEvents = exceptionalOccurenceEventIds == null
        || exceptionalOccurenceEventIds.isEmpty() ? Collections.emptyList()
                                                  : exceptionalOccurenceEventIds.stream()
                                                                                .map(this::getEventById)
                                                                                .collect(Collectors.toList());
    return occurrences.stream()
                      .filter(occurrence -> {
                        ZonedDateTime occurrenceId = occurrence.getOccurrence().getId();
                        LocalDate occurrenceDate = occurrenceId
                                                               .withZoneSameInstant(ZoneOffset.UTC)
                                                               .toLocalDate();
                        return exceptionalEvents.stream()
                                                .noneMatch(exceptionalOccurence -> {
                                                  ZonedDateTime exceptionalOccurenceId = exceptionalOccurence.getOccurrence()
                                                                                                             .getId();
                                                  LocalDate exceptionalOccurenceDate =
                                                                                     exceptionalOccurenceId.withZoneSameInstant(ZoneOffset.UTC)
                                                                                                           .toLocalDate();
                                                  return occurrenceDate.isEqual(exceptionalOccurenceDate)
                                                      // Added for retro
                                                      // compatibility with
                                                      // previous occurrenceId
                                                      // computing algorithm
                                                      || Math.abs(exceptionalOccurenceId.toEpochSecond()
                                                          - occurrenceId.toEpochSecond()) < 43200;
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

  private List<EventAttendee> cleanupAttendeeIds(List<EventAttendee> attendees) {
    if (attendees != null && !attendees.isEmpty()) {
      return attendees.stream().map(attendee -> {
        attendee = attendee.clone();
        attendee.setId(0);
        return attendee;
      }).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private List<EventConference> cleanupConferenceIds(List<EventConference> conferences) {
    if (conferences != null && !conferences.isEmpty()) {
      return conferences.stream().map(conference -> {
        conference = conference.clone();
        conference.setId(0);
        return conference;
      }).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private List<EventReminder> cleanupReminderIds(List<EventReminder> reminders) {
    if (reminders != null && !reminders.isEmpty()) {
      return reminders.stream().map(reminder -> {
        reminder = reminder.clone();
        reminder.setId(0);
        return reminder;
      }).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private ZonedDateTime getMinOptionStartDate(List<EventDateOption> dateOptions) {
    return dateOptions.stream()
                      .min((option1, option2) -> ObjectUtils.compare(option1.getStart(), option2.getStart()))
                      .map(EventDateOption::getStart)
                      .orElse(null);
  }

  private ZonedDateTime getMaxOptionEndDate(List<EventDateOption> dateOptions) {
    return dateOptions.stream()
                      .max((option1, option2) -> ObjectUtils.compare(option1.getEnd(), option2.getEnd()))
                      .map(EventDateOption::getEnd)
                      .orElse(null);
  }

}
