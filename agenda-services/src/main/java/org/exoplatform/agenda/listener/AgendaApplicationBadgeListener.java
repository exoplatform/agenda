/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.agenda.listener;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.plugin.AgendaApplicationBadgePlugin;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.appcenter.plugin.ApplicationBadgePlugin;
import io.meeds.appcenter.service.ApplicationBadgeService;

import jakarta.annotation.PostConstruct;

/**
 * Refreshes the Agenda badge of every attendee concerned by an event change.
 * <p>
 * Pure glue: it holds no counting logic, it only tells App Center that a user's
 * count is stale. This is what makes the badge real-time — a user who answers
 * an invitation sees the counter drop without reloading the page.
 */
@Component
@Asynchronous
@ConditionalOnClass(ApplicationBadgePlugin.class)
public class AgendaApplicationBadgeListener extends Listener<Object, Object> {

  private static final Log           LOG         = ExoLogger.getLogger(AgendaApplicationBadgeListener.class);

  private static final List<String>  EVENT_NAMES = List.of(Utils.POST_CREATE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_UPDATE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_DELETE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_CREATE_AGENDA_EVENT_POLL,
                                                           // SAVED alone: saveEventAttendee broadcasts it on every
                                                           // response and adds SENT on top for a user's own answer,
                                                           // so listening to both would evict and push twice
                                                           Utils.POST_EVENT_RESPONSE_SAVED);

  /**
   * Optional for the same reason as the plugin it feeds: without the
   * Application Center there is no badge to refresh, and Agenda must still
   * start.
   */
  @Autowired(required = false)
  private ApplicationBadgeService    applicationBadgeService;

  @Autowired
  private AgendaEventAttendeeService attendeeService;

  @Autowired
  private IdentityManager            identityManager;

  @Autowired
  private SpaceService               spaceService;

  @Autowired
  private ListenerService            listenerService;

  @PostConstruct
  public void init() {
    if (applicationBadgeService == null) {
      LOG.debug("Application Center badge service not available, Agenda badge listener not registered");
      return;
    }
    EVENT_NAMES.forEach(eventName -> listenerService.addListener(eventName, this));
  }

  @Override
  public void onEvent(Event<Object, Object> event) throws Exception {
    getConcernedIdentityIds(event).stream()
                                  .flatMap(this::getUsernames)
                                  .distinct()
                                  .forEach(username -> applicationBadgeService.updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME,
                                                                                           username));
  }

  /**
   * A response event concerns its single attendee; an event change concerns
   * every attendee, since the invitation appeared or disappeared for all of
   * them at once.
   */
  private Set<Long> getConcernedIdentityIds(Event<Object, Object> event) {
    Object source = event.getSource();
    if (source instanceof EventAttendee attendee) {
      return Set.of(attendee.getIdentityId());
    } else if (event.getData() instanceof EventAttendee attendee) {
      return Set.of(attendee.getIdentityId());
    } else if (source instanceof AgendaEventModification modification) {
      // A deletion removes the attendee rows before broadcasting, so the event
      // carries the snapshot taken before it; looking them up would find nobody
      // and nobody's badge would ever be refreshed
      if (event.getData() instanceof EventAttendeeList attendees) {
        return toIdentityIds(attendees.getEventAttendees());
      }
      return getAttendeeIdentityIds(modification.getEventId());
    }
    return Set.of();
  }

  private Set<Long> getAttendeeIdentityIds(long eventId) {
    try {
      return toIdentityIds(attendeeService.getEventAttendees(eventId).getEventAttendees());
    } catch (Exception e) {
      LOG.warn("Error retrieving attendees of agenda event {} to refresh their badge", eventId, e);
      return Set.of();
    }
  }

  private Set<Long> toIdentityIds(List<EventAttendee> attendees) {
    return attendees == null ? Set.of()
                              : attendees.stream().map(EventAttendee::getIdentityId).collect(Collectors.toSet());
  }

  /**
   * Expands one attendee identity into the users whose badge it changes.
   * <p>
   * An attendee is <strong>not necessarily a user</strong>: an event can be
   * invited to a space, in which case the attendee is the space identity. And
   * because {@code Utils.getCalendarOwnersOfUser} counts a user's pending
   * invitations against their own identity <em>plus every space they are a
   * member of</em>, a space attendee makes the invitation pending for
   * <strong>all</strong> the members — so all of them must be refreshed.
   * <p>
   * Same resolution as {@code AgendaWebSocketService.sendMessage} already
   * performs for its own recipients.
   *
   * @param identityId the attendee identity, of a user or of a space
   * @return the usernames whose badge became stale, empty when the identity is
   *         unusable
   */
  private Stream<String> getUsernames(long identityId) {
    Identity identity = identityManager.getIdentity(identityId);
    if (identity == null || identity.isDeleted() || !identity.isEnable()) {
      return Stream.empty();
    }
    if (identity.isUser()) {
      return Stream.of(identity.getRemoteId());
    } else if (identity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      // The listener is asynchronous, so the space can be gone by the time it
      // runs. Throwing here would abort the flatMap and drop the refresh of
      // every remaining attendee of the event, not just this one.
      if (space == null || ArrayUtils.isEmpty(space.getMembers())) {
        LOG.debug("No member found for space attendee {}, no badge to refresh", identity.getRemoteId());
        return Stream.empty();
      }
      return Arrays.stream(space.getMembers());
    } else {
      return Stream.empty();
    }
  }

}
