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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.TimeZone;
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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;

public class AgendaEventServiceImpl implements AgendaEventService {

  private static final Log             LOG = ExoLogger.getLogger(AgendaEventServiceImpl.class);

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventInvitationService agendaEventInvitationService;

  private AgendaEventReminderService   agendaEventReminderService;

  private AgendaEventStorage           agendaEventStorage;

  private IdentityManager              identityManager;

  private SpaceService                 spaceService;

  public AgendaEventServiceImpl(AgendaCalendarService agendaCalendarService,
                                AgendaEventInvitationService agendaEventInvitationService,
                                AgendaEventReminderService agendaEventReminderService,
                                AgendaEventStorage agendaEventStorage,
                                IdentityManager identityManager,
                                SpaceService spaceService) {
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventInvitationService = agendaEventInvitationService;
    this.agendaEventReminderService = agendaEventReminderService;
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
    computeAcl(event, username);
    return event;
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
    if (event.getAvailability() == null) {
      event.setAvailability(EventAvailability.DEFAULT);
    }
    if (event.getStatus() == null) {
      event.setStatus(EventStatus.CONFIRMED);
    }

    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId, username);
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    }
    if (calendar.getAcl() == null || !calendar.getAcl().isCanEdit()) {
      throw new IllegalAccessException("User '" + username + "' can't create an event in calendar " + calendar.getTitle());
    }

    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + username + "' doesn't exist");
    }
    long userIdentityId = Long.parseLong(userIdentity.getId());

    EventRecurrence recurrence = event.getRecurrence();
    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
      recurrence = null;
      event.setRecurrence(null);
    }

    adjustEventDates(event);
    computeRecurrentEventDates(event);

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
                                    null);

    Event createdEvent = agendaEventStorage.createEvent(eventToCreate,
                                                        attachments,
                                                        conferences,
                                                        attendees,
                                                        reminders);
    if (sendInvitation && attendees != null) {
      sendInvitations(createdEvent, attendees, userIdentityId);
    }
    return createdEvent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateEvent(Event event,
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
    if (event.getAvailability() == null) {
      event.setAvailability(EventAvailability.DEFAULT);
    }
    if (event.getStatus() == null) {
      event.setStatus(EventStatus.CONFIRMED);
    }

    Event storedEvent = getEventById(event.getId());

    long calendarId = event.getCalendarId();
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId, username);
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    }
    if (calendar.getAcl() == null || !calendar.getAcl().isCanEdit()) {
      throw new IllegalAccessException("User '" + username + "' can't create an event in calendar " + calendar.getTitle());
    }

    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalAccessException("User '" + username + "' doesn't exist");
    }
    long userIdentityId = Long.parseLong(userIdentity.getId());

    EventRecurrence recurrence = event.getRecurrence();
    EventOccurrence occurrence = event.getOccurrence();
    if (occurrence != null && occurrence.getId() != null) {
      recurrence = null;
      event.setRecurrence(null);
    }

    adjustEventDates(event);
    computeRecurrentEventDates(event);

    Event eventToUpdate = new Event(0,
                                    event.getParentId(),
                                    event.getRemoteId(),
                                    event.getRemoteProviderId(),
                                    calendarId,
                                    storedEvent.getCreatorId(),
                                    userIdentityId,
                                    storedEvent.getCreated(),
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
                                    null);

    agendaEventStorage.updateEvent(eventToUpdate,
                                   attachments,
                                   conferences,
                                   attendees,
                                   reminders);
    if (sendInvitation && attendees != null) {
      sendInvitations(eventToUpdate, attendees, userIdentityId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException {
    // TODO Auto-generated method stub

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, String username) {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    List<Long> calendarOwners = getCalendarOwnersOfUser(userIdentity);
    return getEvents(start, end, userIdentity, calendarOwners.toArray(new Long[0]));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEventsByOwner(long ownerId,
                                      ZonedDateTime start,
                                      ZonedDateTime end,
                                      String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    return getEvents(start, end, userIdentity, ownerId);
  }

  @Override
  public List<EventAttachment> getEventAttachments(long eventId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<EventAttendee> getEventAttendees(long eventId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<EventConference> getEventConferences(long eventId) {
    // TODO Auto-generated method stub
    return null;
  }

  private void sendInvitations(Event event, List<EventAttendee> attendees, long userIdentityId) {
    for (EventAttendee eventAttendee : attendees) {
      if (eventAttendee.getIdentityId() != userIdentityId) {
        agendaEventInvitationService.sendInvitation(event, eventAttendee.getIdentityId());
      }
    }
  }

  private void computeRecurrentEventDates(Event event) {
    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence == null) {
      return;
    }

    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    long startTime = start.toEpochSecond() * 1000;
    long endTime = end.toEpochSecond() * 1000;

    DateTime startDateTime = new DateTime(startTime);
    DateTime endDateTime = new DateTime(endTime);

    VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
    Recur recur = Utils.getICalendarRecur(event, recurrence, TimeZone.getTimeZone(ZoneOffset.UTC));
    vevent.getProperties().add(new RRule(recur));

    long fromTime = start.toEpochSecond() * 1000;
    DateTime ical4jFrom = new DateTime(fromTime);

    ZonedDateTime untilDateTime = null;
    boolean neverEnds = true;
    if (recurrence.getUntil() != null) {
      neverEnds = false;
      untilDateTime = recurrence.getUntil().withHour(23).withMinute(59).withSecond(59);
    } else if (recurrence.getCount() > 0) {
      neverEnds = false;
      untilDateTime = end.withHour(23).withMinute(59).withSecond(59);
      switch (recurrence.getFrequency()) {
      case YEARLY:
        untilDateTime = untilDateTime.plusYears(recurrence.getInterval() * recurrence.getCount());
        break;
      case MONTHLY:
        untilDateTime = untilDateTime.plusMonths(recurrence.getInterval() * recurrence.getCount());
        break;
      case WEEKLY:
        untilDateTime = untilDateTime.plusWeeks(recurrence.getInterval() * recurrence.getCount());
        break;
      case DAILY:
        untilDateTime = untilDateTime.plusDays(recurrence.getInterval() * recurrence.getCount());
        break;
      default:
        break;
      }
    } else {
      neverEnds = true;
      untilDateTime = end.withHour(23).withMinute(59).withSecond(59);
    }
    long toTime = untilDateTime.toEpochSecond() * 1000;
    DateTime ical4jTo = new DateTime(toTime);

    Period period = new Period(ical4jFrom, ical4jTo);
    PeriodList list = vevent.calculateRecurrenceSet(period);

    Period firstOccurrencePeriod = (Period) list.first();
    Period lastOccurrencePeriod = (Period) list.last();

    ZonedDateTime overallStart = firstOccurrencePeriod.getStart().toInstant().atZone(ZoneOffset.UTC);
    recurrence.setOverallStart(overallStart);

    if (!neverEnds) {
      ZonedDateTime overallEnd = lastOccurrencePeriod.getEnd().toInstant().atZone(ZoneOffset.UTC);
      recurrence.setOverallEnd(overallEnd);
    }
  }

  private void adjustEventDates(Event event) throws AgendaException {
    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    if (start.isBefore(end)) {
      throw new AgendaException(AgendaExceptionType.EVENT_START_DATE_BEFORE_END_DATE);
    } else if (event.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = end.toLocalDate().atStartOfDay(ZoneOffset.UTC);
    } else {
      start = start.withZoneSameInstant(ZoneOffset.UTC);
      end = end.withZoneSameInstant(ZoneOffset.UTC);
    }

    event.setStart(start);
    event.setEnd(end);
  }

  private List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, Identity userIdentity, Long... calendarOwners) {
    TimeZone userTimezone = AgendaDateUtils.getUserTimezone(userIdentity);
    List<Long> eventIds = this.agendaEventStorage.getEventIds(start, end, userTimezone, calendarOwners);
    if (eventIds == null || eventIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Event> events = eventIds.stream().map(this::getEventById).collect(Collectors.toList());
    computeEventsAcl(userIdentity, events);
    return computeRecurrentEvents(events, start, end, userTimezone);
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

  private void computeEventsAcl(Identity userIdentity, List<Event> events) {
    long userIdentityId = Long.parseLong(userIdentity.getId());
    Map<Long, Calendar> calendars = new HashMap<>();
    for (Event event : events) {
      long creatorId = event.getCreatorId();
      if (creatorId == userIdentityId) {
        event.setAcl(new Permission(true));
      } else {
        long calendarId = event.getCalendarId();
        try {
          Calendar calendar = calendars.get(calendarId);
          if (calendar == null) {
            calendar = agendaCalendarService.getCalendarById(calendarId, userIdentity.getRemoteId());
            calendars.put(calendarId, calendar);
          }
          event.setAcl(calendar.getAcl());
        } catch (IllegalAccessException e) {
          LOG.warn("Impossible case happens, this must be a bug. The user has been denied access to the calendar of a retrieved event",
                   e);
        }
      }
    }
  }

  private void computeAcl(Event event, String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    long userIdentityId = Long.parseLong(userIdentity.getId());
    long creatorId = event.getCreatorId();
    if (creatorId == userIdentityId) {
      event.setAcl(new Permission(true));
    } else {
      Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId(), username);
      event.setAcl(calendar.getAcl());
    }
  }

  private List<Event> computeRecurrentEvents(List<Event> events, ZonedDateTime start, ZonedDateTime end, TimeZone userTimezone) {
    List<Event> computedEvents = new ArrayList<>();
    for (Event event : events) {
      if (event.getRecurrence() == null) {
        computedEvents.add(event);
      } else {
        List<Event> occurrences = Utils.getOccurrences(event, start.toLocalDate(), end.toLocalDate(), userTimezone);
        occurrences = filterExceptionalEvents(occurrences, events);
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

  private List<Event> filterExceptionalEvents(List<Event> occurrences, List<Event> events) {
    return occurrences.stream()
                      .filter(occurrence -> events.stream()
                                                  .anyMatch(exceptionalOccurrenceEvent -> exceptionalOccurrenceEvent.getOccurrence() != null
                                                      && exceptionalOccurrenceEvent.getParentId() == occurrence.getParentId()
                                                      && StringUtils.equals(occurrence.getOccurrence().getId(),
                                                                            exceptionalOccurrenceEvent.getOccurrence()
                                                                                                      .getId())))
                      .collect(Collectors.toList());
  }

}
