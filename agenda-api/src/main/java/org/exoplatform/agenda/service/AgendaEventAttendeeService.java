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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventAttendeeService {

  /**
   * Return the list of attendees of an event
   * 
   * @param eventId agenda {@link Event} identifier
   * @return {@link EventAttendeeList}
   */
  public EventAttendeeList getEventAttendees(long eventId);

  /**
   * Return the list of attendees of an event having a specific responses.
   * 
   * @param eventId agenda {@link Event} identifier
   * @param occurrenceId event occurrence id
   * @param responses Array of answers of attendees to retrieve
   * @return {@link List} of {@link EventAttendee}
   */
  List<EventAttendee> getEventAttendees(long eventId,
                                        ZonedDateTime occurrenceId,
                                        EventAttendeeResponse... responses);

  /**
   * Return the list of attendees of an event having a specific responses.
   * 
   * @param eventId agenda {@link Event} identifier
   * @param responses Array of answers of attendees to retrieve
   * @return {@link EventAttendeeList}
   */
  EventAttendeeList getEventAttendees(long eventId, EventAttendeeResponse... responses);

  /**
   * Sends an invitation to event attendees of type: user, space or external
   * user.
   * 
   * @param event {@link Event}
   * @param eventAttendees {@link List} of {@link EventAttendee} of the event
   * @param eventModifications {@link AgendaEventModification} contains a
   *          {@link Set} of {@link AgendaEventModificationType} to indicate
   *          event modification types: fields modifications, creation or
   *          deletion
   */
  public void sendInvitations(Event event,
                              List<EventAttendee> eventAttendees,
                              AgendaEventModification eventModifications);

  /**
   * @param event {@link Event} to attach attendees
   * @param attendees {@link List} of {@link EventAttendee} to save for event
   * @param creatorIdentityId technical identifier if {@link Identity} updating
   *          event
   * @param sendInvitations whether send invitations to other attendees or not
   * @param resetResponses whether reset attendees responses or not to default
   *          {@link EventAttendeeResponse#NEEDS_ACTION}. Whatever the value of
   *          this parameter, the response of a newly added attendee is forced to
   *          {@link EventAttendeeResponse#NEEDS_ACTION} when it isn't the
   *          attendee designated by creatorIdentityId, since a user can only
   *          answer an invitation for himself
   * @param eventModifications {@link AgendaEventModification} contains a
   *          {@link Set} of {@link AgendaEventModificationType} to indicate
   *          event modification types: fields modifications, creation or
   *          deletion
   * @return {@link Set} of {@link AgendaEventModificationType} containing
   *         modifications made on event attendees
   */
  Set<AgendaEventModificationType> saveEventAttendees(Event event,
                                                      List<EventAttendee> attendees,
                                                      long creatorIdentityId,
                                                      boolean sendInvitations,
                                                      boolean resetResponses,
                                                      AgendaEventModification eventModifications);

  /**
   * Generates a token that will be used to authenticate user when requesting
   * REST API in anonymous mode.
   *
   * @param eventId {@link Event} technical identifier
   * @param email User email
   * @return encrypted token containing "EVENT_ID|EMAIL||EXPIRY", or null when
   *         the event carries no date to bound the token by
   */
  public String generateEncryptedToken(long eventId, String email);

  /**
   * Generates a token that will be used to authenticate user when requesting
   * REST API in anonymous mode.
   *
   * <p>
   * The token expires with the meeting it answers. The expiry is the last field
   * of the payload, an epoch second derived from the event by
   * <code>Utils.invitationTokenExpiry</code>; it is a pure function of the
   * event, so the same event and answer always yield the same token, which is
   * what lets the calendar copy carry these links in a DESCRIPTION without
   * being rewritten on every sweep (EXO-89752, EXO-89753).
   *
   * @param eventId {@link Event} technical identifier
   * @param emailOrUsername User name or email
   * @param response {@link EventAttendeeResponse} value for chosen answer, null
   *          for a token that authenticates without carrying an answer
   * @return encrypted token containing
   *         "EVENT_ID|EMAIL|ATTENDEE_RESPONSE|EXPIRY", the answer field being
   *         empty when there is none; null when the event carries no date to
   *         bound the token by, in which case no link should be offered
   */
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response);

  /**
   * Reads token content and retrieves user Social Identity from email or
   * username contained in token. An internal attendee is retrieved from the
   * organization identity provider, an external attendee - a guest invited by
   * mail address only - is retrieved among the guest attendees of the event.
   * No identity is ever created by this lookup, so a token carrying a mail
   * address which attends nothing resolves to null.
   * 
   * <p>
   * A token whose meeting is over is refused, with
   * {@link org.exoplatform.agenda.exception.EventInvitationExpiredException} so
   * that a caller can tell a lapsed link apart from a forged one. A token
   * minted before EXO-89752 carries no expiry of its own and is bounded by
   * asking the event for the same bound at this point, so no invitation already
   * delivered stops working and none of them stays replayable either.
   *
   * @param token encrypted token
   * @param eventId {@link Event} technical identifier
   * @param response {@link EventAttendeeResponse} value for chosen answer
   * @return {@link Identity} of user, or null when the token designates neither
   *         an existing internal user nor a guest attendee of the event
   * @throws IllegalAccessException when the token has bad format, designates
   *           another event or another answer, or - as
   *           {@link org.exoplatform.agenda.exception.EventInvitationExpiredException}
   *           - when the meeting it answers is over
   */
  public Identity decryptUserIdentity(long eventId,
                                      String token,
                                      EventAttendeeResponse response) throws IllegalAccessException;

  /**
   * Retrieves the event response of a user. If the user didn't responded to the
   * event, the default value {@link EventAttendeeResponse#NEEDS_ACTION} will be
   * retrieved. Read with no occurrence id on a series, it is the user's answer
   * to the series as a whole, as {@link #getSeriesResponse(long, long)} reads
   * it.
   *
   * @param eventId Technical identifier of {@link Event}
   * @param occurrenceId event occurrence id, or null
   * @param identityId {@link Identity} technical identifier of user
   * @return {@link EventAttendeeResponse}, no null value is returned
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  public EventAttendeeResponse getEventResponse(long eventId,
                                                ZonedDateTime occurrenceId,
                                                long identityId) throws ObjectNotFoundException,
                                                                 IllegalAccessException;

  /**
   * @param eventId Technical identifier of {@link Event}
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response) throws ObjectNotFoundException,
                                                                IllegalAccessException;

  /**
   * Sends an event response for a recurrent event starting from a specific
   * occurrence
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param occurrenceId event occurrence id
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  public void sendUpcomingEventResponse(long eventId,
                                        ZonedDateTime occurrenceId,
                                        long identityId,
                                        EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Sends an event response for a recurrent event starting from a specific
   * occurrence, and returns what it stored.
   * <p>
   * On a series with a capacity limit, a Yes, or
   * {@link EventAttendeeResponse#WAITING} which asks for the same, is decided
   * date by date: stored as a Yes over the dates with a seat free or already
   * held, and as {@link EventAttendeeResponse#WAITING} over the others, one
   * attendee row per run of dates with the same outcome. The answer reaches
   * the row of each date individually modified the user attends, from the
   * date answered from; on the row of one that is over, it is stored as with
   * no limit, a {@link EventAttendeeResponse#WAITING} asked becoming a Yes:
   * no seat is decided there.
   *
   * @param eventId Technical identifier of the series
   * @param occurrenceId occurrence id of the date the answer starts from
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @param broadcast whether the answer is reported as sent by the user
   * @return the answer stored at the date answered, and the first date it left
   *         waiting
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  EventResponseOutcome sendUpcomingEventResponse(long eventId,
                                                 ZonedDateTime occurrenceId,
                                                 long identityId,
                                                 EventAttendeeResponse response,
                                                 boolean broadcast) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Reads a user's answer to an event as a whole. On a series, where a user
   * may hold several rows, each over a range of its dates, it is the answer
   * valid at the next date the series carries, the first that is neither
   * cancelled nor over; when none remains, at the last date it carries; when
   * every date is cancelled, at the last date its rule produces. Validity is
   * judged on the date's occurrence id. On an event of no series, it is the
   * user's row.
   * <p>
   * It reads the stored answer and checks no right: the caller checks the
   * right to read the event.
   *
   * @param seriesId Technical identifier of the series, or of the event
   * @param identityId {@link Identity} technical identifier of user
   * @return the answer, {@link EventAttendeeResponse#NEEDS_ACTION} when the
   *         user holds none at that date
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   */
  EventAttendeeResponse getSeriesResponse(long seriesId, long identityId) throws ObjectNotFoundException;

  /**
   * Reads the user's row {@link #getSeriesResponse(long, long)} reads the
   * answer of: on a series, the row valid at the date its answer is read at,
   * else, when the user's rows all lie elsewhere, a copy of their first row
   * answering {@link EventAttendeeResponse#NEEDS_ACTION} with no answer date;
   * on an event of no series, the user's row. It checks no right.
   *
   * @param seriesId Technical identifier of the series, or of the event
   * @param identityId {@link Identity} technical identifier of user
   * @return the row, null when the user holds none on the event
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   */
  EventAttendee getSeriesAttendee(long seriesId, long identityId) throws ObjectNotFoundException;

  /**
   * Reads the attendees of an event as a whole, each once: on a series, every
   * attendee's row as {@link #getSeriesAttendee(long, long)} reads it, an
   * attendee with no row valid at the date its answers are read at being
   * listed as answering {@link EventAttendeeResponse#NEEDS_ACTION}; on an
   * event of no series, its rows. It checks no right.
   *
   * @param seriesId Technical identifier of the series, or of the event
   * @return the rows, in the order the attendees' rows are stored
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   */
  List<EventAttendee> getSeriesAttendees(long seriesId) throws ObjectNotFoundException;

  /**
   * Counts the seats of one date of an event: on a date with its own row, or
   * on an event of no series, every row of it; on a date of a series with no
   * row of its own, the series' rows valid at that date. With no date on a
   * series, the date its answers are read at (see
   * {@link #getSeriesResponse(long, long)}). It checks no right.
   *
   * @param eventId Technical identifier of the event, of a series, or of the
   *          row of one of its dates
   * @param occurrenceId the occurrence id of a date of the series, or null
   * @param identityId {@link Identity} technical identifier of the user whose
   *          rank on the waiting list is read, 0 for none
   * @return the counts at the date, with the user's rank when they wait there
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   */
  EventSeatCounts getSeatCounts(long eventId, ZonedDateTime occurrenceId, long identityId) throws ObjectNotFoundException;

  /**
   * @param eventId Technical identifier of {@link Event}
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @param broadcast whether broadcast event about this change or not
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response,
                                boolean broadcast) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Sends an event response for one date of a series, or for the whole event
   * when no date is given. The date's own row is found, or created as a copy
   * of the series when the date has none, under the lock of the series, then
   * answered: a caller never creates the row itself before answering.
   * <p>
   * On an event with a capacity limit, a Yes, or
   * {@link EventAttendeeResponse#WAITING} which asks for the same, is stored
   * as a Yes when a seat is free at the date or the attendee already holds
   * one, and as {@link EventAttendeeResponse#WAITING} otherwise. An answer to
   * every date of a series reaches the row of each date individually modified
   * the user attends; on the row of one that is over, it is stored as with no
   * limit, a {@link EventAttendeeResponse#WAITING} asked becoming a Yes: no
   * seat is decided there.
   *
   * @param eventId Technical identifier of the series, or of the event when
   *          no date is given
   * @param occurrenceId occurrence id of the date answered, null to answer
   *          every date of the event
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @param broadcast whether the answer is reported as sent by the user
   * @return the answer stored, and the first date it left waiting
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when the user may not answer the event: not
   *           an attendee, and the event isn't open to them (see
   *           {@link #canRespondToEvent(Event, long)})
   */
  EventResponseOutcome sendEventResponse(long eventId,
                                         ZonedDateTime occurrenceId,
                                         long identityId,
                                         EventAttendeeResponse response,
                                         boolean broadcast) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Starts every answer of an event over, as a change of its dates does: each
   * attendee's answer becomes {@link EventAttendeeResponse#NEEDS_ACTION}, a
   * place on the waiting list included, on the event and, for a series, on
   * the rows of its dates. A reset frees no seat: nobody is promoted, and no
   * answer is reported as sent by its attendee. The pools forget that they
   * reached their capacity, and so does the series through which a date
   * holding the series' limit reports.
   *
   * @param eventId Technical identifier of {@link Event}
   * @param keptIdentityIds identities whose answer is kept, typically the
   *          user who made the change
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   */
  void resetEventResponses(long eventId, Set<Long> keptIdentityIds) throws ObjectNotFoundException;

  /**
   * Checks whether the user is an attendee of the event or not
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param identityId user name
   * @return true if user is an attendee of the event, else return false
   */
  boolean isEventAttendee(long eventId, long identityId);

  /**
   * Checks whether the user may write their own answer on the event, which is
   * the one right that opening an event widens (eXIP 7.3.0.20). Two halves:
   * <ul>
   * <li>the attendee half is exactly {@link #isEventAttendee(long, long)} on
   * the event itself, so an exceptional occurrence is checked against its own
   * attendee list, as today;</li>
   * <li>the open half: the event is open and the user can access its calendar
   * (a member of the owning space). The flag is the event's own: a date
   * individually modified answers with its own value.</li>
   * </ul>
   * Opening never widens who can see the event, only who can answer it.
   *
   * @param event {@link Event} to answer, as stored
   * @param identityId {@link Identity} technical identifier of user
   * @return true when the user may send or read an answer on the event, else
   *         false
   */
  boolean canRespondToEvent(Event event, long identityId);

}
