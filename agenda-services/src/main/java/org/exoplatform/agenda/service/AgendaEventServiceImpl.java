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
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.CalendarEditorChange;
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
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;

import static org.exoplatform.agenda.util.Utils.*;
import static org.exoplatform.agenda.util.Utils.EVENT_METADATA_KEY;

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

  private MetadataService              metadataService;

  private CalendarShareAccess          calendarShareAccess = new CalendarShareAccess();

  private CalendarSubscriptionAccess   calendarSubscriptionAccess = new CalendarSubscriptionAccess();

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
                                ListenerService listenerService,
                                MetadataService metadataService) {
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
    this.metadataService = metadataService;
  }

  /**
   * Replaces how calendar shares are looked up, for tests: the default
   * resolves the share service from the container on first use.
   *
   * @param calendarShareAccess the lookup
   */
  public void setCalendarShareAccess(CalendarShareAccess calendarShareAccess) {
    this.calendarShareAccess = calendarShareAccess;
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

    EventAccess access = getEventAccess(event, userIdentityId);
    if (access != EventAccess.NONE) {
      adjustEventDatesForRead(event, timeZone);
      boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
      event.setAcl(new EventPermission(canUpdateEvent, isEventAttendee));
      return maskForAccess(event, access);
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
    // The parent is what a computed occurrence is read from, so it is what
    // the reader must be allowed to read: an occurrence with no exceptional
    // row of its own used to be handed out to anyone naming the parent's id
    EventAccess access = getEventAccess(recurrentEvent, userIdentityId);
    if (access == EventAccess.NONE) {
      throw new IllegalAccessException("User with identity id " + userIdentityId + " is not allowed to access event with id "
          + parentEventId);
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
      event = maskForAccess(event, access);
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
    if (event.getVisibility() == null) {
      event.setVisibility(EventVisibility.DEFAULT);
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

    Event parentEvent = null;
    if (event.getParentId() > 0) {
      parentEvent = agendaEventStorage.getEventById(event.getParentId());
      if (parentEvent == null) {
        throw new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND);
      }
    }

    boolean canCreateCalendarEvents = canCreateEvent(calendar, userIdentityId);
    if (!canCreateCalendarEvents) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't create an event in calendar " + calendar.getTitle());
    }
    checkCanCreateOccurrence(parentEvent, userIdentityId);

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
                                    event.getVisibility(),
                                    event.getStatus(),
                                    event.getRecurrence(),
                                    event.getOccurrence(),
                                    null,
                                    event.isAllowAttendeeToUpdate(),
                                    event.isAllowAttendeeToInvite());

    Event createdEvent = agendaEventStorage.createEvent(eventToCreate);
    createOrUpdateEventProperties(event.getParameters(), createdEvent);
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
    notifyOwnerOfEditorChange(createdEvent,
                              userIdentityId,
                              CalendarEditorChange.Kind.ADDED,
                              writeRightOf(createdEvent, userIdentityId));
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

    checkCanMoveEvent(storedEvent, calendar, userIdentityId);

    if (event.getVisibility() == null) {
      // Not stated keeps what is stored, never DEFAULT: visibility decides what
      // the calendar-link feed publishes, and a caller that builds its Event
      // from scratch (caldav-integration's inbound sync) must not un-mask a
      // PRIVATE event by omission. An explicit reset goes through
      // updateEventFields, which states DEFAULT itself.
      event.setVisibility(storedEvent.getVisibility() == null ? EventVisibility.DEFAULT : storedEvent.getVisibility());
    }
    if (event.getAvailability() == null) {
      // The same for the availability, the other half of the form's row: an
      // omission must not turn an event the user marked Free back into Busy,
      // in eXo and in the published feed's TRANSP.
      event.setAvailability(storedEvent.getAvailability() == null ? EventAvailability.DEFAULT
                                                                  : storedEvent.getAvailability());
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
                                    event.getVisibility(),
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
    createOrUpdateEventProperties(event.getParameters(), updatedEvent);
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
    // Snapshotted before saving, because saving removes the rows of attendees
    // dropped from the event: a listener reading the attendees afterwards would
    // never see the users the invitation just disappeared for
    List<EventAttendee> previousAttendees = attendeeService.getEventAttendees(eventId).getEventAttendees();
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

    // Carries the attendees before and after the change, so that a listener
    // reaches both those the update invited and those it removed
    List<EventAttendee> concernedAttendees = new ArrayList<>(previousAttendees);
    concernedAttendees.addAll(attendeeService.getEventAttendees(eventId).getEventAttendees());
    Utils.broadcastEvent(listenerService,
                         Utils.POST_UPDATE_AGENDA_EVENT_EVENT,
                         eventModifications,
                         new EventAttendeeList(concernedAttendees));
    notifyOwnerOfEditorChange(updatedEvent,
                              userIdentityId,
                              CalendarEditorChange.Kind.CHANGED,
                              writeRightOf(updatedEvent, userIdentityId));

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
      updateEventField(event, fieldName, fieldValue, userIdentityId);
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
    notifyOwnerOfEditorChange(event, userIdentityId, CalendarEditorChange.Kind.CHANGED, writeRightOf(event, userIdentityId));
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
    // Read while the event and its attendee rows are still there: after the
    // deletion an attendee allowed to update would read as a share editor
    EventWriteRight right = writeRightOf(event, userIdentityId);

    agendaEventStorage.deleteEventById(eventId);

    event.setModifierId(userIdentityId);

    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.DELETED);
    attendeeService.sendInvitations(event, eventAttendeeList.getEventAttendees(), eventModifications);
    // Carries the attendees snapshotted above: the rows are deleted along with
    // the event, so a listener that needs to reach them can no longer look them
    // up by event id
    Utils.broadcastEvent(listenerService, Utils.POST_DELETE_AGENDA_EVENT_EVENT, eventModifications, eventAttendeeList);
    notifyOwnerOfEditorChange(event, userIdentityId, CalendarEditorChange.Kind.REMOVED, right);
    return event;
  }

  /**
   * Replaces how the calendars of an owner's subscriptions are looked up, for
   * tests: the default resolves the subscription service from the container on
   * first use.
   *
   * @param calendarSubscriptionAccess the lookup
   */
  public void setCalendarSubscriptionAccess(CalendarSubscriptionAccess calendarSubscriptionAccess) {
    this.calendarSubscriptionAccess = calendarSubscriptionAccess;
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

    // The calendars other users shared with the reader (EXO-90357): named by
    // the client — each name checked against the reader, since an unchecked
    // id would read any calendar — or, when the client names none and asks
    // for no particular owner, every calendar shared with them
    List<Long> calendarIds = sharedCalendarIdsToRead(eventFilter, userIdentityId);

    long attendeeId = eventFilter.getAttendeeId();
    if (attendeeId > 0) {
      if (!String.valueOf(attendeeId).contentEquals(userIdentity.getId())) {
        throw new IllegalAccessException("User '" + userIdentity.getId() + "' is not allowed to access calendar of identity '"
            + attendeeId + "'");
      }
      List<Long> attendeeSpaceIds = Utils.getCalendarOwnersOfUser(spaceService, identityManager, userIdentity);
      eventFilter.setAttendeeWithSpacesIds(attendeeSpaceIds);
      // The events a calendar subscription imported are nobody's invitation
      // (EXO-90373): a subscribed calendar is a read-only copy of someone
      // else's calendar, so a space's import writes no attendee row, and an
      // attendee-keyed listing — the personal agenda's default view — would
      // hide them. They belong to their owner, so the subscribed calendars of
      // the owners this listing reads are added to it, whatever the attendee
      // criteria say. Those owners are the ones the reader was already allowed
      // for, checked above, so nothing new is read: unticking the space in the
      // left panel takes its owner out, and its subscribed events with it.
      //
      // Unless the caller asked to be spared them: a reader computing when
      // somebody is busy, or one listing a single attendee response, gets
      // nothing right from events nobody was invited to, and both would pay
      // for them out of their own event budget
      // (EventFilter.subscribedCalendarsExcluded).
      if (!eventFilter.isSubscribedCalendarsExcluded()) {
        List<Long> readOwners = ownerIds == null ? attendeeSpaceIds : ownerIds;
        List<Long> subscribed = calendarSubscriptionAccess.getSubscriptionCalendarIds(readOwners);
        if (!subscribed.isEmpty()) {
          calendarIds = Stream.concat(calendarIds.stream(), subscribed.stream()).distinct().toList();
        }
      }
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
      maxEndDateFilter.setCalendarIds(calendarIds);
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
    requestEventFilter.setCalendarIds(calendarIds);
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
    return getEventAccess(event, identityId) != EventAccess.NONE;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The reading that existed before sharing — the reader may see the calendar,
   * or is invited — is {@link EventAccess#FULL}; a reader admitted only by a
   * share record (EXO-90357) is {@link EventAccess#SHARED} when the share is
   * for viewing and {@link EventAccess#SHARED_EDIT} when it is for editing
   * (EXO-90378), and only a user can be either: a share names a user, never a
   * space or a guest. The share is asked last, so nobody who could read before
   * is ever read as a sharee, and it is asked <b>once</b> — the level comes
   * back with the answer, from the reader's own cached map.
   */
  @Override
  public EventAccess getEventAccess(Event event, long identityId) {
    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null || calendar.isDeleted()) {
      return EventAccess.NONE;
    }

    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    if (identity == null) {
      return EventAccess.NONE;
    }
    boolean user = StringUtils.equals(OrganizationIdentityProvider.NAME, identity.getProviderId());
    if (user && Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), identityId)) {
      return EventAccess.FULL;
    }
    if (attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId)) {
      return EventAccess.FULL;
    }
    if (user) {
      CalendarShareLevel level = calendarShareAccess.levelOf(calendarId, identityId);
      if (level != null) {
        return accessOf(level);
      }
    }
    return EventAccess.NONE;
  }

  /**
   * How a share of a given level is read (EXO-90378). The one place the two
   * vocabularies meet, so a level added later has a single line to answer for
   * rather than a condition repeated across the read paths.
   *
   * @param level the level of the share, never null here
   * @return {@link EventAccess#SHARED_EDIT} for an edit share,
   *         {@link EventAccess#SHARED} otherwise
   */
  private static EventAccess accessOf(CalendarShareLevel level) {
    return level == CalendarShareLevel.EDIT ? EventAccess.SHARED_EDIT : EventAccess.SHARED;
  }

  /**
   * Renders an event for the access its reader has, through the one helper
   * every read path goes through, {@link Utils#maskForAccess(Event, EventAccess)}:
   * the access is stamped on the event, and a private event read through a
   * share alone becomes busy time (EXO-90357).
   *
   * @param event the event, a copy of the cached one
   * @param access how the reader may read it
   * @return the same event, masked when it must be
   */
  static Event maskForAccess(Event event, EventAccess access) {
    return Utils.maskForAccess(event, access);
  }

  /**
   * The shared calendars a listing reads (EXO-90357): the ones the client
   * names, each checked against the reader, or — when it names none and
   * restricts no owner — every calendar shared with the reader.
   *
   * @param eventFilter the filter as the client sent it
   * @param userIdentityId identity identifier of the reader
   * @return the calendar identifiers to read on top of the owner criteria,
   *         empty for none
   * @throws IllegalAccessException when a named calendar is neither
   *           accessible to the reader nor shared with them
   */
  private List<Long> sharedCalendarIdsToRead(EventFilter eventFilter, long userIdentityId) throws IllegalAccessException {
    List<Long> named = eventFilter.getCalendarIds();
    if (named == null) {
      return CollectionUtils.isEmpty(eventFilter.getOwnerIds()) ? calendarShareAccess.getSharedCalendarIds(userIdentityId)
                                                                : Collections.emptyList();
    }
    List<Long> shared = null;
    for (Long calendarId : named) {
      Calendar calendar = calendarId == null || calendarId <= 0 ? null : agendaCalendarService.getCalendarById(calendarId);
      if (calendar == null || calendar.isDeleted()) {
        throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to access calendar '" + calendarId + "'");
      }
      if (Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
        continue;
      }
      if (shared == null) {
        shared = calendarShareAccess.getSharedCalendarIds(userIdentityId);
      }
      if (!shared.contains(calendarId)) {
        throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to access calendar '" + calendarId + "'");
      }
    }
    return new ArrayList<>(named);
  }


  /**
   * Tells the calendar's owner that a colleague they shared it with for
   * editing wrote in it (EXO-90378), and tells them nothing otherwise.
   * <p>
   * The decision lives here, in the service, because it is the one place that
   * knows by what right the writer wrote: {@link EventWriteRight#SHARE_EDITOR}
   * and nothing else. A change by the owner, by a space manager, by the
   * event's creator or by an attendee allowed to update raises nothing — the
   * owner is told about the one case they cannot otherwise see coming.
   * <p>
   * Everything the notification needs is captured now, the summary included: a
   * deletion leaves no event to read afterwards, and the three kinds must read
   * alike. A failure to broadcast is never a failure to write.
   *
   * @param event the event, as it stood when the change was made
   * @param userIdentityId {@link Identity} identifier of the writer
   * @param kind what they did
   * @param right the right they wrote by, taken <b>before</b> the write where
   *          the write removes what the right is read from — a deletion takes
   *          the attendee rows with it, and an attendee allowed to update
   *          would read as a share editor afterwards
   */
  private void notifyOwnerOfEditorChange(Event event,
                                         long userIdentityId,
                                         CalendarEditorChange.Kind kind,
                                         EventWriteRight right) {
    if (event == null || right != EventWriteRight.SHARE_EDITOR) {
      return;
    }
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    if (calendar == null || calendar.isDeleted() || calendar.getOwnerId() == userIdentityId) {
      return;
    }
    CalendarEditorChange change = new CalendarEditorChange(calendar.getId(),
                                                           calendar.getOwnerId(),
                                                           userIdentityId,
                                                           event.getId(),
                                                           StringUtils.defaultString(event.getSummary()),
                                                           kind);
    Utils.broadcastEvent(listenerService, CALENDAR_EDITED_BY_SHAREE_EVENT, change, calendar.getOwnerId());
  }

  /**
   * By what right a user may write an event (EXO-90378) — the one predicate
   * behind every write in this service, and therefore behind REST, drag and
   * drop, the ACL plugin and the MCP write tools, none of which check anything
   * of their own.
   * <p>
   * The rights are asked in the order in which they were added, and the share
   * is asked <b>last</b>: a user who could already write the event by any
   * older right is never answered {@link EventWriteRight#SHARE_EDITOR}, so the
   * share's own restriction — an editor may not move an event out of the
   * calendar, {@link #checkCanMoveEvent} — never narrows a right somebody
   * already had.
   */
  private enum EventWriteRight {

    /** No right at all: the write is refused. */
    NONE,

    /** The user owns the calendar, or manages the space that does. */
    CALENDAR,

    /** The user created the event and may still see its calendar. */
    CREATOR,

    /** The user attends the event, which lets its attendees update it. */
    ATTENDEE,

    /**
     * The user's only right is a calendar share granted for editing
     * (EXO-90378): they write events in the owner's personal calendar as the
     * owner would, and may not move one out of it.
     */
    SHARE_EDITOR
  }

  /**
   * By what right a user may write an event, {@link EventWriteRight#NONE} for
   * none.
   *
   * @param event the event, as stored
   * @param userIdentityId {@link Identity} identifier of the user
   * @return the right, never null
   */
  private EventWriteRight writeRightOf(Event event, long userIdentityId) {
    // The calendar is read first, and always: an event of a subscribed calendar
    // (EXO-90278) is the feed's, and neither its creator nor its owner updates,
    // moves or deletes it. The read is served by the calendar cache.
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    if (calendar == null || calendar.isDeleted() || calendar.isSubscription()) {
      return EventWriteRight.NONE;
    }
    if (userIdentityId == event.getCreatorId()
        && Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
      // A creator who can still access the calendar
      return EventWriteRight.CREATOR;
    }
    if (event.isAllowAttendeeToUpdate()
        && attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId)) {
      return EventWriteRight.ATTENDEE;
    }
    if (Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
      return EventWriteRight.CALENDAR;
    }
    return isSharedForEdit(calendar, userIdentityId) ? EventWriteRight.SHARE_EDITOR : EventWriteRight.NONE;
  }

  /**
   * Whether a calendar is one user's personal calendar that its owner shared
   * with another user for editing (EXO-90378).
   * <p>
   * Personal calendars only, as EXO-90357: a space calendar is read by its
   * members and written by its redactors and managers, and is not shareable.
   * Users only on both ends: a share names a user, never a space, never a
   * guest. The level comes from {@link CalendarShareAccess}, which answers no
   * level — never the narrower one — when the share service cannot be reached,
   * so an unreachable share service admits no editor.
   *
   * @param calendar the calendar, already known to exist and not to be a
   *          subscription
   * @param userIdentityId {@link Identity} identifier of the user
   * @return true when the user holds an edit share on it
   */
  private boolean isSharedForEdit(Calendar calendar, long userIdentityId) {
    Identity owner = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    if (owner == null || !owner.isUser()) {
      return false;
    }
    Identity user = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (user == null || !user.isUser()) {
      return false;
    }
    return calendarShareAccess.isSharedForEditWith(calendar.getId(), userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canUpdateEvent(Event event, long userIdentityId) {
    return writeRightOf(event, userIdentityId) != EventWriteRight.NONE;
  }

  /**
   * {@inheritDoc} Never in a subscribed calendar (EXO-90278), whose events come
   * from its feed alone. A colleague holding an edit share on a personal
   * calendar creates in it as its owner would (EXO-90378).
   */
  @Override
  public boolean canCreateEvent(Calendar calendar, long userIdentityId) {
    if (calendar.isSubscription()) {
      return false;
    }
    return Utils.canCreateEvent(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)
        || isSharedForEdit(calendar, userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventSearchResult> search(AgendaEventSearchFilter filter) {
    if (filter.getUserTimeZone() == null) {
      filter.setUserTimeZone(ZoneOffset.UTC);
    }
    // The calendars shared with the reader (EXO-90357) are the service's to
    // name, never the client's: set here from the share records, and only
    // when the search is not restricted to spaces — a shared calendar is
    // nobody's space
    List<Long> sharedCalendarIds = CollectionUtils.isEmpty(filter.getSpaceIdentityIds())
                                                                                        ? calendarShareAccess.getSharedCalendarIds(filter.getCurrentUserId())
                                                                                        : Collections.emptyList();
    filter.setSharedCalendarIds(sharedCalendarIds);

    List<EventSearchResult> searchResults = agendaSearchConnector.search(filter);
    final ZoneId timeZone = filter.getUserTimeZone();
    return searchResults.stream().map(event -> maskSearchResult(event, sharedCalendarIds, filter.getCurrentUserId())).map(event -> {
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
          if (occurrenceEvent.getOccurrence().isExceptional() && !event.isMasked()) {
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
                                      int limit) throws Exception {
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
    List<Long> calenderIds = getUserCalenders(userIdentity);
    List<Long> eventIds = this.agendaEventStorage.getPendingEventIds(userIdentityId,
                                                                     ownerIds,
                                                                     attendeeIds,
                                                                     calenderIds,
                                                                     offset,
                                                                     limit);
    return computeEventsProperties(eventIds, null, null, userTimeZone, true, limit, userIdentity, null, null);
  }

  @Override
  public long countPendingEvents(List<Long> ownerIds, long userIdentityId) throws Exception {
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
    List<Long> calenderIds = getUserCalenders(userIdentity);
    return this.agendaEventStorage.countPendingEvents(userIdentityId, ownerIds, attendeeIds, calenderIds);
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

  /**
   * Sets each event's permissions for the reader, and masks the private events
   * of the calendars the reader sees only through a <b>view</b> share
   * (EXO-90357); an edit share (EXO-90378) reads the calendar in full. The
   * access is decided once per parent event, like the permissions, and only
   * an event of a shared calendar pays the calendar check: the reader's share
   * levels are one cache hit for the whole listing.
   *
   * @param events the events read, copies of the cached ones
   * @param userIdentity the reader
   */
  private void computeEventsAcl(List<Event> events, Identity userIdentity) {
    long userIdentityId = Long.parseLong(userIdentity.getId());
    Map<Long, EventPermission> eventPermissionsMap = new HashMap<>();
    Map<Long, EventAccess> eventAccessMap = new HashMap<>();
    Map<Long, CalendarShareLevel> shareLevels = calendarShareAccess.levelsOf(userIdentityId);
    events.forEach(event -> {
      long eventId = getEventIdOrParentId(event);
      EventPermission permission = eventPermissionsMap.get(eventId);
      if (permission == null) {
        boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
        boolean isEventAttendee = attendeeService.isEventAttendee(eventId, userIdentityId);
        permission = new EventPermission(canUpdateEvent, isEventAttendee);
        eventPermissionsMap.put(eventId, permission);
        CalendarShareLevel level = shareLevels.get(event.getCalendarId());
        boolean sharedOnly = level != null
            && !isEventAttendee
            && !canAccessCalendarOf(event, userIdentityId);
        eventAccessMap.put(eventId, sharedOnly ? accessOf(level) : EventAccess.FULL);
      }
      event.setAcl(permission);
      maskForAccess(event, eventAccessMap.get(eventId));
    });
  }

  /**
   * Masks a search hit the reader sees only through a share, when the stored
   * event is private (EXO-90357): the index carries the words that matched,
   * and the hit is the one place those words would leave through. Read
   * through the same access primitive as every other path; only a hit of a
   * shared calendar pays the stored read.
   *
   * @param result the hit as the index answered it
   * @param sharedCalendarIds the calendars shared with the reader
   * @param userIdentityId identity identifier of the reader
   * @return the same hit, masked when it must be
   */
  private EventSearchResult maskSearchResult(EventSearchResult result, List<Long> sharedCalendarIds, long userIdentityId) {
    if (result == null || !sharedCalendarIds.contains(result.getCalendarId())) {
      return result;
    }
    Event stored = agendaEventStorage.getEventById(result.getId());
    if (stored == null) {
      return result;
    }
    result.setVisibility(stored.getVisibility());
    if (maskForAccess(result, getEventAccess(stored, userIdentityId)).isMasked()) {
      result.setExcerpts(Collections.emptyList());
    }
    return result;
  }

  /**
   * Whether the reader may see the calendar of an event by their own right —
   * their calendar, or a space they belong to.
   *
   * @param event the event
   * @param userIdentityId identity identifier of the reader
   * @return true when they may
   */
  private boolean canAccessCalendarOf(Event event, long userIdentityId) {
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    return calendar != null && !calendar.isDeleted()
        && Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
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

  private List<Long> getUserCalenders(Identity userIdentity) throws Exception {
    List<Long> calenderIds = this.agendaEventStorage.getUserEventCalenderIds(Long.parseLong(userIdentity.getId()));
    calenderIds.addAll(this.agendaCalendarService.getCalendars(0,
                                                               this.agendaCalendarService.countCalendars(userIdentity.getRemoteId()),
                                                               userIdentity.getRemoteId())
                                                 .stream()
                                                 .map(Calendar::getId)
                                                 .toList());
    return filterUserCalenders(calenderIds);
  }

  private List<Long> filterUserCalenders(List<Long> calendersIds) {
    return calendersIds.stream().distinct().filter(id -> {
      Calendar calendar = agendaCalendarService.getCalendarById(id);
      return calendar != null && !calendar.isDeleted();
    }).collect(Collectors.toList());
  }

  /**
   * Checks that a user may file an event into a calendar. Keeping the event in
   * its current calendar needs nothing more than the right to update the event,
   * which the caller has already checked. Moving it to another calendar
   * additionally requires the right to create events in that target calendar,
   * derived from the stored calendar row and never from the request: being
   * allowed to update an event must not grant filing it into a calendar where
   * the user can't add events, such as a space where they aren't a redactor or
   * another user's personal calendar. Both update paths go through this
   * method: {@code updateEvent} and the {@code calendarId} field of
   * {@code updateEventFields}. An exceptional occurrence created through
   * {@code createEvent} is checked by {@code checkCanCreateOccurrence} instead.
   *
   * @param storedEvent the event as it is stored, carrying the calendar it is
   *          currently filed in
   * @param targetCalendar the stored calendar the event is filed into, already
   *          checked to exist
   * @param userIdentityId the {@link Identity} identifier of the user moving the
   *          event
   * @throws IllegalAccessException when the calendar changes and the user can't
   *           create events in the target calendar, or their only right over
   *           the event is an edit share on the calendar it leaves
   */
  private void checkCanMoveEvent(Event storedEvent,
                                 Calendar targetCalendar,
                                 long userIdentityId) throws IllegalAccessException {
    if (storedEvent.getCalendarId() == targetCalendar.getId()) {
      return;
    }
    // A colleague whose only right over the event is an edit share (EXO-90378)
    // may write it where it is and nowhere else: the event is the owner's, and
    // taking it out of their calendar — into the editor's own, or anywhere they
    // may create — would remove it from the calendar its owner shared. Asked
    // before the target check, which such a move would often pass. Anyone with
    // an older right is unaffected: the share is the last right writeRightOf
    // answers, so it is answered only when there is no other.
    if (writeRightOf(storedEvent, userIdentityId) == EventWriteRight.SHARE_EDITOR) {
      throw new IllegalAccessException("User '" + userIdentityId + "' edits calendar " + storedEvent.getCalendarId()
          + " through a share and can't move event " + storedEvent.getId() + " out of it");
    }
    if (!canCreateEvent(targetCalendar, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't move event " + storedEvent.getId() + " to calendar "
          + targetCalendar.getId());
    }
  }

  /**
   * Checks that a user may create an exceptional occurrence of a recurring
   * event, which is what {@code createEvent} does when the event carries a
   * parent. Such an occurrence replaces the computed one in the series for
   * every reader, whatever calendar it is filed into, so it requires the right
   * to update the series, as editing that occurrence in place would. The right
   * to create events in the target calendar is checked by the caller. Without
   * this check, anyone who can add events to their own calendar could remove an
   * occurrence of any recurring event from the views of its owner and
   * attendees, knowing only its identifier.
   *
   * @param parentEvent the stored recurring event the occurrence belongs to, or
   *          null when the created event has no parent
   * @param userIdentityId the {@link Identity} identifier of the user creating
   *          the event
   * @throws IllegalAccessException when the event has a parent that the user
   *           can't update
   */
  private void checkCanCreateOccurrence(Event parentEvent, long userIdentityId) throws IllegalAccessException {
    if (parentEvent != null && !canUpdateEvent(parentEvent, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't create an occurrence of event "
          + parentEvent.getId());
    }
  }

  /**
   * Applies one patched field to an event, without storing it. The caller has
   * already checked that the user may update the event; moving it to another
   * calendar ({@code calendarId}) additionally requires the right to create
   * events in that calendar, checked here before the field is applied.
   *
   * @param event the event to modify, still carrying its stored calendar
   * @param fieldName the name of the field to patch
   * @param fieldValue the new value of the field, as sent by the client
   * @param userIdentityId the {@link Identity} identifier of the user patching
   *          the event
   * @throws AgendaException when a date field is missing
   * @throws IllegalAccessException when the event is moved to a calendar in
   *           which the user can't create events
   */
  private void updateEventField(Event event, String fieldName, String fieldValue, long userIdentityId) throws AgendaException,
                                                                                                      IllegalAccessException {
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
        checkCanMoveEvent(event, calendar, userIdentityId);
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
      case "visibility":
        if (StringUtils.isBlank(fieldValue)) {
          event.setVisibility(EventVisibility.DEFAULT);
        } else {
          event.setVisibility(EventVisibility.valueOf(fieldValue.toUpperCase()));
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

  private void createOrUpdateEventProperties(Map<String, String> properties, Event event) {
    if (properties == null) {
      properties = new HashMap<>();
    }
    MetadataObject metadataObject = new MetadataObject(EVENT_METADATA_NAME, String.valueOf(event.getId()));
    List<MetadataItem> metadataItems =
        metadataService.getMetadataItemsByMetadataAndObject(EVENT_METADATA_KEY, metadataObject);
    if (CollectionUtils.isEmpty(metadataItems)) {
      try {
        metadataService.createMetadataItem(metadataObject, EVENT_METADATA_KEY, properties, event.getCreatorId());
      } catch (Exception e) {
        LOG.error("Failed to save metadata for eventId={}, creatorId={}, properties={}. Operation continues without metadata.",
                  event.getId(),
                  event.getCreatorId(),
                  properties,
                  e);
      }
    } else {
      MetadataItem metadataItem = metadataItems.getFirst();
      Map<String, String> existingProperties = metadataItem.getProperties() != null
          ? new HashMap<>(metadataItem.getProperties())
          : new HashMap<>();
      existingProperties.putAll(properties);
      metadataItem.setProperties(existingProperties);
      metadataService.updateMetadataItem(metadataItem, event.getModifierId());
    }
  }

}
