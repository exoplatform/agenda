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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
    Event exceptionalOccurrenceEvent = agendaEventStorage.getExceptionalOccurrenceEvent(parentEventId, occurrenceId);
    // Access is read on the event actually served, before anything else is
    // computed: a stored exceptional occurrence through its own row (its own
    // attendee list), a computed occurrence through the parent it inherits
    // everything from. The same access masks what is served below.
    Event servedEvent = exceptionalOccurrenceEvent == null ? recurrentEvent : exceptionalOccurrenceEvent;
    EventAccess access = getEventAccess(servedEvent, userIdentityId);
    if (access == EventAccess.NONE) {
      throw new IllegalAccessException("User with identity id " + userIdentityId + " is not allowed to access event with id "
          + servedEvent.getId());
    }

    if (recurrentEvent.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + parentEventId + " is not a recurrent event");
    }

    Event event = null;

    if (exceptionalOccurrenceEvent != null) {
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
    // 'open' is not part of the positional constructor above: set it explicitly
    // or it is silently lost. Absent on the wire means locked at creation. The
    // invariant (canBeOpen) is enforced here, not only by the UI hiding the
    // padlock: a date poll, a personal calendar event and an event whose
    // participants may modify it or invite are never open; asked together with
    // such a permission, the flag yields and the permission is kept. This
    // is also the path the form's "this occurrence only" takes — it posts the
    // computed occurrence with its parent — so the padlock the organiser left
    // on that date is what the new row carries, its own from then on (US06).
    // The status read is the one checkAndComputeDateOptions just derived from
    // the date options.
    eventToCreate.setOpen(Boolean.TRUE.equals(event.getOpen())
        && canBeOpen(eventToCreate, calendar));

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
    return createdEvent;
  }

  /**
   * Anchors the dates of one occurrence on the dates of its series: the
   * occurrence keeps its own day and takes the series&#39; time of day and
   * duration. Shared by the creation of an exceptional occurrence and by the
   * propagation of a series change to the occurrences that did not customise
   * their dates, so both compute the same instant for the same date.
   *
   * @param seriesEvent the recurrent parent, source of the time and duration
   * @param occurrenceEvent the occurrence whose dates are written
   * @param occurrenceId the date of the occurrence, already normalised
   */
  private void anchorOccurrenceDatesOnSeries(Event seriesEvent, Event occurrenceEvent, ZonedDateTime occurrenceId) {
    ZonedDateTime start = seriesEvent.getStart();
    ZonedDateTime end = seriesEvent.getEnd();
    long diffInSeconds = end.toEpochSecond() - start.toEpochSecond();

    ZonedDateTime occurrenceStart = null;
    if (seriesEvent.isAllDay()) {
      ZonedDateTime occurrenceStartTime = occurrenceId.withZoneSameInstant(seriesEvent.getTimeZoneId());
      occurrenceStart = start.withYear(occurrenceStartTime.getYear())
                             .withMonth(occurrenceStartTime.getMonthValue())
                             .withDayOfMonth(occurrenceStartTime.getDayOfMonth());
    } else {
      ZonedDateTime startStartTime = start.withZoneSameInstant(seriesEvent.getTimeZoneId());
      occurrenceStart = startStartTime.withYear(occurrenceId.getYear())
                                      .withMonth(occurrenceId.getMonthValue())
                                      .withDayOfMonth(occurrenceId.getDayOfMonth())
                                      .withHour(startStartTime.getHour())
                                      .withMinute(startStartTime.getMinute());
    }
    occurrenceEvent.setStart(occurrenceStart);
    occurrenceEvent.setEnd(occurrenceStart.plusSeconds(diffInSeconds));
    adjustEventDatesForWrite(occurrenceEvent);
  }

  /**
   * Applies a change made on a whole series to the dates of that series which
   * were individually modified, instead of deleting them.
   * <p>
   * <strong>The loop visits every stored exceptional occurrence of the
   * series</strong>, not only the ones a user customised: the reminder
   * computing job and its listener materialise a row for every occurrence of
   * every confirmed recurrent event in the next two days, so the existence of a
   * row carries no user intent. Telling the two apart is precisely what the
   * merge below does, property by property.
   * <p>
   * The rule is per property, not per occurrence: a change on the series
   * reaches a customised date like any other, <strong>except</strong> on the
   * properties customised there, which keep their value. Deleting those rows
   * on every save would instead discard, when the summary of a weekly meeting
   * is corrected, the room changed on one date, the date cancelled on another,
   * and the answers attached to both.
   * <p>
   * Which properties were customised is recorded nowhere: an exceptional
   * occurrence is a full clone of its series and the only marker it carries is
   * the one for its dates. So it is derived, by comparing three terms the way a
   * merge does: a property of the occurrence that still equals the series value
   * <em>before</em> the change was inherited and follows the change; one that
   * differs was customised and is kept. The single blind spot is a property a
   * user deliberately re-typed to the series value, which is indistinguishable
   * from an inherited one and follows the change.
   * <p>
   * A row that carries no intent at all — every merged property equal to the
   * series as it was, dates not moved, attendees, conferences and reminders
   * the ones the series gave it for that date — is the reminder job's materialisation of an
   * upcoming date: it is dropped rather than merged, and the job rebuilds it
   * from the new series when the date comes near. The loop therefore works on
   * the rows a user touched, not on every date the job ever materialised. An
   * occurrence whose date the new recurrence no longer produces is the other
   * case where the row is deleted.
   *
   * @param storedSeries the series as it was before this save
   * @param updatedSeries the series as it is being saved
   * @param previousSeriesAttendees the attendee list of the series as it was
   *          before this save, or null on a path that cannot change it (a field
   *          patch) — the deltas are then empty and no membership travels
   * @param previousSeriesConferences the conferences of the series as they were
   *          before this save, or null on a path that cannot change them
   * @param modifierIdentityId who is saving, recorded on every row this touches
   */
  private void applySeriesChangeToExceptionalOccurrences(Event storedSeries,
                                                         Event updatedSeries,
                                                         List<EventAttendee> previousSeriesAttendees,
                                                         List<EventConference> previousSeriesConferences,
                                                         long modifierIdentityId) {
    long seriesId = updatedSeries.getId();
    List<Long> occurrenceEventIds = agendaEventStorage.getExceptionalOccurenceIds(seriesId);
    if (CollectionUtils.isEmpty(occurrenceEventIds)) {
      return;
    }
    // The series as the rows were cloned from it: a row equal to it carries
    // inheritance, not intent. A path that cannot change a list passes null,
    // and the current list is then that reference.
    List<EventAttendee> seriesAttendeesBefore = previousSeriesAttendees != null ? previousSeriesAttendees
                                                                               : attendeeService.getEventAttendees(seriesId)
                                                                                                .getEventAttendees();
    List<EventConference> seriesConferencesBefore = previousSeriesConferences != null ? previousSeriesConferences
                                                                                     : conferenceService.getEventConferences(seriesId);
    // Reminders are read as they are now: a per-user save on the series has
    // just been carried onto its rows, so a row nobody touched equals it
    List<EventReminder> seriesReminders = reminderService.getEventReminders(seriesId);
    List<EventConference> seriesConferencesAfter = conferenceService.getEventConferences(seriesId);
    boolean conferencesChanged = !conferenceKeys(seriesConferencesBefore).equals(conferenceKeys(seriesConferencesAfter));
    OccurrenceLists seriesBefore = new OccurrenceLists(seriesAttendeesBefore, seriesConferencesBefore, seriesReminders);
    // Who the series gained and lost, computed once: a date individually
    // modified is not a place to hide from an invitation or from its
    // withdrawal
    Set<Long> attendeesAddedToSeries = new HashSet<>();
    Set<Long> attendeesRemovedFromSeries = new HashSet<>();
    if (previousSeriesAttendees != null) {
      Set<Long> before = attendeeIdentityIds(previousSeriesAttendees);
      Set<Long> after = attendeeIdentityIds(attendeeService.getEventAttendees(updatedSeries.getId()).getEventAttendees());
      attendeesAddedToSeries.addAll(after);
      attendeesAddedToSeries.removeAll(before);
      attendeesRemovedFromSeries.addAll(before);
      attendeesRemovedFromSeries.removeAll(after);
    }
    // Expanding a recurrence costs more than everything else in this loop, and
    // only a change of the rule or of the times can make a date disappear from
    // it: a corrected summary cannot, so it does not pay for the expansion
    boolean datesOrRuleChanged = !sameRecurrenceRule(storedSeries, updatedSeries)
        || !isSameInstant(storedSeries.getStart(), updatedSeries.getStart())
        || !isSameInstant(storedSeries.getEnd(), updatedSeries.getEnd());
    // Read once: every occurrence lives in the calendar of its series, and the
    // open invariant is evaluated against it for each of them below
    Calendar seriesCalendar = agendaCalendarService.getCalendarById(updatedSeries.getCalendarId());
    for (Long occurrenceEventId : occurrenceEventIds) {
      Event occurrence = agendaEventStorage.getEventById(occurrenceEventId);
      if (occurrence == null || occurrence.getOccurrence() == null || occurrence.getOccurrence().getId() == null) {
        continue;
      }
      Event occurrenceBeforeMerge = occurrence.clone();
      ZonedDateTime occurrenceId = occurrence.getOccurrence().getId();
      if (datesOrRuleChanged && !seriesStillProducesOccurrence(updatedSeries, occurrenceId)) {
        // The one case where a date individually modified is dropped, and it
        // takes its attendees, their answers, its conferences and its reminders
        // with it: said here rather than left silent
        LOG.info("Event {}: the recurrence no longer produces {}, dropping the exceptional occurrence {} saved for that date",
                 updatedSeries.getId(),
                 occurrenceId,
                 occurrenceEventId);
        agendaEventStorage.deleteEventAfterRead(occurrenceEventId);
        continue;
      }
      // Read once per row: they decide whether the row carries intent, and the
      // conference merge below reuses them
      OccurrenceLists rowLists = new OccurrenceLists(attendeeService.getEventAttendees(occurrenceEventId).getEventAttendees(),
                                                     conferenceService.getEventConferences(occurrenceEventId),
                                                     reminderService.getEventReminders(occurrenceEventId));
      if (!carriesUserIntent(occurrence, storedSeries, occurrenceId, rowLists, seriesBefore)) {
        LOG.debug("Event {}: the exceptional occurrence {} saved for {} carries nothing of its own, dropping it; the reminder job rebuilds it from the series",
                  seriesId,
                  occurrenceEventId,
                  occurrenceId);
        agendaEventStorage.deleteEventAfterRead(occurrenceEventId);
        continue;
      }
      // Structural, never a customisation: an occurrence lives in the calendar
      // of its series
      occurrence.setCalendarId(updatedSeries.getCalendarId());
      occurrence.setSummary(mergeOccurrenceProperty(occurrence.getSummary(),
                                                    storedSeries.getSummary(),
                                                    updatedSeries.getSummary()));
      occurrence.setDescription(mergeOccurrenceProperty(occurrence.getDescription(),
                                                        storedSeries.getDescription(),
                                                        updatedSeries.getDescription()));
      occurrence.setLocation(mergeOccurrenceProperty(occurrence.getLocation(),
                                                     storedSeries.getLocation(),
                                                     updatedSeries.getLocation()));
      occurrence.setColor(mergeOccurrenceProperty(occurrence.getColor(), storedSeries.getColor(), updatedSeries.getColor()));
      occurrence.setAvailability(mergeOccurrenceProperty(occurrence.getAvailability(),
                                                         storedSeries.getAvailability(),
                                                         updatedSeries.getAvailability()));
      // A property the merge does not name is one a series change never
      // reaches on a customised date, and this one decides what a published
      // calendar link shows of the event, so a date left more visible than its
      // series leaks.
      // Compared once a null is read as DEFAULT — the meaning the column already
      // has everywhere visibility is honoured ("a null reads as not masked").
      // VISIBILITY is nullable by design, and a database whose addColumn does
      // not backfill leaves every pre-1.0.0-44 row null; the first full save of
      // such a date writes DEFAULT onto its row while its series stays null,
      // and a plain equality would then read that date as customised and leave
      // it published when the series is made private.
      occurrence.setVisibility(mergeOccurrenceProperty(visibilityOf(occurrence),
                                                       visibilityOf(storedSeries),
                                                       visibilityOf(updatedSeries)));
      occurrence.setStatus(mergeOccurrenceProperty(occurrence.getStatus(),
                                                   storedSeries.getStatus(),
                                                   updatedSeries.getStatus()));
      occurrence.setTimeZoneId(mergeOccurrenceProperty(occurrence.getTimeZoneId(),
                                                       storedSeries.getTimeZoneId(),
                                                       updatedSeries.getTimeZoneId()));
      occurrence.setAllDay(mergeOccurrenceProperty(occurrence.isAllDay(),
                                                   storedSeries.isAllDay(),
                                                   updatedSeries.isAllDay()));
      occurrence.setAllowAttendeeToUpdate(mergeOccurrenceProperty(occurrence.isAllowAttendeeToUpdate(),
                                                                  storedSeries.isAllowAttendeeToUpdate(),
                                                                  updatedSeries.isAllowAttendeeToUpdate()));
      occurrence.setAllowAttendeeToInvite(mergeOccurrenceProperty(occurrence.isAllowAttendeeToInvite(),
                                                                  storedSeries.isAllowAttendeeToInvite(),
                                                                  updatedSeries.isAllowAttendeeToInvite()));
      // 'open' is merged like the rest, then clamped: it is the flag
      // canRespondToEvent reads, so a value customised on one date must not
      // survive a change to the series that has to lock it — a series turned
      // into a date poll, moved to a personal calendar, or whose participants
      // may now modify it or invite. The status and the two permissions are
      // the ones merged just above, the calendar the series' own.
      occurrence.setOpen(Boolean.TRUE.equals(mergeOccurrenceProperty(occurrence.getOpen(),
                                                                    storedSeries.getOpen(),
                                                                    updatedSeries.getOpen()))
          && canBeOpen(occurrence, seriesCalendar));
      // The dates have the one explicit marker the model carries, so they need
      // no comparison: an occurrence that was moved keeps its own dates, one
      // that was not follows the series' time and duration on its own day
      if (!occurrence.getOccurrence().isDatesModified()) {
        anchorOccurrenceDatesOnSeries(updatedSeries, occurrence, occurrenceId);
      }
      boolean datesMoved = !isSameInstant(occurrenceBeforeMerge.getStart(), occurrence.getStart());
      Event storedOccurrence = occurrence;
      if (!occurrence.equals(occurrenceBeforeMerge)) {
        occurrence.setModifierId(updatedSeries.getModifierId());
        occurrence.setUpdated(ZonedDateTime.now());
        storedOccurrence = agendaEventStorage.updateEvent(occurrence);
      }

      if (datesMoved) {
        // The trigger date of a reminder is stored, not recomputed when it is
        // due, so moving the date of an occurrence leaves every reminder of
        // every attendee pointing at the old time. Recomputed here for all
        // receivers: the variant that follows occurrences elsewhere works on
        // the acting user alone.
        if (!rowLists.reminders().isEmpty()) {
          reminderService.saveEventReminders(storedOccurrence, rowLists.reminders());
        }
      }

      // Conferences merge like the properties: a row whose links are the ones
      // the series had takes the series' new ones, a row with links of its own
      // keeps them. Without it a changed meeting link never reaches the dates
      // the reminder job materialised for the next days.
      if (conferencesChanged && conferenceKeys(rowLists.conferences()).equals(conferenceKeys(seriesConferencesBefore))) {
        conferenceService.saveEventConferences(storedOccurrence.getId(),
                                               copyConferencesTo(seriesConferencesAfter, storedOccurrence.getId()));
      }

      applySeriesMembershipChange(storedOccurrence,
                                  rowLists.attendees(),
                                  attendeesAddedToSeries,
                                  attendeesRemovedFromSeries,
                                  modifierIdentityId);
    }
  }

  /**
   * Carries the people the series gained and lost into one date individually
   * modified, leaving the people invited on that date alone untouched — the
   * same rule as the properties, applied to a set.
   *
   * @param occurrence the stored occurrence, as it is after the properties were
   *          merged
   * @param occurrenceAttendees its attendees, as read before the merge
   * @param addedToSeries identities the series gained
   * @param removedFromSeries identities the series lost
   * @param modifierIdentityId who is saving
   */
  private void applySeriesMembershipChange(Event occurrence,
                                           List<EventAttendee> occurrenceAttendees,
                                           Set<Long> addedToSeries,
                                           Set<Long> removedFromSeries,
                                           long modifierIdentityId) {
    if (addedToSeries.isEmpty() && removedFromSeries.isEmpty()) {
      return;
    }
    List<EventAttendee> merged = occurrenceAttendees.stream()
                                                    .filter(attendee -> !removedFromSeries.contains(attendee.getIdentityId()))
                                                    .collect(Collectors.toList());
    Set<Long> present = attendeeIdentityIds(merged);
    for (Long identityId : addedToSeries) {
      if (!present.contains(identityId)) {
        merged.add(new EventAttendee(0, occurrence.getId(), identityId, null));
      }
    }
    Set<Long> attendeesBefore = attendeeIdentityIds(occurrenceAttendees);
    if (attendeeIdentityIds(merged).equals(attendeesBefore)) {
      return;
    }
    attendeeService.saveEventAttendees(occurrence,
                                       merged,
                                       modifierIdentityId,
                                       false,
                                       false,
                                       new AgendaEventModification(occurrence.getId(),
                                                                   occurrence.getCalendarId(),
                                                                   modifierIdentityId));
    // Removing the attendee row does not remove the reminders that person set
    // on that date: nothing listens to the attendee-deleted event, and a
    // reminder is sent from its own stored trigger date with no attendance
    // check, so they are removed here.
    for (Long identityId : removedFromSeries) {
      if (attendeesBefore.contains(identityId)) {
        reminderService.removeUserReminders(occurrence.getId(), identityId);
      }
    }
  }

  /**
   * Whether an exceptional occurrence row holds anything a user put there, read
   * against the series as it was when the row was cloned from it: a property
   * that differs, dates that were moved, or an attendee list, a conference list
   * or a reminder list other than the series' own for that date. A row with
   * none of these is the reminder job's materialisation of an
   * upcoming date, which the job rebuilds from the series whenever it needs it.
   *
   * @param row the stored occurrence, before the merge
   * @param storedSeries the series before the change
   * @param occurrenceId the date the row stands for
   * @param rowLists the row's attendees, conferences and reminders
   * @param seriesLists the series' attendees, conferences and reminders before
   *          the change
   * @return whether the row is kept and merged rather than dropped
   */
  private boolean carriesUserIntent(Event row,
                                    Event storedSeries,
                                    ZonedDateTime occurrenceId,
                                    OccurrenceLists rowLists,
                                    OccurrenceLists seriesLists) {
    if (row.getOccurrence().isDatesModified()
        || !Objects.equals(row.getSummary(), storedSeries.getSummary())
        || !Objects.equals(row.getDescription(), storedSeries.getDescription())
        || !Objects.equals(row.getLocation(), storedSeries.getLocation())
        || !Objects.equals(row.getColor(), storedSeries.getColor())
        || row.getAvailability() != storedSeries.getAvailability()
        || visibilityOf(row) != visibilityOf(storedSeries)
        || row.getStatus() != storedSeries.getStatus()
        || !Objects.equals(row.getTimeZoneId(), storedSeries.getTimeZoneId())
        || row.isAllDay() != storedSeries.isAllDay()
        || row.isAllowAttendeeToUpdate() != storedSeries.isAllowAttendeeToUpdate()
        || row.isAllowAttendeeToInvite() != storedSeries.isAllowAttendeeToInvite()
        || Boolean.TRUE.equals(row.getOpen()) != Boolean.TRUE.equals(storedSeries.getOpen())) {
      return true;
    }
    List<EventAttendee> seriesAttendeesOnThatDate = new EventAttendeeList(seriesLists.attendees()).getEventAttendees(occurrenceId);
    // Reminders are compared with the series' reminders that apply to that
    // date, the ones the row was built with: one turned off on the date alone
    // is intent, and so is one set there, even when the series carries the
    // same reminder for another range of its dates
    List<EventReminder> seriesRemindersOnThatDate = remindersOnThatDate(seriesLists.reminders(), occurrenceId);
    return !attendeeResponses(rowLists.attendees()).equals(attendeeResponses(seriesAttendeesOnThatDate))
        || !conferenceKeys(rowLists.conferences()).equals(conferenceKeys(seriesLists.conferences()))
        || !reminderKeys(rowLists.reminders()).equals(reminderKeys(seriesRemindersOnThatDate));
  }

  /**
   * @param reminders the series' reminders
   * @param occurrenceId a date of the series
   * @return the ones that apply to that date by the range each carries, the
   *         filter the row was built with
   */
  private List<EventReminder> remindersOnThatDate(List<EventReminder> reminders, ZonedDateTime occurrenceId) {
    if (reminders == null) {
      return Collections.emptyList();
    }
    return reminders.stream()
                    .filter(reminder -> (reminder.getFromOccurrenceId() == null
                        || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                        || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                        && (reminder.getUntilOccurrenceId() == null || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                    .toList();
  }

  /**
   * @param attendees an attendee list
   * @return each identity's answer, an unanswered invitation read as
   *         {@link EventAttendeeResponse#NEEDS_ACTION}
   */
  private Map<Long, EventAttendeeResponse> attendeeResponses(List<EventAttendee> attendees) {
    Map<Long, EventAttendeeResponse> responses = new HashMap<>();
    if (attendees != null) {
      for (EventAttendee attendee : attendees) {
        responses.put(attendee.getIdentityId(),
                      attendee.getResponse() == null ? EventAttendeeResponse.NEEDS_ACTION : attendee.getResponse());
      }
    }
    return responses;
  }

  /**
   * @param conferences a conference list
   * @return what each conference is, without the row ids that differ between
   *         a series and the rows cloned from it
   */
  private Set<String> conferenceKeys(List<EventConference> conferences) {
    Set<String> keys = new HashSet<>();
    if (conferences != null) {
      for (EventConference conference : conferences) {
        keys.add(String.join("|",
                             String.valueOf(conference.getType()),
                             String.valueOf(conference.getUrl()),
                             String.valueOf(conference.getPhone()),
                             String.valueOf(conference.getAccessCode()),
                             String.valueOf(conference.getDescription())));
      }
    }
    return keys;
  }

  /**
   * @param reminders a reminder list
   * @return who is reminded how long before, without the row ids and the
   *         trigger dates that differ between a series and its dates
   */
  private Set<String> reminderKeys(List<EventReminder> reminders) {
    Set<String> keys = new HashSet<>();
    if (reminders != null) {
      for (EventReminder reminder : reminders) {
        keys.add(reminder.getReceiverId() + "|" + reminder.getBefore() + "|" + reminder.getBeforePeriodType());
      }
    }
    return keys;
  }

  /**
   * @param conferences the conferences to copy
   * @param eventId the row they are copied onto
   * @return copies with no id of their own yet, attached to that row
   */
  private List<EventConference> copyConferencesTo(List<EventConference> conferences, long eventId) {
    List<EventConference> copies = new ArrayList<>();
    for (EventConference conference : conferences) {
      EventConference copy = conference.clone();
      copy.setId(0);
      copy.setEventId(eventId);
      copies.add(copy);
    }
    return copies;
  }

  /**
   * Whether two recurrences describe the same dates, compared on the rule both
   * carry once they have been read back from storage.
   *
   * @param storedSeries the series before the change
   * @param updatedSeries the series after it
   * @return whether the rule is unchanged
   */
  private boolean sameRecurrenceRule(Event storedSeries, Event updatedSeries) {
    EventRecurrence before = storedSeries.getRecurrence();
    EventRecurrence after = updatedSeries.getRecurrence();
    if (before == null || after == null) {
      return before == after;
    }
    return StringUtils.equals(before.getRrule(), after.getRrule());
  }

  /**
   * @param first a moment, or null
   * @param second another moment, or null
   * @return whether both name the same instant, two nulls included
   */
  private boolean isSameInstant(ZonedDateTime first, ZonedDateTime second) {
    if (first == null || second == null) {
      return first == null && second == null;
    }
    return first.toInstant().equals(second.toInstant());
  }

  /**
   * @param attendees an attendee list
   * @return the identities it names, without duplicates
   */
  private Set<Long> attendeeIdentityIds(List<EventAttendee> attendees) {
    return attendees == null ? new HashSet<>()
                             : attendees.stream().map(EventAttendee::getIdentityId).collect(Collectors.toSet());
  }

  /**
   * One property of one occurrence, merged against the series change.
   *
   * @param occurrenceValue the value the occurrence carries today
   * @param storedSeriesValue the value the series carried before the change
   * @param updatedSeriesValue the value the series carries after it
   * @return the new series value when the occurrence had inherited the old one,
   *         the occurrence&#39;s own value when it had been customised
   */
  private <T> T mergeOccurrenceProperty(T occurrenceValue, T storedSeriesValue, T updatedSeriesValue) {
    return Objects.equals(occurrenceValue, storedSeriesValue) ? updatedSeriesValue : occurrenceValue;
  }

  /**
   * @param event an event as stored
   * @return its visibility, a legacy null read as
   *         {@link EventVisibility#DEFAULT} — the value the publish boundary
   *         already gives it
   */
  private EventVisibility visibilityOf(Event event) {
    return event.getVisibility() == null ? EventVisibility.DEFAULT : event.getVisibility();
  }

  /**
   * Whether the recurrence of the series still produces the date of a stored
   * occurrence. Deleting a row is the consequence of answering no, so the match
   * is the <strong>same predicate</strong> that decides elsewhere whether a row
   * is recognised as the exception of a date — recognising a row and keeping it
   * must never give opposite answers.
   *
   * @param series the series as it is being saved
   * @param occurrenceId the date of the stored occurrence
   * @return false when the new recurrence no longer produces that day
   */
  private boolean seriesStillProducesOccurrence(Event series, ZonedDateTime occurrenceId) {
    if (series.getRecurrence() == null) {
      return false;
    }
    LocalDate occurrenceDate = occurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    // No limit: the three-day window bounds the expansion per frequency, not
    // absolutely — three dates for a daily rule, 72 for an hourly one — and a
    // cap sized for the first would silently answer no for the second, whose
    // dates all fall on the first day. Answering no deletes a row, so the cap
    // is the more expensive mistake of the two
    List<Event> occurrences = Utils.getOccurrences(series, occurrenceDate.minusDays(1), occurrenceDate.plusDays(1), 0);
    return occurrences != null && occurrences.stream().anyMatch(occurrence -> {
      EventOccurrence computed = occurrence.getOccurrence();
      return computed != null && computed.getId() != null && isSameOccurrenceDate(computed.getId(), occurrenceId);
    });
  }

  /**
   * Whether a date produced by a recurrence and the date stored on an
   * exceptional occurrence designate the same occurrence. The UTC day, plus the
   * twelve-hour tolerance kept for identifiers produced before TASK-39591: the
   * formula changed for all-day events only, and an all-day row of a zone east
   * of UTC then sits on the previous UTC day, less than twelve hours away.
   *
   * @param computedOccurrenceId the date the recurrence produces
   * @param storedOccurrenceId the date stored on the occurrence row
   * @return whether both designate the same occurrence
   */
  private boolean isSameOccurrenceDate(ZonedDateTime computedOccurrenceId, ZonedDateTime storedOccurrenceId) {
    return computedOccurrenceId.withZoneSameInstant(ZoneOffset.UTC)
                               .toLocalDate()
                               .isEqual(storedOccurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
        || Math.abs(storedOccurrenceId.toEpochSecond() - computedOccurrenceId.toEpochSecond()) < 43200;
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
    // The open flag is inherited from the series like every other property of
    // the clone, and may then be changed on this date alone (US06). It stays in
    // step with its series through applySeriesChangeToExceptionalOccurrences
    // until somebody changes it here.
    anchorOccurrenceDatesOnSeries(parentEvent, exceptionalEvent, occurrenceId);
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

    checkCanMoveEvent(storedEvent.getCalendarId(), eventId, calendar, userIdentityId);

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

    // 'open' is not part of the positional constructor above. A payload that
    // does not state it (null) keeps the stored value, so no client can lock or
    // open an event by omission; an explicit value is applied under the same
    // canUpdateEvent right as the rest of the save. The invariant (canBeOpen)
    // is evaluated on the TARGET calendar, the derived status and the two
    // participant permissions as the creator-only rule above resolved them, so
    // moving an event to a personal calendar, turning it into a date poll or
    // allowing its participants to modify it or invite locks it.
    // This is also the path that writes the flag onto one date of a series:
    // since US06 each row carries its own, so the scope the organiser chose in
    // the form is what decides which rows are written, not this method.
    boolean open = event.getOpen() == null ? Boolean.TRUE.equals(storedEvent.getOpen()) : event.getOpen();
    eventToUpdate.setOpen(open && canBeOpen(eventToUpdate, calendar));

    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.UPDATED);
    // The modification is detected on the incoming event, whose 'open' is what
    // the client asked for (null when it did not ask at all); the audit must
    // report what was written instead, or it would announce a toggle on every
    // partial payload and stay silent when a move actually locked the event
    event.setOpen(eventToUpdate.getOpen());
    Utils.detectEventModifiedFields(event, storedEvent, eventModifications);
    if (eventToUpdate.getOccurrence() != null && eventModifications.hasModifiedDate()) {
      eventToUpdate.getOccurrence().setDatesModified(true);
    }
    Event updatedEvent = agendaEventStorage.updateEvent(eventToUpdate);

    createOrUpdateEventProperties(event.getParameters(), updatedEvent);
    // Snapshotted before saving, like the attendees below: a date whose
    // conferences are the ones the series had carries inheritance, not intent,
    // and the merge below needs that reference
    List<EventConference> previousConferences = conferenceService.getEventConferences(eventId);
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

    // A change on the series reaches the dates that were individually modified
    // instead of deleting them. Run here, after the
    // series and its attendee list are stored, for three reasons: an until is a
    // LocalDate that does not round-trip across zones, so the stored recurrence
    // is the one every later read expands; a series update that fails leaves
    // its occurrences untouched; and the new attendee list of the series is
    // what those dates must be carried towards.
    if (updatedEvent.getParentId() <= 0
        && (updatedEvent.getRecurrence() != null || storedEvent.getRecurrence() != null)) {
      applySeriesChangeToExceptionalOccurrences(storedEvent,
                                                updatedEvent,
                                                previousAttendees,
                                                previousConferences,
                                                userIdentityId);
    }

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

    // Both checks below are evaluated after the field loop, on the calendar the
    // event lands in, so that a patch moving it and changing something else at
    // once is judged where it ends up rather than where it started.
    Calendar targetCalendar = agendaCalendarService.getCalendarById(event.getCalendarId());

    // Moving the event to another calendar additionally requires the right to
    // create events in the TARGET calendar, exactly as the full-save path
    // requires it: being allowed to update an event must not grant filing it
    // into someone else's calendar — and canUpdateEvent above is satisfied by a
    // mere attendee when allowAttendeeToUpdate is set
    if (originalEvent.getCalendarId() != event.getCalendarId() && !canCreateEvent(targetCalendar, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't move event " + eventId + " to calendar "
          + event.getCalendarId());
    }

    // The open invariant excludes the two participant permissions, in both
    // directions. A patch that allows the participants to modify the event or
    // to invite while it stays open is a deliberate act, refused with its own
    // code: the form never sends it, each option being disabled while the other
    // is on, and the full-save path resolves the same conflict by locking.
    boolean openAfterPatch = Boolean.TRUE.equals(event.getOpen());
    if (openAfterPatch
        && !fields.containsKey("open")
        && (fields.containsKey("allowAttendeeToUpdate") || fields.containsKey("allowAttendeeToInvite"))
        && (event.isAllowAttendeeToUpdate() || event.isAllowAttendeeToInvite())) {
      throw new IllegalArgumentException("agenda.openEvent.attendeePermissionNotAllowed");
    }
    // What happens when the open invariant fails depends on who asked: a patch
    // that carries 'open' is a deliberate act and is refused, while a patch that
    // merely moves an already open event into a personal calendar, or turns it
    // into a date poll, locks it silently — the same outcome the full-save path
    // gives that operation.
    if (openAfterPatch && !canBeOpen(event, targetCalendar)) {
      if (fields.containsKey("open")) {
        throw new IllegalArgumentException("agenda.openEvent.notAllowed");
      }
      event.setOpen(false);
    }

    event.setModifierId(Long.parseLong(userIdentity.getId()));
    AgendaEventModification eventModifications = new AgendaEventModification(eventId, event.getCalendarId(), userIdentityId);
    eventModifications.addModificationType(AgendaEventModificationType.UPDATED);
    Utils.detectEventModifiedFields(event, originalEvent, eventModifications);
    if (event.getOccurrence() != null && eventModifications.hasModifiedDate()) {
      event.getOccurrence().setDatesModified(true);
    }
    event = agendaEventStorage.updateEvent(event);

    // A patch meant for the whole series reaches the dates individually
    // modified instead of deleting them, and leaves the properties customised
    // there untouched. Run on the stored event for the same reasons as the
    // full-save path.
    if (updateAllOccurrences && event.getParentId() <= 0 && event.getRecurrence() != null) {
      // No attendee list travels on this path: a patch changes fields, never
      // the people invited
      applySeriesChangeToExceptionalOccurrences(originalEvent, event, null, null, userIdentityId);
    }

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
    // Carries the attendees snapshotted above: the rows are deleted along with
    // the event, so a listener that needs to reach them can no longer look them
    // up by event id
    Utils.broadcastEvent(listenerService, Utils.POST_DELETE_AGENDA_EVENT_EVENT, eventModifications, eventAttendeeList);
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
   * share record (EXO-90357) is {@link EventAccess#SHARED}, and only a user
   * can be one: a share names a user, never a space or a guest. The share is
   * asked last, so nobody who could read before is ever read as a sharee.
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
    // The calendar half is the one the answer right of an open event reuses
    // (AgendaEventAttendeeService#canRespondToEvent): one rule, written once
    if (Utils.canAccessEventCalendar(identityManager, spaceService, calendar, identityId)) {
      return EventAccess.FULL;
    }
    if (attendeeService.isEventAttendee(getEventIdOrParentId(event), identityId)) {
      return EventAccess.FULL;
    }
    if (user && calendarShareAccess.isSharedWith(calendarId, identityId)) {
      return EventAccess.SHARED;
    }
    return EventAccess.NONE;
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
   * {@inheritDoc}
   */
  @Override
  public boolean canUpdateEvent(Event event, long userIdentityId) {
    // The calendar is read first, and always: an event of a subscribed calendar
    // (EXO-90278) is the feed's, and neither its creator nor its owner updates,
    // moves or deletes it. The read is served by the calendar cache.
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    if (calendar == null || calendar.isDeleted() || calendar.isSubscription()) {
      return false;
    }
    if (userIdentityId == event.getCreatorId()
        && Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
      // A creator who can still access the calendar
      return true;
    }
    if (event.isAllowAttendeeToUpdate()
        && attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId)) {
      return true;
    }
    return Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
  }

  /**
   * {@inheritDoc} Never in a subscribed calendar (EXO-90278), whose events come
   * from its feed alone.
   */
  @Override
  public boolean canCreateEvent(Calendar calendar, long userIdentityId) {
    if (calendar.isSubscription()) {
      return false;
    }
    return Utils.canCreateEvent(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
  }

  /**
   * Where the open flag may be true at all, whatever the client sends: an event
   * that is not a date poll (opening a date poll is out of scope), in a space
   * calendar (in a personal calendar nobody but the owner and the invitees can
   * reach the event, so there is nobody to open it to), and whose participants
   * are allowed neither to modify it nor to invite. Answering an open event
   * writes a full attendee row, and either permission would hand the edit,
   * delete or invite right to whoever clicks Yes, Maybe or No on an event they
   * were not invited to: opening widens the right to answer and nothing else,
   * so the flag and the two permissions are mutually exclusive. The server-side
   * invariant behind the board's rules, independent of the UI hiding or
   * disabling the padlock.
   * <p>
   * One occurrence of a series may carry its own value: the rule does not
   * refuse an event that has a parent, since a change on the series merges
   * into the dates individually modified instead of deleting them, so a value
   * written on an occurrence row survives the next save of its series.
   *
   * @param eventToStore the event as the save is about to store it: its status
   *          as derived from the date options, its participant permissions as
   *          resolved for this save
   * @param calendar the calendar the event is saved into
   * @return whether the open flag may be stored as true
   */
  private boolean canBeOpen(Event eventToStore, Calendar calendar) {
    return eventToStore.getStatus() != EventStatus.TENTATIVE
        && isSpaceCalendar(calendar)
        && !eventToStore.isAllowAttendeeToUpdate()
        && !eventToStore.isAllowAttendeeToInvite();
  }

  /**
   * In a personal calendar nobody but the owner and the invitees can reach the
   * event, so there is nobody to open it to.
   */
  private boolean isSpaceCalendar(Calendar calendar) {
    Identity owner = calendar == null ? null : identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    return owner != null && owner.isSpace();
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
   * of the calendars the reader sees only through a share (EXO-90357). The
   * access is decided once per parent event, like the permissions, and only
   * an event of a shared calendar pays the calendar check: the shared set is
   * one cache hit for the whole listing.
   *
   * @param events the events read, copies of the cached ones
   * @param userIdentity the reader
   */
  private void computeEventsAcl(List<Event> events, Identity userIdentity) {
    long userIdentityId = Long.parseLong(userIdentity.getId());
    Map<Long, EventPermission> eventPermissionsMap = new HashMap<>();
    Map<Long, EventAccess> eventAccessMap = new HashMap<>();
    List<Long> sharedCalendarIds = calendarShareAccess.getSharedCalendarIds(userIdentityId);
    events.forEach(event -> {
      long eventId = getEventIdOrParentId(event);
      EventPermission permission = eventPermissionsMap.get(eventId);
      if (permission == null) {
        boolean canUpdateEvent = canUpdateEvent(event, userIdentityId);
        boolean isEventAttendee = attendeeService.isEventAttendee(eventId, userIdentityId);
        permission = new EventPermission(canUpdateEvent, isEventAttendee);
        eventPermissionsMap.put(eventId, permission);
        boolean sharedOnly = sharedCalendarIds.contains(event.getCalendarId())
            && !isEventAttendee
            && !canAccessCalendarOf(event, userIdentityId);
        eventAccessMap.put(eventId, sharedOnly ? EventAccess.SHARED : EventAccess.FULL);
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
   * @param currentCalendarId the identifier of the calendar the event is stored
   *          in
   * @param eventId the technical identifier of the event
   * @param targetCalendar the stored calendar the event is filed into, already
   *          checked to exist
   * @param userIdentityId the {@link Identity} identifier of the user moving the
   *          event
   * @throws IllegalAccessException when the calendar changes and the user can't
   *           create events in the target calendar
   */
  private void checkCanMoveEvent(long currentCalendarId,
                                 long eventId,
                                 Calendar targetCalendar,
                                 long userIdentityId) throws IllegalAccessException {
    if (currentCalendarId != targetCalendar.getId() && !canCreateEvent(targetCalendar, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't move event " + eventId + " to calendar "
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
        checkCanMoveEvent(event.getCalendarId(), event.getId(), calendar, userIdentityId);
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
      case "open":
        // The invariant (canBeOpen) is checked once, after the whole field set
        // is applied, so that a patch changing the calendar and the flag at
        // once is evaluated on the calendar it lands in
        event.setOpen(Boolean.parseBoolean(fieldValue));
        break;
      default:
        throw new IllegalArgumentException("agenda.eventFieldNotSupported");
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
                        return exceptionalEvents.stream()
                                                .noneMatch(exceptionalOccurence -> isSameOccurrenceDate(occurrenceId,
                                                                                                        exceptionalOccurence.getOccurrence()
                                                                                                                            .getId()));
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
