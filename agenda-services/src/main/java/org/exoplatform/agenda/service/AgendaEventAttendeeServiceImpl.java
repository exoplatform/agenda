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

import static org.exoplatform.agenda.util.NotificationUtils.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.exception.EventInvitationExpiredException;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class AgendaEventAttendeeServiceImpl implements AgendaEventAttendeeService {

  private static final Log           LOG       = ExoLogger.getLogger(AgendaEventAttendeeServiceImpl.class);

  private static final String        SEPARATOR            = "@@@";

  /**
   * Position of the expiry inside a decoded token payload.
   *
   * <p>
   * A payload shorter than this is a token minted before EXO-89752, which
   * carries no expiry of its own and is bounded against the event instead.
   */
  private static final int           EXPIRY_TOKEN_PART    = 3;

  private AgendaEventAttendeeStorage attendeeStorage;

  private AgendaEventStorage         eventStorage;

  private ListenerService            listenerService;

  private IdentityManager            identityManager;

  private SpaceService               spaceService;

  private CodecInitializer           codecInitializer;

  private AgendaCalendarService       agendaCalendarService;

  private AgendaCalendarService      calendarService;

  private DecideSteps                decideSteps;

  private AgendaEventService         agendaEventService;

  public AgendaEventAttendeeServiceImpl(AgendaEventAttendeeStorage attendeeStorage,
                                        AgendaEventStorage eventStorage,
                                        AgendaCalendarService calendarService,
                                        ListenerService listenerService,
                                        IdentityManager identityManager,
                                        SpaceService spaceService,
                                        CodecInitializer codecInitializer) {
    this.attendeeStorage = attendeeStorage;
    this.eventStorage = eventStorage;
    this.calendarService = calendarService;
    this.codecInitializer = codecInitializer;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.decideSteps = new DecideSteps(eventStorage);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<AgendaEventModificationType> saveEventAttendees(Event event,
                                                             List<EventAttendee> attendees,
                                                             long creatorUserId,
                                                             boolean sendInvitations,
                                                             boolean resetResponses,
                                                             AgendaEventModification eventModifications) {
    // The caller hands the event: what is resolved again, when a date moved to
    // another series meanwhile, is only the series to lock
    return decideSteps.resolvingAgainOnce(() -> {
      Event storedEvent = eventStorage.getEventById(event.getId());
      return decideSteps.decide(DecideSteps.targetOf(storedEvent == null ? event : storedEvent),
                                () -> writeEventAttendees(event,
                                                          attendees,
                                                          creatorUserId,
                                                          sendInvitations,
                                                          resetResponses,
                                                          eventModifications));
    });
  }

  private Set<AgendaEventModificationType> writeEventAttendees(Event event,
                                                               List<EventAttendee> attendees,
                                                               long creatorUserId,
                                                               boolean sendInvitations,
                                                               boolean resetResponses,
                                                               AgendaEventModification eventModifications) {
    long eventId = event.getId();
    EventStatus eventStatus = event.getStatus();
    // The pool's capacity is the stored one, read under the lock, never the
    // caller's copy of the event
    Event storedEvent = eventStorage.getEventById(eventId);
    Event pool = storedEvent == null ? event : storedEvent;

    List<EventAttendee> oldAttendees = getEventAttendees(event.getId()).getEventAttendees();
    List<EventAttendee> newAttendees = attendees == null ? Collections.emptyList() : attendees;

    Set<AgendaEventModificationType> eventModificationTypes = new HashSet<>();

    boolean seatFreed = processAttendeesToDelete(oldAttendees, newAttendees, eventModificationTypes);
    List<Long> seatRequesters = processAttendeesToCreate(pool,
                                                         eventStatus,
                                                         oldAttendees,
                                                         newAttendees,
                                                         creatorUserId,
                                                         resetResponses,
                                                         eventModificationTypes);
    if (resetResponses) {
      resetResponses(eventId, Collections.emptySet());
    } else {
      // A seat the list frees goes to the waiting list first: the attendees
      // the list adds with a Yes asked after them
      if (seatFreed) {
        promoteWaitingAttendees(eventId);
      }
      for (long seatRequester : seatRequesters) {
        saveEventAttendee(pool, null, seatRequester, EventAttendeeResponse.ACCEPTED, false);
      }
    }
    processSendingInvitation(event, attendees, sendInvitations, eventModifications);

    Utils.broadcastEvent(listenerService, "exo.agenda.event.attendees.saved", eventId, 0);

    return eventModificationTypes;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeList getEventAttendees(long eventId) {
    return attendeeStorage.getEventAttendees(eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeList getEventAttendees(long eventId, EventAttendeeResponse... responses) {
    return attendeeStorage.getEventAttendees(eventId, responses);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventAttendee> getEventAttendees(long eventId,
                                               ZonedDateTime occurrenceId,
                                               EventAttendeeResponse... responses) {
    EventAttendeeList eventAttendeeList = attendeeStorage.getEventAttendees(eventId, responses);
    return eventAttendeeList.getEventAttendees(occurrenceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeResponse getEventResponse(long eventId,
                                                ZonedDateTime occurrenceId,
                                                long identityId) throws ObjectNotFoundException,
                                                                 IllegalAccessException {
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
    if (!canRespondToEvent(event, identityId)) {
      throw new IllegalAccessException("User " + identityId + " may not answer event " + eventId
          + ": not an attendee, and the event isn't open to them");
    }
    EventAttendeeList eventAttendeeList = attendeeStorage.getEventAttendees(eventId, identityId);
    if (eventAttendeeList.isEmpty()) {
      return EventAttendeeResponse.NEEDS_ACTION;
    }
    EventAttendee eventAttendee = eventAttendeeList.getEventAttendee(identityId, occurrenceId);
    return eventAttendee == null ? EventAttendeeResponse.NEEDS_ACTION : eventAttendee.getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException {
    sendEventResponse(eventId, identityId, response, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendUpcomingEventResponse(long eventId,
                                        ZonedDateTime occurrenceId,
                                        long identityId,
                                        EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (occurrenceId == null) {
      throw new IllegalArgumentException("occurrenceId is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (identityId <= 0) {
      throw new IllegalArgumentException("identityId is mandatory");
    }
    try {
      decideSteps.<Void, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
        }
        if (event.getRecurrence() == null) {
          throw new IllegalStateException("Event with id " + eventId + " isn't recurrent");
        }
        checkCanRespond(event, identityId);
        return decideSteps.decide(DecideSteps.targetOf(event), () -> {
          writeUpcomingEventResponse(eventId, occurrenceId, identityId, response);
          return null;
        });
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
  }

  private EventResponseOutcome writeUpcomingEventResponse(long eventId,
                                                          ZonedDateTime occurrenceId,
                                                          long identityId,
                                                          EventAttendeeResponse response) {
    Event series = eventStorage.getEventById(eventId);
    // The answer starts from the date as the date's row stores it: the
    // identifier the occurrence list gives an all-day date the clocks start
    // past midnight is past the UTC midnight that row carries
    ZonedDateTime fromDate = Utils.getOccurrenceId(series.isAllDay(), occurrenceId, series.getTimeZoneId());
    EventAttendee stored = saveEventAttendee(series, fromDate, identityId, response, true);

    // Apply modification on exceptional occurrences as well
    ZonedDateTime firstWaitingOccurrenceId = null;
    List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId, fromDate);
    for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
      if (isEventAttendee(exceptionalOccurenceEventId, identityId)) {
        Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
        EventAttendee propagated = saveEventAttendee(occurrence, null, identityId, response, false);
        firstWaitingOccurrenceId = earliestWaiting(firstWaitingOccurrenceId, occurrence, propagated);
      }
    }
    return new EventResponseOutcome(stored.getResponse(), firstWaitingOccurrenceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response,
                                boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    answerEvent(eventId, identityId, response, broadcast);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventResponseOutcome sendEventResponse(long eventId,
                                                ZonedDateTime occurrenceId,
                                                long identityId,
                                                EventAttendeeResponse response,
                                                boolean broadcast) throws ObjectNotFoundException,
                                                                   IllegalAccessException {
    if (occurrenceId == null) {
      return answerEvent(eventId, identityId, response, broadcast);
    } else {
      return answerDate(eventId, occurrenceId, identityId, response, broadcast);
    }
  }

  private EventResponseOutcome answerEvent(long eventId,
                                           long identityId,
                                           EventAttendeeResponse response,
                                           boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    try {
      return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
        }
        checkCanRespond(event, identityId);
        return decideSteps.decide(DecideSteps.targetOf(event),
                                  () -> writeEventResponse(eventId, identityId, response, broadcast));
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
  }

  /**
   * Answers one date of a series: under the lock of the series, the date's
   * own row is found, or created as a copy of the series when it has none,
   * and the answer is decided on that row. Two first answers on one date so
   * end on the same row, and a caller never creates the row before it
   * answers.
   */
  private EventResponseOutcome answerDate(long seriesId,
                                          ZonedDateTime occurrenceId,
                                          long identityId,
                                          EventAttendeeResponse response,
                                          boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (seriesId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    try {
      return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event series = eventStorage.getEventById(seriesId);
        if (series == null) {
          throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
        }
        if (series.getRecurrence() == null) {
          throw new IllegalStateException("Event with id " + seriesId + " isn't recurrent");
        }
        // Checked before the step, so that a refused caller is told so without
        // a row being written; checked again under the lock on the row answered
        Event knownOccurrence = eventStorage.findExceptionalOccurrence(series,
                                                                       Utils.getOccurrenceId(series.isAllDay(),
                                                                                             occurrenceId,
                                                                                             series.getTimeZoneId()));
        checkCanRespond(knownOccurrence == null ? series : knownOccurrence, identityId);
        return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> decide(DecideSteps.targetOf(series), () -> {
          Event occurrence = findOrCreateOccurrence(seriesId, occurrenceId);
          checkCanRespond(occurrence, identityId);
          return writeEventResponse(occurrence.getId(), identityId, response, broadcast);
        });
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
    }
  }

  private Event findOrCreateOccurrence(long seriesId, ZonedDateTime occurrenceId) throws ObjectNotFoundException {
    try {
      return getAgendaEventService().saveEventExceptionalOccurrence(seriesId, occurrenceId);
    } catch (AgendaException e) {
      if (e.getAgendaExceptionType() == AgendaExceptionType.EVENT_NOT_FOUND) {
        throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
      }
      throw new IllegalStateException("The date " + occurrenceId + " of event " + seriesId + " can't be answered", e);
    }
  }

  private void checkCanRespond(Event event, long identityId) throws ObjectNotFoundException, IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }
    checkNotSubscribedByAnotherOwner(event, identityId);
    if (!canRespondToEvent(event, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " may not answer event with id " + event.getId()
          + ": not an attendee, and the event isn't open to them");
    }
  }

  private EventResponseOutcome writeEventResponse(long eventId,
                                                  long identityId,
                                                  EventAttendeeResponse response,
                                                  boolean broadcast) {
    Event event = eventStorage.getEventById(eventId);
    EventAttendee stored = saveEventAttendee(event, null, identityId, response, broadcast);
    ZonedDateTime firstWaitingOccurrenceId = earliestWaiting(null, event, stored);

    // Apply modification on exceptional occurrences as well
    if (event.getRecurrence() != null) {
      List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId);
      for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
        if (isEventAttendee(exceptionalOccurenceEventId, identityId)) {
          Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
          EventAttendee propagated = saveEventAttendee(occurrence, null, identityId, response, false);
          firstWaitingOccurrenceId = earliestWaiting(firstWaitingOccurrenceId, occurrence, propagated);
        }
      }
    }
    return new EventResponseOutcome(stored.getResponse(), firstWaitingOccurrenceId);
  }

  /**
   * @param current the earliest waiting date found so far, or null
   * @param pool a row an answer was stored on
   * @param stored the attendee row stored on it
   * @return the earlier of the current one and the pool's date, when the pool
   *         is a date of a series, not cancelled, that the answer left waiting
   */
  private static ZonedDateTime earliestWaiting(ZonedDateTime current, Event pool, EventAttendee stored) {
    ZonedDateTime date = occurrenceDateOf(pool);
    if (date == null || stored.getResponse() != EventAttendeeResponse.WAITING || pool.getStatus() == EventStatus.CANCELLED) {
      return current;
    }
    return current == null || date.isBefore(current) ? date : current;
  }

  /**
   * @param pool a stored event row
   * @return the occurrence id of the date it is, when it is a date of a
   *         series, else null
   */
  private static ZonedDateTime occurrenceDateOf(Event pool) {
    return pool.getParentId() > 0 && pool.getOccurrence() != null ? pool.getOccurrence().getId() : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetEventResponses(long eventId, Set<Long> keptIdentityIds) throws ObjectNotFoundException {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    Set<Long> kept = keptIdentityIds == null ? Collections.emptySet() : keptIdentityIds;
    try {
      decideSteps.<Void, ObjectNotFoundException, RuntimeException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
        }
        // With nothing to start over, the event is neither locked nor evicted
        // from the cache
        if (!hasResponsesToReset(event, kept)) {
          return null;
        }
        return decideSteps.decide(DecideSteps.targetOf(event), () -> {
          resetResponses(eventId, kept);
          return null;
        });
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
  }

  /**
   * Starts every answer of an event over, on its row and on the rows of its
   * dates when it is a series, the waiting list included. A reset frees no
   * seat: nobody is promoted, and no answer is reported as sent.
   */
  private void resetResponses(long eventId, Set<Long> keptIdentityIds) {
    Event event = eventStorage.getEventById(eventId);
    resetPoolResponses(event, keptIdentityIds);
    if (event.getRecurrence() != null) {
      for (long exceptionalOccurenceEventId : eventStorage.getExceptionalOccurenceIds(eventId)) {
        Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
        if (occurrence != null) {
          resetPoolResponses(occurrence, keptIdentityIds);
        }
      }
    }
  }

  /**
   * Whether a reset would change anything, on the event or, for a series, on
   * the row of one of its dates, where the answers to single dates are.
   */
  private boolean hasResponsesToReset(Event event, Set<Long> keptIdentityIds) {
    if (poolHasResponsesToReset(event, keptIdentityIds)) {
      return true;
    }
    if (event.getRecurrence() == null) {
      return false;
    }
    for (long exceptionalOccurenceEventId : eventStorage.getExceptionalOccurenceIds(event.getId())) {
      Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
      if (occurrence != null && poolHasResponsesToReset(occurrence, keptIdentityIds)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether an attendee not kept holds an answer on a pool, or the pool is
   * marked as having reached its capacity: what
   * {@link #resetPoolResponses(Event, Set)} starts over.
   */
  private boolean poolHasResponsesToReset(Event pool, Set<Long> keptIdentityIds) {
    return pool.getCapacityReachedDate() != null
        || attendeeStorage.getEventAttendees(pool.getId())
                          .getEventAttendees()
                          .stream()
                          .anyMatch(row -> !keptIdentityIds.contains(row.getIdentityId())
                              && row.getResponse() != EventAttendeeResponse.NEEDS_ACTION);
  }

  private void resetPoolResponses(Event pool, Set<Long> keptIdentityIds) {
    long eventId = pool.getId();
    Map<Long, List<EventAttendee>> rowsByIdentity = attendeeStorage.getEventAttendees(eventId)
                                                                   .getEventAttendees()
                                                                   .stream()
                                                                   .collect(Collectors.groupingBy(EventAttendee::getIdentityId,
                                                                                                  LinkedHashMap::new,
                                                                                                  Collectors.toList()));
    rowsByIdentity.forEach((identityId, rows) -> {
      if (keptIdentityIds.contains(identityId)
          || rows.stream().allMatch(row -> row.getResponse() == EventAttendeeResponse.NEEDS_ACTION)) {
        return;
      }
      EventAttendee oldAttendee = rows.get(0).clone();
      EventAttendee attendee;
      if (rows.size() > 1) {
        // One answer again, to every date, as an answer to all dates writes it
        rows.forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
        attendee = new EventAttendee(0, eventId, identityId, EventAttendeeResponse.NEEDS_ACTION);
      } else {
        attendee = rows.get(0);
        attendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      }
      attendee.setResponseDate(ZonedDateTime.now(ZoneOffset.UTC));
      attendeeStorage.saveEventAttendee(attendee, eventId);
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);
    });
    if (pool.getCapacityReachedDate() != null) {
      eventStorage.saveCapacityReachedDate(eventId, null);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId, String email) {
    return generateEncryptedToken(eventId, email, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response) {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (StringUtils.isBlank(emailOrUsername)) {
      throw new IllegalArgumentException("email is mandatory");
    }
    long expiry = Utils.invitationTokenExpiry(eventStorage.getEventById(eventId));
    if (expiry <= 0) {
      // No event, or an event carrying no date at all: there is no meeting to
      // bound the link by. Minting one anyway would either be a link that never
      // expires - the defect being fixed - or a link dead on arrival. Neither
      // belongs in a mail, so no link is offered at all.
      LOG.warn("No expiry could be derived for event with id '{}', no invitation token is generated", eventId);
      return null;
    }
    // The answer slot is always written, empty when the token carries no
    // answer, so that the expiry keeps the same position in every token. The
    // reader below relies on it: a three field payload is a token minted before
    // EXO-89752, not a four field one with something missing.
    StringBuilder tokenFlatStringBuilder = new StringBuilder().append(String.valueOf(eventId))
                                                              .append(SEPARATOR)
                                                              .append(emailOrUsername)
                                                              .append(SEPARATOR)
                                                              .append(response == null ? "" : response.getValue())
                                                              .append(SEPARATOR)
                                                              .append(expiry);
    String tokenFlat = tokenFlatStringBuilder.toString();
    try {
      return codecInitializer.getCodec().encode(tokenFlat);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error generating Token", e);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Identity decryptUserIdentity(long eventId,
                                      String token,
                                      EventAttendeeResponse response) throws IllegalAccessException {
    String tokenFlat;
    try {
      tokenFlat = codecInitializer.getCodec().decode(token);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error decrypting Token", e);
      return null;
    }
    // -1 keeps the trailing fields of a token whose answer slot is empty, which
    // split() would otherwise drop along with the expiry sitting after it.
    String[] tokenParts = tokenFlat.split("\\" + SEPARATOR, -1);
    if (tokenParts.length < 2) {
      throw new IllegalAccessException("Wrong token format");
    }
    String eventIdString = tokenParts[0];
    if (!eventIdString.equals(String.valueOf(eventId))) {
      throw new IllegalAccessException("Wrong eventId from token");
    }
    if (response != null) {
      String responseString = tokenParts.length > 2 ? StringUtils.defaultIfEmpty(tokenParts[2], null) : null;
      if (!StringUtils.equals(responseString, response.getValue())) {
        throw new IllegalAccessException("Wrong response from token");
      }
    }
    // Deliberately after the event and answer checks: a token repointed at
    // another meeting or another answer must still be refused as forged, and
    // must not be told instead that it merely expired.
    checkInvitationNotExpired(eventId, tokenParts);
    String emailOrUsername = tokenParts[1];
    return getAttendeeIdentity(eventId, emailOrUsername);
  }

  /**
   * Refuses a token whose meeting is over.
   *
   * <p>
   * <b>How tokens minted before EXO-89752 are handled, and why nothing breaks.</b>
   * Every invitation already delivered carries a payload with no expiry in it.
   * Rejecting those outright would break the Accept button of every mail ever
   * sent, and accepting them unchecked would leave the replayable link in place
   * for exactly the population that already has one - so neither is done.
   * A payload with no expiry is bounded by asking the event for the bound now,
   * with {@link Utils#invitationTokenExpiry(Event)}: the same rule, the same
   * grace, computed instead of read.
   *
   * <p>
   * That is what makes this a fix rather than a migration. There is no
   * transition window to configure, no flag to remember to turn off, and no
   * date on which old links stop working in a batch: an old link is answerable
   * for exactly as long as a new one for the same meeting would be, and every
   * link - old and new alike - is bounded from the moment this deploys. The
   * transition ends by itself as the old mails age out.
   *
   * <p>
   * The two are not merely equivalent, and the difference is the reason the
   * field is in the payload at all: a stored expiry pins the bound as it stood
   * when the invitation was issued, so a link cannot be quietly extended by
   * moving the meeting later, whereas a derived one follows the event.
   *
   * @param eventId technical identifier of the {@link Event} being answered,
   *          already checked against the token
   * @param tokenParts the decoded payload, split on the separator
   * @throws EventInvitationExpiredException when the meeting is far enough past
   *           that the link is no longer honoured, or when no bound can be
   *           established at all
   */
  private void checkInvitationNotExpired(long eventId, String[] tokenParts) throws EventInvitationExpiredException {
    long expiry = 0;
    if (tokenParts.length > EXPIRY_TOKEN_PART && StringUtils.isNotBlank(tokenParts[EXPIRY_TOKEN_PART])) {
      try {
        expiry = Long.parseLong(tokenParts[EXPIRY_TOKEN_PART]);
      } catch (NumberFormatException e) {
        // A payload that decoded cleanly cannot have a corrupt expiry unless it
        // was forged. Leaving it at 0 refuses it below.
        LOG.debug("Unparsable expiry in the invitation token of event with id '{}'", eventId, e);
      }
    } else {
      expiry = Utils.invitationTokenExpiry(eventStorage.getEventById(eventId));
    }
    long now = ZonedDateTime.now().toEpochSecond();
    if (expiry <= 0 || now > expiry) {
      throw new EventInvitationExpiredException("Invitation token for event with id '" + eventId + "' expired at '" + expiry
          + "', current time is '" + now + "'");
    }
  }

  /**
   * Resolves the {@link Identity} designated by the identifier carried inside an
   * invitation token. An internal attendee is designated by its username and
   * lives under the {@link OrganizationIdentityProvider}, while an external
   * attendee - a guest invited by mail address only - lives under the
   * {@link AgendaGuestUserIdentityProvider}. The organization provider is tried
   * first so that the behavior of internal attendees is left untouched, then the
   * guest attendees of the designated event are looked up.
   *
   * @param eventId technical identifier of the {@link Event} the token answers
   * @param emailOrUsername username of an internal attendee or mail address of a
   *          guest attendee, as carried by the token
   * @return the {@link Identity} of the attendee, or null when the identifier
   *         designates neither an existing internal user nor a guest attendee of
   *         the event
   */
  private Identity getAttendeeIdentity(long eventId, String emailOrUsername) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, emailOrUsername);
    if (identity != null) {
      return identity;
    }
    return getGuestAttendeeIdentity(eventId, emailOrUsername);
  }

  /**
   * Looks up the guest {@link Identity} of a given mail address among the
   * attendees of an event. The lookup is deliberately made on the event
   * attendees and never through
   * {@link IdentityManager#getOrCreateIdentity(String, String)}: the
   * {@link AgendaGuestUserIdentityProvider} accepts any parsable mail address,
   * so asking it to resolve an unknown address would silently create a stray
   * identity for anyone able to forge a token payload.
   *
   * @param eventId technical identifier of the {@link Event} whose attendees are
   *          searched
   * @param email mail address of the guest attendee to look for
   * @return the guest {@link Identity} attending the event with that mail
   *         address, or null when no guest attendee matches
   */
  private Identity getGuestAttendeeIdentity(long eventId, String email) {
    if (StringUtils.isBlank(email)) {
      return null;
    }
    EventAttendeeList eventAttendees = getEventAttendees(eventId);
    if (eventAttendees == null || eventAttendees.getEventAttendees() == null) {
      return null;
    }
    return eventAttendees.getEventAttendees()
                         .stream()
                         .map(eventAttendee -> Utils.getIdentityById(identityManager, eventAttendee.getIdentityId()))
                         .filter(attendeeIdentity -> attendeeIdentity != null
                             && AgendaGuestUserIdentityProvider.NAME.equals(attendeeIdentity.getProviderId())
                             && StringUtils.equalsIgnoreCase(attendeeIdentity.getRemoteId(), email))
                         .findFirst()
                         .orElse(null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEventAttendee(long eventId, long identityId) { // NOSONAR
    EventAttendeeList eventAttendees = getEventAttendees(eventId);
    return Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees);
  }

  /**
   * Replaces how the calendar of an event is read, for the tests; the default
   * resolves the Kernel component on first use.
   *
   * @param agendaCalendarService the calendar service
   */
  public void setAgendaCalendarService(AgendaCalendarService agendaCalendarService) {
    this.agendaCalendarService = agendaCalendarService;
  }

  /**
   * Refuses an answer to an event imported by a subscription somebody else
   * owns — a space's (EXO-90373).
   * <p>
   * Such an event is a copy of a calendar the owner subscribed to: nobody was
   * invited to it, it is read-only everywhere else
   * ({@code AgendaEventServiceImpl#canUpdateEvent}), and an answer recorded on
   * it would be an answer to an invitation that does not exist. The owner of a
   * personal subscription is left alone: their own imported events carry their
   * attendee row by design (EXO-90278). The calendar service is a Kernel
   * component reached on first use; when it cannot be reached the answer is
   * allowed, as it was before this guard — the attendee check below is what
   * refuses it in fact, since a space's imported event has no attendee at all.
   *
   * @param event the event being answered
   * @param identityId identity identifier of the user answering
   * @throws IllegalAccessException when the event is another owner's imported copy
   */
  private void checkNotSubscribedByAnotherOwner(Event event, long identityId) throws IllegalAccessException {
    if (agendaCalendarService == null) {
      agendaCalendarService = ExoContainerContext.getService(AgendaCalendarService.class);
    }
    if (agendaCalendarService == null) {
      return;
    }
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    if (calendar != null && calendar.isSubscription() && calendar.getOwnerId() != identityId) {
      throw new IllegalAccessException("Event " + event.getId() + " comes from a calendar subscription of identity "
          + calendar.getOwnerId() + ": user " + identityId + " cannot answer it");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canRespondToEvent(Event event, long identityId) {
    if (event == null || identityId <= 0) {
      return false;
    }
    // The attendee half stays per event: on an exceptional occurrence, its own
    // attendee list. Resolving it to the series would let a participant removed
    // from one occurrence answer on it even on a locked series (US05)
    if (isEventAttendee(event.getId(), identityId)) {
      return true;
    }
    if (!isOpen(event)) {
      return false;
    }
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    return Utils.canAccessEventCalendar(identityManager, spaceService, calendar, identityId);
  }

  /**
   * Since US06 the open flag is a property of each row: a date individually
   * modified answers with its own value, which a series change merges into but
   * never overwrites when it was customised there. The event this reads is
   * always one that has a row — a computed occurrence is never stored and
   * never reaches a response.
   *
   * @param event {@link Event} as stored
   * @return whether that event is open to the members of its space
   */
  private boolean isOpen(Event event) {
    return Boolean.TRUE.equals(event.getOpen());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendInvitations(Event event, List<EventAttendee> eventAttendees, AgendaEventModification eventModifications) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(EVENT_AGENDA, event);
    ctx.append(EVENT_ATTENDEE, eventAttendees);
    ctx.append(EVENT_MODIFIER, eventModifications.getModifierId());

    if (eventModifications.hasModification(AgendaEventModificationType.DELETED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.DELETED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.ADDED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.START_DATE_UPDATED)
        || eventModifications.hasModification(AgendaEventModificationType.END_DATE_UPDATED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.DATES_UPDATED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.UPDATED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.UPDATED.name());
    }

    if (eventModifications.hasModification(AgendaEventModificationType.DELETED)) {
      dispatch(ctx, AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.UPDATED)) {
      dispatch(ctx, AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)
        && event.getStatus() == EventStatus.TENTATIVE) {
      dispatch(ctx, AGENDA_DATE_POLL_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)
        && event.getStatus() == EventStatus.CONFIRMED) {
      dispatch(ctx, AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    }
  }

  /**
   * Stores an answer of an attendee on one row of an event, the pool: the row
   * of an event of no series, of a date of a series, or the series itself.
   * A Yes takes a seat when one is free or the attendee already holds one,
   * and waits otherwise. When the answer leaves the attendee fewer seats than
   * the rows it replaces held (a seat given up, seated rows merged), the
   * waiting list is promoted in the same transaction while seats are free.
   * An answer stored as a Yes that leaves the pool full while it is not
   * marked marks it (see {@link #markCapacityReached(Event)}).
   *
   * @param pool the stored row answered, as read under the lock
   * @param occurrenceId the date the answer starts from on a series, null for
   *          an answer to every date of the row
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param userResponseSent whether the attendee sent it, reported as such
   * @return the attendee row stored
   */
  private EventAttendee saveEventAttendee(Event pool,
                                          ZonedDateTime occurrenceId,
                                          long identityId,
                                          EventAttendeeResponse requested,
                                          boolean userResponseSent) {
    long eventId = pool.getId();
    EventAttendeeList eventAttendees = attendeeStorage.getEventAttendees(eventId, identityId);
    EventAttendee attendee = eventAttendees.getEventAttendee(identityId, occurrenceId);
    EventAttendee oldAttendee = attendee == null ? null : attendee.clone();
    // An answer to every date of the row replaces every row of the attendee:
    // the seats they hold and their place on the waiting list are read from
    // all of those rows, not only from the first one
    List<EventAttendee> replacedRows = occurrenceId == null ? eventAttendees.getEventAttendees(identityId)
                                                            : oldAttendee == null ? List.of() : List.of(oldAttendee);
    long seatsHeld = replacedRows.stream().filter(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED).count();
    EventAttendeeResponse response = decideSeat(pool, identityId, requested, seatsHeld > 0);
    // The date changes only with the answer stored: when every replaced row
    // already holds it, the earliest of their dates is kept, a null one
    // included; now otherwise, a Yes again after a No on some dates included
    boolean answerUnchanged = !replacedRows.isEmpty()
        && replacedRows.stream().allMatch(row -> row.getResponse() == response);
    ZonedDateTime responseDate = answerUnchanged ? replacedRows.stream()
                                                               .min(Comparator.comparing(EventAttendee::getResponseDate,
                                                                                         Comparator.nullsFirst(Comparator.naturalOrder())))
                                                               .orElseThrow()
                                                               .getResponseDate()
                                                 : ZonedDateTime.now(ZoneOffset.UTC);
    if (occurrenceId == null) {
      List<EventAttendee> userEventAttendees = eventAttendees.getEventAttendees(identityId);
      if (userEventAttendees.size() > 1) {
        userEventAttendees.forEach(userEventAttendee -> {
          attendeeStorage.removeEventAttendee(userEventAttendee.getId());
        });
        attendee = new EventAttendee(0, eventId, identityId, response);
      } else if (attendee == null) {
        attendee = new EventAttendee(0, eventId, identityId, response);
      } else {
        attendee.setResponse(response);
      }
      attendee.setResponseDate(responseDate);
      attendeeStorage.saveEventAttendee(attendee, eventId);
    } else {
      List<EventAttendee> userAttendees = eventAttendees.getEventAttendees();
      for (EventAttendee eventAttendee : userAttendees) {
        if (eventAttendee.getFromOccurrenceId() != null && (eventAttendee.getFromOccurrenceId().isAfter(occurrenceId)
            || eventAttendee.getFromOccurrenceId().isEqual(occurrenceId))) {
          attendeeStorage.removeEventAttendee(eventAttendee.getId());
        } else if ((eventAttendee.getFromOccurrenceId() == null || eventAttendee.getFromOccurrenceId().isBefore(occurrenceId))
            && (eventAttendee.getUntilOccurrenceId() == null || eventAttendee.getUntilOccurrenceId().isAfter(occurrenceId))) {
          eventAttendee.setUntilOccurrenceId(occurrenceId);
          attendeeStorage.saveEventAttendee(eventAttendee, eventId);
        }
      }
      attendee = new EventAttendee(0, eventId, identityId, occurrenceId, null, response, responseDate);
      attendeeStorage.saveEventAttendee(attendee, eventId);
    }

    // Broadcast technical event when saving any new response for a user
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);

    // Broadcast user response to an event
    if (userResponseSent) {
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SENT, oldAttendee, attendee);
    }

    // Fewer seats held than before: a seat given up, or rows merged into one
    if (seatsHeld > (response == EventAttendeeResponse.ACCEPTED ? 1 : 0)) {
      promoteWaitingAttendees(eventId);
    }
    if (response == EventAttendeeResponse.ACCEPTED) {
      markCapacityReached(pool);
    }
    return attendee;
  }

  /**
   * Decides the answer stored for a requested one on a pool. Every Yes on a
   * pool of one date counts, its range ignored: a date's row gets the series'
   * rows valid at that date, and an answer on the pool keeps the row it
   * finds, so a row out of range is left from an older shape of the data —
   * a copy made before that filter, or a ranged answer kept when a series
   * became an event of no series — and counting it takes a seat away rather
   * than giving one more than the capacity.
   *
   * @param pool the stored row answered
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param seatHeld whether one of the attendee's rows the answer replaces
   *          holds a seat
   * @return the answer to store
   */
  private EventAttendeeResponse decideSeat(Event pool,
                                           long identityId,
                                           EventAttendeeResponse requested,
                                           boolean seatHeld) {
    int capacity = SeatRule.capacityOf(pool);
    if (!SeatRule.asksForSeat(requested) || capacity <= 0) {
      return SeatRule.decide(requested, null, 0, 0);
    }
    if (pool.getRecurrence() != null) {
      throw new IllegalStateException("Event " + pool.getId() + " is a series with a capacity limit, whose answers are decided date by date");
    }
    int acceptedByOthers = SeatRule.acceptedAt(attendeeStorage.getSeatRows(pool.getId(), EventAttendeeResponse.ACCEPTED),
                                               null,
                                               identityId);
    return SeatRule.decide(requested, seatHeld ? EventAttendeeResponse.ACCEPTED : null, capacity, acceptedByOthers);
  }

  /**
   * Marks a pool of one date as having reached its capacity when an answer
   * stored as a Yes leaves it full while it is not marked, and queues the
   * event that reports it: the answer filling its last seat, or, after a
   * reset that cleared the mark while the answers it kept still fill the
   * pool, a Yes given again by an attendee holding one of those seats. A
   * promotion does not mark the pool it fills.
   *
   * @param pool the stored row answered
   */
  private void markCapacityReached(Event pool) {
    int capacity = SeatRule.capacityOf(pool);
    if (capacity <= 0 || pool.getCapacityReachedDate() != null || pool.getRecurrence() != null) {
      return;
    }
    int accepted = SeatRule.acceptedAt(attendeeStorage.getSeatRows(pool.getId(), EventAttendeeResponse.ACCEPTED), null, 0);
    if (accepted < capacity) {
      return;
    }
    ZonedDateTime capacityReachedDate = ZonedDateTime.now(ZoneOffset.UTC);
    eventStorage.saveCapacityReachedDate(pool.getId(), capacityReachedDate);
    pool.setCapacityReachedDate(capacityReachedDate);
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_CAPACITY_REACHED, pool.getId(), occurrenceDateOf(pool));
  }

  /**
   * Gives the free seats of a pool of one date to its waiting attendees, in
   * the order they asked: earliest answer date first, then lowest row id.
   * Runs inside the decide step that freed the seat. A promotion is stored as
   * a Yes of now, and reported as saved and as promoted, never as an answer
   * the attendee sent.
   *
   * @param eventId the identifier of the pool's event row
   */
  void promoteWaitingAttendees(long eventId) {
    List<EventAttendee> waitingAttendees = attendeeStorage.getWaitingAttendees(eventId);
    if (waitingAttendees.isEmpty()) {
      return;
    }
    Event pool = eventStorage.getEventById(eventId);
    if (pool.getRecurrence() != null) {
      throw new IllegalStateException("Event " + eventId + " is a series with a waiting list, whose attendees are promoted date by date");
    }
    int capacity = SeatRule.capacityOf(pool);
    int accepted = SeatRule.acceptedAt(attendeeStorage.getSeatRows(eventId, EventAttendeeResponse.ACCEPTED), null, 0);
    for (EventAttendee waitingAttendee : waitingAttendees) {
      if (!SeatRule.hasFreeSeat(capacity, accepted)) {
        return;
      }
      EventAttendee oldAttendee = waitingAttendee.clone();
      waitingAttendee.setResponse(EventAttendeeResponse.ACCEPTED);
      waitingAttendee.setResponseDate(ZonedDateTime.now(ZoneOffset.UTC));
      attendeeStorage.saveEventAttendee(waitingAttendee, eventId);
      accepted++;
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, waitingAttendee);
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_ATTENDEE_PROMOTED, oldAttendee, waitingAttendee);
    }
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private void dispatch(NotificationContext ctx, String... pluginId) {
    List<NotificationCommand> commands = new ArrayList<>(pluginId.length);
    for (String p : pluginId) {
      NotificationCommand command = ctx.makeCommand(PluginKey.key(p));
      if (command != null) {
        commands.add(command);
      }
    }

    if (commands.isEmpty() || DecideContext.queue(() -> execute(ctx, commands))) {
      return;
    }
    execute(ctx, commands);
  }

  private void execute(NotificationContext ctx, List<NotificationCommand> commands) {
    try {
      ctx.getNotificationExecutor().with(commands).execute(ctx);
    } catch (Exception e) {
      LOG.warn("Error sending invitation notifications", e);
    }
  }

  private void processSendingInvitation(Event event,
                                        List<EventAttendee> attendees,
                                        boolean sendInvitations,
                                        AgendaEventModification eventModifications) {
    if (sendInvitations) {
      if (event.getStatus() == EventStatus.CANCELLED) {
        eventModifications.setModificationTypes(Collections.singleton(AgendaEventModificationType.DELETED));
      }
      sendInvitations(event, attendees, eventModifications);
    }
  }

  /**
   * Creates the rows of the attendees the saved list adds. Another attendee's
   * answer carried by the list starts over; the saver's own Yes, or place on
   * the waiting list, takes a seat like any answer: stored as a Yes on a pool
   * with no limit, and returned to be decided once the list is stored on a
   * pool with one. Rows copied from a series (creatorUserId 0) keep the
   * answer and the answer date they had.
   *
   * @return the identity of the saver when their answer is to be decided
   */
  private List<Long> processAttendeesToCreate(Event pool,
                                              EventStatus eventStatus,
                                              List<EventAttendee> oldAttendees,
                                              List<EventAttendee> newAttendees,
                                              long creatorUserId,
                                              boolean resetResponses,
                                              Set<AgendaEventModificationType> eventModificationTypes) {
    long eventId = pool.getId();
    List<EventAttendee> attendeesToCreate =
                                          newAttendees.stream()
                                                      .filter(attendee -> oldAttendees.stream()
                                                                                      .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    List<Long> seatRequesters = new ArrayList<>();
    // Create new attendees
    for (EventAttendee eventAttendee : attendeesToCreate) {
      if (resetResponses || eventAttendee.getResponse() == null || eventStatus == EventStatus.TENTATIVE
          || isOtherAttendeeResponse(eventAttendee, creatorUserId)) {
        eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      } else if (creatorUserId > 0 && SeatRule.asksForSeat(eventAttendee.getResponse())) {
        if (SeatRule.capacityOf(pool) > 0) {
          eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
          seatRequesters.add(eventAttendee.getIdentityId());
        } else {
          eventAttendee.setResponse(EventAttendeeResponse.ACCEPTED);
        }
      }
      if (creatorUserId > 0) {
        eventAttendee.setResponseDate(eventAttendee.getResponse() == EventAttendeeResponse.NEEDS_ACTION ? null
                                                                                                        : ZonedDateTime.now(ZoneOffset.UTC));
      }
      attendeeStorage.saveEventAttendee(eventAttendee, eventId);
    }
    if (!attendeesToCreate.isEmpty()) {
      eventModificationTypes.add(AgendaEventModificationType.ATTENDEE_ADDED);
    }
    return seatRequesters;
  }

  /**
   * A user can only answer an invitation for himself, thus a response carried by
   * the saved event can't be applied on another attendee than the user who
   * creates or updates the event.
   *
   * @param eventAttendee {@link EventAttendee} to create
   * @param creatorUserId {@link Identity} technical identifier of the user
   *          creating or updating the event, 0 when the attendee isn't saved on
   *          behalf of a user (exceptional occurrence creation for example,
   *          where responses are copied from the parent event)
   * @return true when the response is made on behalf of another attendee
   */
  private boolean isOtherAttendeeResponse(EventAttendee eventAttendee, long creatorUserId) {
    return creatorUserId > 0 && eventAttendee.getIdentityId() != creatorUserId;
  }

  /**
   * Removes the rows of the attendees the saved list drops.
   *
   * @return whether a removed row held a seat, which frees it
   */
  private boolean processAttendeesToDelete(List<EventAttendee> oldAttendees,
                                           List<EventAttendee> newAttendees,
                                           Set<AgendaEventModificationType> eventModificationTypes) {
    List<EventAttendee> attendeesToDelete =
                                          oldAttendees.stream()
                                                      .filter(attendee -> newAttendees.stream()
                                                                                      .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    // Delete attendees
    for (EventAttendee eventAttendee : attendeesToDelete) {
      attendeeStorage.removeEventAttendee(eventAttendee.getId());
    }
    if (!attendeesToDelete.isEmpty()) {
      eventModificationTypes.add(AgendaEventModificationType.ATTENDEE_DELETED);
    }
    return attendeesToDelete.stream().anyMatch(attendee -> attendee.getResponse() == EventAttendeeResponse.ACCEPTED);
  }
}
