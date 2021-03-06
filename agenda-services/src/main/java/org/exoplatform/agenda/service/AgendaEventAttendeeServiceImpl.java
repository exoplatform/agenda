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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.*;
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

  private static final String        SEPARATOR = "@@@";

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
  public Set<AgendaEventModificationType> saveEventAttendees(Event event,
                                                             List<EventAttendee> attendees,
                                                             long creatorUserId,
                                                             boolean sendInvitations,
                                                             boolean resetResponses,
                                                             AgendaEventModification eventModifications) {
    long eventId = event.getId();
    EventStatus eventStatus = event.getStatus();

    List<EventAttendee> oldAttendees = getEventAttendees(event.getId()).getEventAttendees();
    List<EventAttendee> newAttendees = attendees == null ? Collections.emptyList() : attendees;

    Set<AgendaEventModificationType> eventModificationTypes = new HashSet<>();

    processAttendeesToDelete(oldAttendees, newAttendees, eventModificationTypes);
    processAttendeesToCreate(eventId, eventStatus, oldAttendees, newAttendees, resetResponses, eventModificationTypes);
    processAttendeesToUpdate(eventId, oldAttendees, newAttendees, resetResponses);
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
    if (!isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User " + identityId + " is not attendee of event " + eventId);
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
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
    if (event.getRecurrence() == null) {
      throw new IllegalStateException("Event with id " + eventId + " isn't recurrent");
    }
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }

    if (!isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " isn't attendee of event with id " + eventId);
    }

    saveEventAttendee(eventId, occurrenceId, identityId, response, true);

    // Apply modification on exceptional occurrences as well
    List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId, occurrenceId);
    for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
      if (isEventAttendee(exceptionalOccurenceEventId, identityId)) {
        saveEventAttendee(exceptionalOccurenceEventId, identityId, response, false);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response,
                                boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
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

    boolean isRecurrentEvent = eventStorage.isRecurrentEvent(eventId);
    if (isRecurrentEvent) {

    }

    saveEventAttendee(eventId, identityId, response, broadcast);

    // Apply modification on exceptional occurrences as well
    if (isRecurrentEvent) {
      List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId);
      for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
        if (isEventAttendee(exceptionalOccurenceEventId, identityId)) {
          saveEventAttendee(exceptionalOccurenceEventId, identityId, response, false);
        }
      }
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
    StringBuilder tokenFlatStringBuilder = new StringBuilder().append(String.valueOf(eventId))
                                                              .append(SEPARATOR)
                                                              .append(emailOrUsername);
    if (response != null) {
      tokenFlatStringBuilder.append(SEPARATOR).append(response.getValue());
    }
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
    String[] tokenParts = tokenFlat.split("\\" + SEPARATOR);
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
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, emailOrUsername);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEventAttendee(long eventId, long identityId) {
    EventAttendeeList eventAttendees = getEventAttendees(eventId);
    return Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees);
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

  private void saveEventAttendee(long eventId,
                                 long identityId,
                                 EventAttendeeResponse response,
                                 boolean userResponseSent) {
    saveEventAttendee(eventId, null, identityId, response, userResponseSent);
  }

  private void saveEventAttendee(long eventId,
                                 ZonedDateTime occurrenceId,
                                 long identityId,
                                 EventAttendeeResponse response,
                                 boolean userResponseSent) {
    EventAttendeeList eventAttendees = attendeeStorage.getEventAttendees(eventId, identityId);
    EventAttendee attendee = eventAttendees.getEventAttendee(identityId, occurrenceId);
    EventAttendee oldAttendee = attendee == null ? null : attendee.clone();
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
      attendee = new EventAttendee(identityId, eventId, identityId, occurrenceId, null, response);
      attendeeStorage.saveEventAttendee(attendee, eventId);
    }

    // Broadcast technical event when saving any new response for a user
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);

    // Broadcast user response to an event
    if (userResponseSent) {
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SENT, oldAttendee, attendee);
    }
  }

  private void dispatch(NotificationContext ctx, String... pluginId) {
    List<NotificationCommand> commands = new ArrayList<>(pluginId.length);
    for (String p : pluginId) {
      NotificationCommand command = ctx.makeCommand(PluginKey.key(p));
      if (command != null) {
        commands.add(command);
      }
    }

    try {
      if (!commands.isEmpty()) {
        ctx.getNotificationExecutor().with(commands).execute(ctx);
      }
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

  private void processAttendeesToUpdate(long eventId,
                                        List<EventAttendee> oldAttendees,
                                        List<EventAttendee> newAttendees,
                                        boolean resetResponses) {
    if (resetResponses) {
      List<EventAttendee> attendeesToUpdate =
                                            oldAttendees.stream()
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
  }

  private void processAttendeesToCreate(long eventId,
                                        EventStatus eventStatus,
                                        List<EventAttendee> oldAttendees,
                                        List<EventAttendee> newAttendees,
                                        boolean resetResponses,
                                        Set<AgendaEventModificationType> eventModificationTypes) {
    List<EventAttendee> attendeesToCreate =
                                          newAttendees.stream()
                                                      .filter(attendee -> oldAttendees.stream()
                                                                                      .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    // Create new attendees
    for (EventAttendee eventAttendee : attendeesToCreate) {
      if (resetResponses || eventAttendee.getResponse() == null || eventStatus == EventStatus.TENTATIVE) {
        eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      }
      attendeeStorage.saveEventAttendee(eventAttendee, eventId);
    }
    if (!attendeesToCreate.isEmpty()) {
      eventModificationTypes.add(AgendaEventModificationType.ATTENDEE_ADDED);
    }
  }

  private void processAttendeesToDelete(List<EventAttendee> oldAttendees,
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
  }
}
