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

import java.util.List;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public interface AgendaEventInvitationService {

  /**
   * Generates a token that will be used to authenticate user when requesting
   * REST API in anonymous mode
   * 
   * @param eventId {@link Event} technical identifier
   * @param email User email
   * @return encrypted token containing "EVENT_ID|EMAIL"
   * @throws TokenServiceInitializationException when an error occurs while
   *           encrypting data
   */
  public String generateEncryptedToken(long eventId, String email) throws TokenServiceInitializationException;

  /**
   * Generates a token that will be used to authenticate user when requesting
   * REST API in anonymous mode. This
   * 
   * @param eventId {@link Event} technical identifier
   * @param emailOrUsername User name or email
   * @param response {@link EventAttendeeResponse} value for chosen answer
   * @return encrypted token containing "EVENT_ID|EMAIL|ATTENDEE_RESPONSE"
   * @throws TokenServiceInitializationException when an error occurs while
   *           encrypting data
   */
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response) throws TokenServiceInitializationException;

  /**
   * Reads token content and retrieves user Social Identity from email or
   * username contained in token
   * 
   * @param token encrypted token
   * @param eventId {@link Event} technical identifier
   * @param response {@link EventAttendeeResponse} value for chosen answer
   * @return {@link Identity} of user
   * @throws TokenServiceInitializationException when an error occurs while
   *           decrypting data
   * @throws IllegalAccessException when the token has bad format
   */
  public Identity readUserIdentity(String token,
                                   long eventId,
                                   EventAttendeeResponse response) throws TokenServiceInitializationException,
                                                                   IllegalAccessException;

  /**
   * Retrieves the event response of a user. If the user didn't responded to the
   * event, the default value {@link EventAttendeeResponse#NEEDS_ACTION} will be
   * retrieved.
   * 
   * @param identityId {@link Identity} technical identifier of user
   * @param eventId Technical identifier of {@link Event}
   * @return {@link EventAttendeeResponse}, no null value is returned
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when user is not an invitee of the event
   */
  public EventAttendeeResponse getEventResponse(String identityId, long eventId) throws ObjectNotFoundException,
                                                                                 IllegalAccessException;

  /**
   * @param identityId {@link Identity} technical identifier of user
   * @param eventId Technical identifier of {@link Event}
   * @param response User response of type {@link EventAttendeeResponse} to the
   *          event. The value {@link EventAttendeeResponse#NEEDS_ACTION} isn't
   *          allowed.
   * @throws ObjectNotFoundException when event with provided identifier doesn't
   *           exists
   * @throws IllegalAccessException when user is not an invitee of the event
   */
  public void sendEventResponse(String identityId, long eventId, EventAttendeeResponse response) throws ObjectNotFoundException,
                                                                                                 IllegalAccessException;

  /**
   * Invites a user, space or external user to an event.
   * 
   * @param eventId technical identifier of the event
   * @param identityId technical identitifier of {@link Identity}
   * @param response default answer of the attendee that could be null, in which
   *          case {@link EventAttendeeResponse#NEEDS_ACTION} will be used
   * @param sendInvitation whether send invitations or not
   */
  public void invite(long eventId, long identityId, EventAttendeeResponse response, boolean sendInvitation);

  /**
   * @param event {@link Event} to attach attendees
   * @param attendees {@link List} of {@link EventAttendee} to save for event
   * @param creatorIdentityId technical identifier if {@link Identity} updating
   *          event
   * @param sendInvitations whether send invitations to other attendees or not
   */
  void saveEventAttendees(Event event, List<EventAttendee> attendees, long creatorIdentityId, boolean sendInvitations);

  /**
   * Sends an invitation to an event for a user/space
   * 
   * @param event {@link Event} to invite users to
   * @param receiverId identity Id of invitation receiver
   */
  public void sendInvitation(Event event, long receiverId);
}
