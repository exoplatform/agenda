// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
   *          {@link EventAttendeeResponse#NEEDS_ACTION}
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
   * REST API in anonymous mode
   * 
   * @param eventId {@link Event} technical identifier
   * @param email User email
   * @return encrypted token containing "EVENT_ID|EMAIL"
   */
  public String generateEncryptedToken(long eventId, String email);

  /**
   * Generates a token that will be used to authenticate user when requesting
   * REST API in anonymous mode. This
   * 
   * @param eventId {@link Event} technical identifier
   * @param emailOrUsername User name or email
   * @param response {@link EventAttendeeResponse} value for chosen answer
   * @return encrypted token containing "EVENT_ID|EMAIL|ATTENDEE_RESPONSE"
   */
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response);

  /**
   * Reads token content and retrieves user Social Identity from email or
   * username contained in token
   * 
   * @param token encrypted token
   * @param eventId {@link Event} technical identifier
   * @param response {@link EventAttendeeResponse} value for chosen answer
   * @return {@link Identity} of user
   * @throws IllegalAccessException when the token has bad format
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
