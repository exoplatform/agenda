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

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.plugin.AgendaExternalUserIdentityProvider;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.services.listener.ListenerService;
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

  private static final String        SEPARATOR = "|";

  private AgendaEventAttendeeStorage attendeeStorage;

  private AgendaEventStorage         eventStorage;

  private ListenerService            listenerService;

  private IdentityManager            identityManager;

  private SpaceService               spaceService;

  private CodecInitializer           codecInitializer;

  public AgendaEventAttendeeServiceImpl(AgendaEventAttendeeStorage attendeeStorage,
                                        AgendaEventStorage eventStorage,
                                        ListenerService listenerService,
                                        IdentityManager identityManager,
                                        SpaceService spaceService,
                                        CodecInitializer codecInitializer) {
    this.attendeeStorage = attendeeStorage;
    this.eventStorage = eventStorage;
    this.codecInitializer = codecInitializer;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
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
                                 boolean resetResponses,
                                 EventModificationType eventModificationType) {
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
      if (resetResponses || eventAttendee.getResponse() == null) {
        eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      }
      attendeeStorage.saveEventAttendee(eventAttendee, eventId);
    }

    if (resetResponses) {
      List<EventAttendee> attendeesToUpdate =
                                            savedAttendees.stream()
                                                          .filter(attendee -> newAttendees.stream()
                                                                                          .anyMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                          .collect(Collectors.toList());
      for (EventAttendee eventAttendee : attendeesToUpdate) {
        try {
          sendEventResponse(eventId, eventAttendee.getIdentityId(), EventAttendeeResponse.NEEDS_ACTION);
        } catch (Exception e) {
          LOG.warn("Error initializing default reminders of event {} for user with id {}",
                   eventId,
                   eventAttendee.getIdentityId(),
                   e);
        }
      }
    }

    try {
      if (creatorUserId > 0 && isEventAttendee(eventId, creatorUserId)) {
        if (event.getStatus() == EventStatus.CONFIRMED) {
          sendEventResponse(eventId, creatorUserId, EventAttendeeResponse.ACCEPTED);
        } else if (event.getStatus() == EventStatus.TENTATIVE) {
          sendEventResponse(eventId, creatorUserId, EventAttendeeResponse.TENTATIVE);
        } else if (event.getStatus() == EventStatus.CANCELLED) {
          sendEventResponse(eventId, creatorUserId, EventAttendeeResponse.DECLINED);
        }
      }
    } catch (Exception e) {
      LOG.warn("Error initializing default reminders of event {} for creator with id {}", eventId, creatorUserId, e);
    }

    if (sendInvitations) {
      if (event.getStatus() == EventStatus.CANCELLED) {
        eventModificationType = EventModificationType.DELETED;
      }
      sendInvitations(event, attendees, eventModificationType);
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
  public EventAttendeeResponse getEventResponse(long eventId, long identityId) throws ObjectNotFoundException,
                                                                               IllegalAccessException {
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
    if (!isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User " + identityId + " is not attendee of event " + eventId);
    }
    EventAttendee attendee = attendeeStorage.getEventAttendee(eventId, identityId);
    if (attendee == null) {
      return EventAttendeeResponse.NEEDS_ACTION;
    }
    return attendee.getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }

    if (!isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " isn't attendee of event with id " + eventId);
    }

    saveEventAttendee(eventId, identityId, response);

    // Apply modification on exceptional occurrences as well
    if (eventStorage.isRecurrentEvent(eventId)) {
      List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId);
      for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
        if (isEventAttendee(exceptionalOccurenceEventId, identityId)) {
          saveEventAttendee(exceptionalOccurenceEventId, identityId, response);
        }
      }
    }
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
  public boolean isEventAttendee(long eventId, long identityId) {
    List<EventAttendee> eventAttendees = getEventAttendees(eventId);
    return Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendInvitations(Event event, List<EventAttendee> eventAttendees, EventModificationType eventModificationType) {
    String agendaNotificationPluginType = null;
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(EVENT_AGENDA, event);
    ctx.append(EVENT_ATTENDEE, eventAttendees);
    if (eventModificationType.name().equals("ADDED")) {
      agendaNotificationPluginType = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN;
      ctx.append(EVENT_MODIFICATION_TYPE, "ADDED");
    } else if (eventModificationType.name().equals("UPDATED")) {
      agendaNotificationPluginType = AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN;
      ctx.append(EVENT_MODIFICATION_TYPE, "UPDATED");
    } else {
      agendaNotificationPluginType = AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN;
      ctx.append(EVENT_MODIFICATION_TYPE, "DELETED");
    }
    dispatch(ctx, agendaNotificationPluginType);
  }

  private void saveEventAttendee(long eventId, long identityId, EventAttendeeResponse response) {
    EventAttendee attendee = attendeeStorage.getEventAttendee(eventId, identityId);
    EventAttendee oldAttendee = null;
    if (attendee == null) {
      attendee = new EventAttendee(0, eventId, identityId, response);
    } else {
      oldAttendee = attendee.clone();
      attendee.setResponse(response);
    }
    attendeeStorage.saveEventAttendee(attendee, eventId);

    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SENT, oldAttendee, attendee);
  }

  private void dispatch(NotificationContext ctx, String... pluginId) {
    List<NotificationCommand> commands = new ArrayList<>(pluginId.length);
    for (String p : pluginId) {
      commands.add(ctx.makeCommand(PluginKey.key(p)));
    }

    try {
      ctx.getNotificationExecutor().with(commands).execute(ctx);
    } catch (Exception e) {
      LOG.warn("Error sending invitation notifications", e);
    }
  }
}
