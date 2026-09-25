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
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
      event.setAcl(permissionOf(event, userIdentityId, isEventAttendee));
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
      boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
      event.setAcl(permissionOf(event, userIdentityId, isEventAttendee));
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

    checkCanCreateEvent(event, parentEvent, calendar, userIdentityId);

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

    createdEvent = readCreatedEvent(eventId, event.getTimeZoneId(), userIdentityId);

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
    } else {
      inheritSeriesAttendees(createdEvent, event, parentEvent);
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
   * Reads back the row {@link #createEvent} has just written, the way
   * {@link #getEventById(long, ZoneId, long)} reads any event — storage read,
   * dates adjusted to the reader's zone, {@code acl} stamped on it, rendered
   * through {@link Utils#maskForAccess(Event, EventAccess)} — but
   * <b>without asking the read ACL again</b> (EXO-90408).
   * <p>
   * Asking it here is a second ruling on a different question. The write has
   * already been decided a few lines earlier by {@code checkCanCreateEvent},
   * from the payload; the read ACL is then asked of a row the payload has
   * <b>not been fully applied to</b> — attendees, reminders and conferences are
   * all stored after this point. An exceptional occurrence created by somebody
   * who may update the series but cannot see the calendar it is filed in
   * (EXO-90382) therefore carries no attendee row at this instant,
   * {@link #getEventAccess(Event, long)} answers {@link EventAccess#NONE}, and
   * the creator is refused the event they were just allowed to create. The row
   * is committed regardless — the transaction is the storage's, not this
   * method's — so the caller gets a 401 on a successful write, and the
   * occurrence is left with no attendee at all: invisible to every
   * attendee-keyed listing, while
   * {@code AgendaEvent.getExceptionalOccurenceIdsByPeriod} matches it on its
   * parent and on its occurrence identifier falling in the window being read,
   * so {@code filterExceptionalEvents} takes that date out of the series for
   * every reader, the organiser included.
   * <p>
   * The event is rendered as {@link EventAccess#FULL}: the only reader here is
   * the creator, on the row they have just been allowed to create, so nothing
   * is masked. The call is kept so the access is stamped on the returned event
   * exactly as every other read path stamps it.
   * <p>
   * <b>The one cost, stated</b>: the {@code acl} stamped here is computed
   * before the attendees exist, so for a creator whom this very payload admits
   * as an attendee, {@code canEdit} and {@code isEventAttendee} come back
   * false on the returned object. No caller reads them today — the REST layer
   * re-reads the event before answering, the two MCP-side tools
   * ({@code AgendaEventMcpTool} and the enterprise {@code AgendaEventAiTool})
   * read the dates and the masking flag, and {@code caldav-integration} reads
   * the identifier — but a
   * caller that did would read them stale.
   *
   * @param eventId identifier of the row just created
   * @param timeZone the zone the dates are read in, may be null
   * @param userIdentityId identity identifier of the creator
   * @return the created event read in full. The null branch mirrors
   *         {@link #getEventById(long, ZoneId, long)} and is unreachable from
   *         the only caller, which reads back a row it created two statements
   *         earlier in the same transaction — {@code createEvent} dereferences
   *         the result without a guard, and should not grow one on the
   *         strength of this signature
   */
  private Event readCreatedEvent(long eventId, ZoneId timeZone, long userIdentityId) {
    Event event = agendaEventStorage.getEventById(eventId);
    if (event == null) {
      return null;
    }
    adjustEventDatesForRead(event, timeZone);
    boolean isEventAttendee = attendeeService.isEventAttendee(getEventIdOrParentId(event), userIdentityId);
    event.setAcl(permissionOf(event, userIdentityId, isEventAttendee));
    return maskForAccess(event, EventAccess.FULL);
  }

  /**
   * Gives a freshly created exceptional occurrence the attendees its series
   * carries for that date, when the payload that created it named none
   * (EXO-90408).
   * <p>
   * The invariant is not new: it is the one
   * {@link #saveEventExceptionalOccurrence(long, ZonedDateTime)} already keeps
   * when it detaches one date of a series, by copying the parent's attendees
   * for that occurrence and handing them to
   * {@link #createEventExceptionalOccurrence}. An occurrence created through
   * {@link #createEvent} is the same object made by the other door, so it owes
   * the same thing. Without it the row is an orphan: nobody is invited to it,
   * so no attendee-keyed listing returns it, while
   * {@code AgendaEvent.getExceptionalOccurenceIdsByPeriod} matches it on its
   * parent and on its occurrence identifier falling in the window being read —
   * so {@code filterExceptionalEvents} removes that date from the series for
   * everyone and nothing replaces it. The date simply
   * disappears from the organiser's and the invitees' calendars.
   * <p>
   * The same mechanism is reused deliberately —
   * {@link AgendaEventAttendeeService#getEventAttendees(long)} filtered to the
   * occurrence, then {@link #cleanupAttendeeIds(List)} so the rows are created
   * against the occurrence rather than updated on the series — and it is
   * called with the same arguments
   * {@link #createEventExceptionalOccurrence} uses: <b>no creator</b> and
   * <b>no invitation</b>.
   * <p>
   * No invitation is the consequential one, and it holds end to end: the
   * notification plugins are dispatched from
   * {@code AgendaEventAttendeeServiceImpl.sendInvitations}, which
   * {@code processSendingInvitation} only reaches when that flag is true. These
   * people were invited to the series by whoever organised it and are not
   * being invited to anything new, so no mail leaves on this path. That also
   * keeps it clear of the invitation mail's ICS attachment
   * ({@code AgendaTemplateBuilder} attaches {@code Utils.generateIcsFile}'s
   * output for an {@code ADDED} + {@code CONFIRMED} notification, where the
   * recipient's own {@code shouldAttachIcsFile} allows it), which an
   * all-day event does not survive unshifted: that writer takes no all-day
   * flag and never emits {@code VALUE=DATE}, so a day stored at UTC midnight
   * reaches a recipient west of it as the previous evening.
   * <p>
   * No creator only disables {@code isOtherAttendeeResponse}, so the responses
   * carried by the copied rows are stored as they stand. <b>They do not
   * survive the call</b>, and the earlier wording of this comment claimed they
   * did: {@code createEvent} broadcasts {@code POST_CREATE_AGENDA_EVENT_EVENT}
   * a few lines further down, {@code AgendaReplyOnSaveListener} is registered
   * on it synchronously, and on an {@code ADDED} modification it answers
   * {@code NEEDS_ACTION} for every attendee who is not the modifier and
   * {@code ACCEPTED} for the one who is — the modifier's own copied answer no
   * more survives than anybody else's. That is the same end state an
   * occurrence whose payload names attendees already reaches, so the two doors
   * agree — but it is the listener that decides it, not these arguments. The
   * pins therefore assert that end state and name the listener; the two
   * arguments themselves have no observable effect here, so nothing pins them,
   * and a change to the listener would change this contract with the suite
   * still green.
   * {@code resetResponses} is kept as
   * {@code status != CONFIRMED} to mirror the sibling; note the sibling reads
   * the <b>parent's</b> status, its event being a clone of it, while this path
   * reads the payload's, which {@code createEvent} has already defaulted to
   * {@code CONFIRMED}.
   * <p>
   * It applies to a payload shaped like an amendment of one date of a series —
   * {@link #isAmendmentOfADateOf} — so a payload merely naming a parent takes
   * nothing from anywhere, whether that parent is a non-repeating event or
   * another occurrence, whose recurrence {@code createEvent} nulls. That is
   * <b>not</b> the same set {@link #checkCanCreateEvent} relaxes the creation
   * right for, and deliberately so: the relaxation asks two further things —
   * the date must be one the series has, and it must not already be amended —
   * and this is a <b>superset</b> of it. Everything it covers and the
   * relaxation does not is a row a caller who <b>holds</b> the creation right
   * has legitimately created: a second amendment of the same date, one filed
   * into another calendar, one whose identifier the date check could not
   * resolve. Each of those is an exceptional occurrence and each owes its
   * attendees; withholding them would leave the orphan this method exists to
   * prevent. An ordinary event created with no attendee is a legitimate event
   * with no attendee and is left exactly as it was.
   *
   * @param occurrence the row just created, never null here
   * @param event the payload that created it, as the client sent it
   * @param parentEvent the stored event the payload names as parent, or null
   *          when it names none
   */
  private void inheritSeriesAttendees(Event occurrence, Event event, Event parentEvent) {
    if (occurrence == null || parentEvent == null || !isAmendmentOfADateOf(event, parentEvent)) {
      return;
    }
    EventOccurrence eventOccurrence = occurrence.getOccurrence();
    if (eventOccurrence == null || eventOccurrence.getId() == null) {
      return;
    }
    EventAttendeeList seriesAttendees = attendeeService.getEventAttendees(occurrence.getParentId());
    if (seriesAttendees == null) {
      return;
    }
    List<EventAttendee> inherited = cleanupAttendeeIds(seriesAttendees.getEventAttendees(eventOccurrence.getId()));
    if (inherited.isEmpty()) {
      return;
    }
    attendeeService.saveEventAttendees(occurrence,
                                       inherited,
                                       0,
                                       false,
                                       occurrence.getStatus() != EventStatus.CONFIRMED,
                                       null);
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
   * The permissions a user holds on an event, answered from a single reading
   * of their write right: only an attendee's right costs one more calendar
   * check, for the move right.
   *
   * @param event the event, as stored
   * @param userIdentityId {@link Identity} identifier of the user
   * @param isEventAttendee whether the user attends the event
   * @return the permissions, never null
   */
  private EventPermission permissionOf(Event event, long userIdentityId, boolean isEventAttendee) {
    EventWriteRight writeRight = writeRightOf(event, userIdentityId);
    return new EventPermission(writeRight != EventWriteRight.NONE,
                               isEventAttendee,
                               canMoveWith(writeRight, event, userIdentityId));
  }

  /**
   * Whether a write right lets its holder move the event into another calendar
   * (EXO-90149): the creator and whoever manages the event's calendar may, an
   * attendee allowed to update the event and a share editor may not.
   * {@link #writeRightOf} answers the attendee right before the calendar one,
   * so an attendee is asked again whether they also manage the calendar.
   * <p>
   * An exceptional occurrence is answered on its series, never on itself. An
   * attendee allowed to update a series may amend one of its dates in place,
   * and storing that amendment makes them the occurrence's creator; read off
   * the occurrence the right would then be {@link EventWriteRight#CREATOR},
   * which is the move right the series denies them — enough to take that date
   * out of the space for every member who reads it. The series is what
   * {@link #checkCanCreateEvent} already asks about for the same reason, and
   * the occurrence carrying its series' right is also what keeps the move
   * available to the series' creator, who is not the creator of a date
   * somebody else amended.
   *
   * @param writeRight the user's right, as {@link #writeRightOf} answered it
   * @param event the event, as stored
   * @param userIdentityId {@link Identity} identifier of the user
   * @return true when the user may move the event
   */
  private boolean canMoveWith(EventWriteRight writeRight, Event event, long userIdentityId) {
    Event moveSubject = event;
    EventWriteRight moveRight = writeRight;
    if (event.getParentId() > 0) {
      // The read is served by the event cache, and computeEventsAcl asks once
      // per series, so a listing pays it once however many dates it carries
      Event series = agendaEventStorage.getEventById(event.getParentId());
      if (series != null) {
        moveSubject = series;
        moveRight = writeRightOf(series, userIdentityId);
      }
    }
    if (moveRight == EventWriteRight.CREATOR || moveRight == EventWriteRight.CALENDAR) {
      return true;
    }
    if (moveRight != EventWriteRight.ATTENDEE) {
      return false;
    }
    Calendar calendar = agendaCalendarService.getCalendarById(moveSubject.getCalendarId());
    return calendar != null && Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId);
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
        boolean isEventAttendee = attendeeService.isEventAttendee(eventId, userIdentityId);
        permission = permissionOf(event, userIdentityId, isEventAttendee);
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
   * {@code createEvent} is checked by {@link #checkCanCreateEvent} instead,
   * which asks the creation right and the move right only when that occurrence
   * is filed into a calendar other than its series' (EXO-90382, EXO-90149).
   *
   * @param storedEvent the event as it is stored, carrying the calendar it is
   *          currently filed in
   * @param targetCalendar the stored calendar the event is filed into, already
   *          checked to exist
   * @param userIdentityId the {@link Identity} identifier of the user moving the
   *          event
   * @throws IllegalAccessException when the calendar changes and the user can't
   *           create events in the target calendar, or their only right over
   *           the event is an edit share on the calendar it leaves, or being
   *           one of its attendees without managing its calendar (EXO-90149)
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
    EventWriteRight writeRight = writeRightOf(storedEvent, userIdentityId);
    if (writeRight == EventWriteRight.SHARE_EDITOR) {
      throw new IllegalAccessException("User '" + userIdentityId + "' edits calendar " + storedEvent.getCalendarId()
          + " through a share and can't move event " + storedEvent.getId() + " out of it");
    }
    // Likewise "attendees can modify the event" lets an attendee change the
    // event, not take it away from everyone reading its calendar (EXO-90149).
    // The refusal names the right that is missing rather than the right the
    // user holds: an occurrence is answered on its series, so whoever is
    // refused here may well be its creator — a share editor who amended one
    // date is — and a message naming them an attendee would send a reader of
    // the log looking for the wrong thing.
    if (!canMoveWith(writeRight, storedEvent, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' doesn't hold the move right on event "
          + storedEvent.getId() + " (its creator, or a manager of its calendar) and can't move it out of calendar "
          + storedEvent.getCalendarId());
    }
    if (!canCreateEvent(targetCalendar, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't move event " + storedEvent.getId() + " to calendar "
          + targetCalendar.getId());
    }
  }

  /**
   * Checks that a user may create an event, and is the one place that decides
   * it: {@code createEvent} asks nothing else.
   * <p>
   * An ordinary event — no parent — needs the right to add events to its
   * calendar, and nothing has changed for it.
   * <p>
   * An event carrying a parent is an <b>exceptional occurrence</b>: it replaces
   * the computed occurrence in the series for every reader, so it always
   * requires the right to update the series (EXO-90381), as editing that
   * occurrence in place would. Without that, anyone able to add events to their
   * own calendar could remove one date of any recurring event from the views of
   * its owner and attendees, knowing only its identifier.
   * <p>
   * That update right is also <b>enough</b>, as long as the occurrence stays in
   * the series' own calendar (EXO-90382). The reason is that an exceptional
   * occurrence is a <b>change to the series</b>, not a new entry in the
   * calendar it is filed into. eXo is the outlier in storing that change as a
   * separate row, and asking that row for a creation right excluded people who
   * may plainly change the whole series — an attendee the organiser allowed to
   * update the event, and a creator who is no longer a redactor of the space —
   * from changing one date of it. Those two are the whole population this
   * relaxes for, and it is worth naming who is <b>not</b> in it: an editor of a
   * calendar shared with them (EXO-90378). {@link #canCreateEvent} answers true
   * for an edit share exactly when {@code writeRightOf} answers
   * {@code SHARE_EDITOR}, so on the series' own calendar such an editor already
   * held both rights and the old rule never stopped them.
   * <p>
   * The protocol corroborates the <i>shape</i>, and only the shape. Stated as
   * the connector's code has it rather than as its prose reads: an override
   * adopts the series' UID — {@code CaldavPushService.adoptOrMintUid} is keyed
   * on the parent's identifier, not the occurrence's — and the address is
   * {@code <collection>/<uid>.ics} with no occurrence component
   * ({@code objectHref}), so nothing ever allocates a second address for an
   * override. Two things are <b>not</b> true and are recorded here so the next
   * reader does not lean on them: the server is not "never asked for a create"
   * (the first push of a series the user has no copy of yet mints a UID and
   * creates the object under {@code If-None-Match: *}); and {@code IcsMerger}'s
   * "pushing an override replaces only the override for that instance"
   * describes a branch the shipped push does not take, since the sole call site
   * passes {@code occurrence = false}. Neither affects this rule: every
   * outbound write goes into the pushing user's <b>own</b> account, under their
   * own credentials, into their own collection, so there is no remote
   * counterpart of {@link #canCreateEvent} for this relaxation to bypass. The
   * rule stands on the eXo-side reason above; CalDAV is corroboration, not the
   * justification.
   * <p>
   * Filing the occurrence into <b>another</b> calendar is a different act: that
   * is a creation there, and it takes that date out of the series' calendar
   * for every reader, which is a move. So it needs both what
   * {@link #checkCanMoveEvent} asks of a move: the creation right on the
   * target, and the move right on the series (EXO-90149) — an attendee who
   * doesn't manage the series' calendar and a share editor are refused here as
   * they are there.
   * <p>
   * The <b>bound</b> this leaves, stated rather than left to be re-derived: one
   * row per date the series' rule generates, in the series' own calendar, for
   * as long as that rule runs — which for a series with no overall end is
   * unbounded in absolute terms, since the window handed to
   * {@link #seriesHasOccurrence} is explicit and never falls back on
   * {@code Utils.getOccurrences}' five-year cap. Each such row carries the
   * payload's own summary and times, stored verbatim, and takes its date out of
   * the series in exchange. That is the cardinality the feature itself has — a
   * user edits each date, over time — and it is inside the grant the organiser
   * made; revoking it is the organiser unticking
   * {@code allowAttendeeToUpdate}.
   * <p>
   * What the payload <b>is</b> decides that, not merely what it points at. The
   * relaxed branch is entered only by a real exceptional occurrence of a real
   * series — see {@link #isExceptionalOccurrenceOf}. Carrying a parent is not
   * enough: {@code parentId} comes from the client, nothing else in
   * {@code createEvent} requires the payload to be an occurrence, and a payload
   * that is not one keeps its own recurrence and is stored as an ordinary
   * event. Without that guard, naming any updatable event as parent would file
   * an arbitrary event — a whole new series, with its own summary and dates —
   * into the calendar that event happens to live in, which for an attendee
   * allowed to update it is a calendar they may not write to at all. Such a
   * payload falls through to the creation right, as it always did.
   * <p>
   * Two further things about that payload decide it, both found in review and
   * both client-controlled like the parent. The occurrence identifier must name
   * a date the series <b>has</b>, not merely be non-null — otherwise the same
   * caller posts any event at any date, and the row is purely additive because
   * it replaces no computed date in exchange. And the series must not
   * <b>already</b> carry an exceptional occurrence for that date — otherwise
   * the branch is re-entered for the same date as often as the caller likes,
   * and nothing else on this path deduplicates. Both are conditions on taking
   * the shortcut, not new refusals: a payload failing either one is asked for
   * the creation right, and a caller who holds it is unaffected.
   *
   * @param event the event to create, as the client sent it
   * @param parentEvent the stored recurring event the occurrence belongs to, or
   *          null when the created event has no parent
   * @param calendar the stored calendar the event is filed into, already
   *          checked to exist
   * @param userIdentityId the {@link Identity} identifier of the user creating
   *          the event
   * @throws IllegalAccessException when the event has a parent the user can't
   *           update, or files an occurrence of it into another calendar
   *           without the move right on the series (EXO-90149), or when the
   *           user can't add events to the calendar and the event is not a
   *           first amendment of one of its series' own dates, staying in that
   *           series' calendar
   */
  private void checkCanCreateEvent(Event event,
                                   Event parentEvent,
                                   Calendar calendar,
                                   long userIdentityId) throws IllegalAccessException {
    if (parentEvent != null) {
      if (!canUpdateEvent(parentEvent, userIdentityId)) {
        throw new IllegalAccessException("User '" + userIdentityId + "' can't create an occurrence of event "
            + parentEvent.getId());
      }
      if (parentEvent.getCalendarId() == calendar.getId() && isExceptionalOccurrenceOf(event, parentEvent)
          && !hasExceptionalOccurrence(parentEvent, event.getOccurrence().getId())) {
        return;
      }
      // Filed elsewhere, the occurrence takes its date out of the series'
      // calendar for every reader: that is a move, and needs the move right
      // (EXO-90149), which neither an attendee nor a share editor holds
      if (parentEvent.getCalendarId() != calendar.getId()
          && !canMoveWith(writeRightOf(parentEvent, userIdentityId), parentEvent, userIdentityId)) {
        throw new IllegalAccessException("User '" + userIdentityId + "' can't move an occurrence of event "
            + parentEvent.getId() + " out of calendar " + parentEvent.getCalendarId());
      }
    }
    if (!canCreateEvent(calendar, userIdentityId)) {
      throw new IllegalAccessException("User '" + userIdentityId + "' can't create an event in calendar " + calendar.getTitle());
    }
  }

  /**
   * Whether a payload handed to {@code createEvent} really is an exceptional
   * occurrence of the stored event it names as parent, which is the only thing
   * {@link #checkCanCreateEvent} relaxes the creation right for.
   * <p>
   * All three parts are load-bearing and none is checked anywhere else on the
   * create path. The payload must carry an <b>occurrence identifier</b> — the
   * date it replaces; without one {@code createEvent} keeps the payload's own
   * recurrence ({@code if (occurrence != null && occurrence.getId() != null)
   * event.setRecurrence(null)}) and stores an ordinary event, so an
   * occurrence-less payload naming a parent is a new event, not an amendment
   * of one. The parent must be a <b>series</b>: there is no such thing as one
   * date of a non-repeating event, which is why
   * {@code createEventExceptionalOccurrence} refuses that parent outright. And
   * that identifier must name a date the series <b>actually has</b> — see
   * {@link #seriesHasOccurrence}.
   * <p>
   * That third part is what makes the relaxation mean what it says. The other
   * two are satisfied by the payload's shape alone, and the identifier is
   * client-controlled like the parent: without it, an attendee the organiser
   * allowed to update a series filed in the organiser's own calendar could
   * post any event at all — any summary, any date, any number of times — by
   * naming that series as parent and any instant as the occurrence it amends.
   * Nothing downstream would have caught it: {@code createEvent} stores the
   * payload's own start and end verbatim rather than deriving them from the
   * identifier as {@code createEventExceptionalOccurrence} does, and a row
   * whose identifier falls outside the series is purely <b>additive</b> —
   * {@code AgendaEvent.getExceptionalOccurenceIdsByPeriod} matches it on a
   * window it is not in, so {@code filterExceptionalEvents} removes nothing in
   * exchange, while {@link #inheritSeriesAttendees} puts every invitee of the
   * series on it. Reproduced against this branch before the check was added,
   * and refused by the pre-image at the unconditional creation right this
   * delivery relaxes.
   *
   * @param event the event to create, as the client sent it
   * @param parentEvent the stored event it names as parent, never null here
   * @return true when the payload amends one date of that series
   */
  private boolean isExceptionalOccurrenceOf(Event event, Event parentEvent) {
    return isAmendmentOfADateOf(event, parentEvent)
        && seriesHasOccurrence(parentEvent, event.getOccurrence().getId());
  }

  /**
   * Whether a payload is shaped like an amendment of one date of the stored
   * event it names as parent: the parent is a <b>series</b>, and the payload
   * carries an <b>occurrence identifier</b>.
   * <p>
   * Split out of {@link #isExceptionalOccurrenceOf} so that the two questions
   * asked of such a payload stay separable. This one is about the payload and
   * its parent; {@link #seriesHasOccurrence} is about the <b>date</b>, and that
   * is the security-relevant half — it is what stops a client naming a date the
   * series does not have. {@link #inheritSeriesAttendees} deliberately asks
   * only this one: a row that was created anyway, by a caller who holds the
   * creation right and whose identifier the date check could not resolve, must
   * still get the series' attendees, or it is exactly the orphan EXO-90408
   * exists to prevent — {@code filterExceptionalEvents} knows nothing of these
   * guards and still takes that date out of the series for every reader.
   * Sharing one predicate would have tied who gets invited to a permission
   * decision, in the direction that hurts.
   *
   * @param event the event to create, as the client sent it
   * @param parentEvent the stored event it names as parent, never null here
   * @return true when the payload is shaped like an amendment of one of its
   *         dates
   */
  private static boolean isAmendmentOfADateOf(Event event, Event parentEvent) {
    return parentEvent.getRecurrence() != null
        && event.getOccurrence() != null
        && event.getOccurrence().getId() != null;
  }

  /**
   * Whether a series really has an occurrence on the date an identifier names.
   * <p>
   * The series is expanded around that date and the identifier is matched
   * against what comes out <b>on the UTC date</b>, which is the granularity the
   * rest of the exceptional-occurrence machinery already works at:
   * {@code AgendaEventStorage.getExceptionalOccurrenceEvent} queries the
   * identifier's UTC day, and {@code filterExceptionalEvents} matches on
   * {@code withZoneSameInstant(UTC).toLocalDate()}. Matching the instant alone
   * would be stricter than either, and would refuse the case
   * {@code filterExceptionalEvents} names in its own comment — an identifier
   * computed by the previous algorithm, on the right date at another
   * time of day. Because the row still replaces the computed occurrence at
   * that granularity, an identifier this admits costs the series its date in
   * exchange rather than adding to it. The window is the day before to the day
   * after, with a limit of three, the same window and limit
   * {@link #getEventOccurrence} expands for a reader — enough for every
   * frequency the recurrence editor offers, since none of them yields more than
   * three occurrences over three days.
   * <p>
   * The range test {@code createEventExceptionalOccurrence} makes
   * ({@code overallStart - 1 day <= date <= overallEnd}) is deliberately not
   * reused: it admits any date inside the span — every Wednesday of a Monday
   * series, and every future date of a series with no end at all.
   * <p>
   * It <b>fails closed</b>: a frequency this window cannot resolve — an hourly
   * or finer rule — is asked for the creation right rather than let through.
   * Such a rule is <b>not</b> only an import artefact, which is worth saying
   * because the shorter sentence invites the next reader to decide the case
   * cannot happen: {@code EventRecurrenceFrequency} carries {@code HOURLY},
   * {@code MINUTELY} and {@code SECONDLY}; the recurrence drawer does not offer
   * them, but a plain REST payload reaches them through
   * {@code RestEntityBuilder}, and {@code create_agenda_event} advertises them
   * to the model in {@code ai-tool-definitions.json}.
   * {@link #getEventOccurrence} expands the same window with the same limit, so
   * <i>reading</i> one of those occurrences is already broken the same way; and
   * {@link #inheritSeriesAttendees} asks a different question, so such a row
   * still gets its attendees and is not left an orphan.
   *
   * @param parentEvent the stored series, its recurrence already known to be
   *          non-null
   * @param occurrenceId the date the payload claims to amend
   * @return true when the series has an occurrence on that date
   */
  private boolean seriesHasOccurrence(Event parentEvent, ZonedDateTime occurrenceId) {
    LocalDate occurrenceDateUTC = occurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    List<Event> occurrences = Utils.getOccurrences(parentEvent,
                                                   occurrenceDateUTC.minusDays(1),
                                                   occurrenceDateUTC.plusDays(1),
                                                   3);
    return occurrences.stream()
                      .map(occurrence -> occurrence.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC))
                      .anyMatch(id -> id.toLocalDate().equals(occurrenceDateUTC));
  }

  /**
   * Whether the series already carries an exceptional occurrence for that
   * date.
   * <p>
   * The relaxation is the right to <b>amend one date</b> of a series, which is
   * a thing there is one of. Nothing on this path deduplicates — the unique
   * constraint the schema carries for it, {@code agenda-rdbms} changeset
   * {@code 1.0.0-10}, is on {@code (EVENT_ID, OCCURRENCE_ID)} and
   * {@code EVENT_ID} is the row's own primary key, so it constrains nothing —
   * and {@code saveEventExceptionalOccurrence} is the path that checks for an
   * existing row before making one. Without this, the relaxed branch could be
   * entered again and again for the same date, each call adding another row to
   * a calendar the caller may not write to. A caller who <b>does</b> hold the
   * creation right is unaffected: they fall through to it and
   * {@code createEvent} behaves exactly as it did.
   *
   * @param parentEvent the stored series
   * @param occurrenceId the date the payload claims to amend
   * @return true when an exceptional occurrence already exists for that date
   */
  private boolean hasExceptionalOccurrence(Event parentEvent, ZonedDateTime occurrenceId) {
    return agendaEventStorage.getExceptionalOccurrenceEvent(parentEvent.getId(), occurrenceId) != null;
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
