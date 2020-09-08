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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.plugin.AgendaExternalUserIdentityProvider;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class AgendaEventAttendeeServiceImpl implements AgendaEventAttendeeService {

  private static final String        SEPARATOR = "|";

  private AgendaEventAttendeeStorage attendeeStorage;

  private ListenerService            listenerService;

  private IdentityManager            identityManager;

  private CodecInitializer           codecInitializer;

  public AgendaEventAttendeeServiceImpl(AgendaEventAttendeeStorage attendeeStorage,
                                        ListenerService listenerService,
                                        IdentityManager identityManager,
                                        CodecInitializer codecInitializer) {
    this.attendeeStorage = attendeeStorage;
    this.codecInitializer = codecInitializer;
    this.identityManager = identityManager;
    this.listenerService = listenerService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void saveEventAttendees(Event event,
                                 List<EventAttendee> attendees,
                                 long creatorUserId,
                                 boolean sendInvitations,
                                 boolean resetResponses) {
    long eventId = event.getId();

    List<EventAttendee> savedAttendees = getEventAttendees(event.getId());
    List<EventAttendee> newAttendees = attendees == null ? Collections.emptyList() : attendees;
    List<EventAttendee> attendeesToDelete =
                                          savedAttendees.stream()
                                                        .filter(attendee -> newAttendees.stream()
                                                                                        .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                        .collect(Collectors.toList());

    // Delete attendees
    for (EventAttendee eventAttendee : attendeesToDelete) {
      attendeeStorage.removeEventAttendee(eventAttendee.getId());
    }

    List<EventAttendee> attendeesToCreate =
                                          newAttendees.stream()
                                                      .filter(attendee -> savedAttendees.stream()
                                                                                        .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    // Create new attendees
    for (EventAttendee eventAttendee : attendeesToCreate) {
      long identityId = eventAttendee.getIdentityId();

      EventAttendeeResponse response = null;
      if (identityId == creatorUserId) {
        if (event.getStatus() == EventStatus.CONFIRMED) {
          response = EventAttendeeResponse.ACCEPTED;
        } else if (event.getStatus() == EventStatus.TENTATIVE) {
          response = EventAttendeeResponse.TENTATIVE;
        } else if (event.getStatus() == EventStatus.CANCELED) {
          response = EventAttendeeResponse.DECLINED;
        }
      } else {
        response = EventAttendeeResponse.NEEDS_ACTION;
      }
      eventAttendee.setResponse(response);
      attendeeStorage.saveEventAttendee(eventAttendee, eventId);
    }

    if (resetResponses) {
      List<EventAttendee> attendeesToUpdate =
                                            savedAttendees.stream()
                                                          .filter(attendee -> newAttendees.stream()
                                                                                          .anyMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                          .collect(Collectors.toList());
      for (EventAttendee eventAttendee : attendeesToUpdate) {
        if (eventAttendee.getIdentityId() != creatorUserId) {
          eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
          attendeeStorage.saveEventAttendee(eventAttendee, eventId);
        }
      }
    }

    if (sendInvitations) {
      sendInvitations(eventId);
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.attendees.saved", eventId, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventAttendee> getEventAttendees(long eventId) {
    return attendeeStorage.getEventAttendees(eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeResponse getEventResponse(long eventId, long identityId) throws ObjectNotFoundException {
    EventAttendee attendee = attendeeStorage.getEventAttendee(eventId, identityId);
    if (attendee == null) {
      throw new ObjectNotFoundException("Attendee with identity id " + eventId + " wasn't found in event " + eventId);
    }
    return attendee.getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId, long identityId, EventAttendeeResponse response) throws ObjectNotFoundException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    EventAttendee attendee = attendeeStorage.getEventAttendee(eventId, identityId);
    if (attendee == null) {
      throw new ObjectNotFoundException("Attendee with identity id " + eventId + " wasn't found in event " + eventId);
    }
    attendee.setResponse(response);
    attendeeStorage.saveEventAttendee(attendee, eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId, String email) throws TokenServiceInitializationException {
    return generateEncryptedToken(eventId, email, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response) throws TokenServiceInitializationException {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (StringUtils.isBlank(emailOrUsername)) {
      throw new IllegalArgumentException("email is mandatory");
    }
    StringBuilder tokenFlatStringBuilder = new StringBuilder().append(String.valueOf(eventId))
                                                              .append(SEPARATOR)
                                                              .append(emailOrUsername);
    if (response != null) {
      tokenFlatStringBuilder.append(SEPARATOR).append(response.getValue());
    }
    String tokenFlat = tokenFlatStringBuilder.toString();
    return codecInitializer.getCodec().encode(tokenFlat);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Identity decryptUserIdentity(long eventId,
                                      String token,
                                      EventAttendeeResponse response) throws TokenServiceInitializationException,
                                                                      IllegalAccessException {
    String tokenFlat = codecInitializer.getCodec().decode(token);
    String[] tokenParts = tokenFlat.split(SEPARATOR);
    if (tokenParts.length < 2) {
      throw new IllegalAccessException("Wrong token format");
    }
    String eventIdString = tokenParts[0];
    if (!eventIdString.equals(String.valueOf(eventId))) {
      throw new IllegalAccessException("Wrong eventId from token");
    }
    if (response != null) {
      String responseString = tokenParts.length > 2 ? tokenParts[2] : null;
      if (!StringUtils.equals(responseString, response.getValue())) {
        throw new IllegalAccessException("Wrong response from token");
      }
    }
    String emailOrUsername = tokenParts[1];
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, emailOrUsername);
    if (identity == null) {
      identity = identityManager.getOrCreateIdentity(AgendaExternalUserIdentityProvider.NAME, emailOrUsername);
    }
    return identity;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendInvitations(long eventId) {
    // TODO Auto-generated method stub
  }
}
