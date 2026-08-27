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
   * retrieved.
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param occurrenceId event occurrence id
   * @param identityId {@link Identity} technical identifier of user
   * @return {@link EventAttendeeResponse}, no null value is returned
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when user is not an invitee of the event
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
   * @throws IllegalAccessException when user is not an invitee of the event
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
   * @throws IllegalAccessException when user is not an invitee of the event
   */
  public void sendUpcomingEventResponse(long eventId,
                                        ZonedDateTime occurrenceId,
                                        long identityId,
                                        EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * @param eventId Technical identifier of {@link Event}
   * @param identityId {@link Identity} technical identifier of user
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @param broadcast whether broadcast event about this change or not
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when user is not an invitee of the event
   */
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response,
                                boolean broadcast) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Checks whether the user is an attendee of the event or not
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param identityId user name
   * @return true if user is an attendee of the event, else return false
   */
  boolean isEventAttendee(long eventId, long identityId);

}
