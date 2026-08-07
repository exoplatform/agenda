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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.EventAttendee;
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

import io.meeds.appcenter.service.ApplicationBadgeService;

import jakarta.annotation.PostConstruct;

/**
 * Refreshes the Agenda badge of every attendee concerned by an event change.
 * <p>
 * Pure glue: it holds no counting logic, it only tells App Center that a
 * user's count is stale. This is what makes the badge real-time — a user who
 * answers an invitation sees the counter drop without reloading the page.
 */
@Component
@Asynchronous
public class AgendaApplicationBadgeListener extends Listener<Object, Object> {

  private static final Log           LOG         = ExoLogger.getLogger(AgendaApplicationBadgeListener.class);

  private static final List<String>  EVENT_NAMES = List.of(Utils.POST_CREATE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_UPDATE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_DELETE_AGENDA_EVENT_EVENT,
                                                           Utils.POST_CREATE_AGENDA_EVENT_POLL,
                                                           Utils.POST_EVENT_RESPONSE_SENT,
                                                           Utils.POST_EVENT_RESPONSE_SAVED);

  @Autowired
  private ApplicationBadgeService    applicationBadgeService;

  @Autowired
  private AgendaEventAttendeeService attendeeService;

  @Autowired
  private IdentityManager            identityManager;

  @Autowired
  private ListenerService            listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(eventName -> listenerService.addListener(eventName, this));
  }

  @Override
  public void onEvent(Event<Object, Object> event) throws Exception {
    getConcernedIdentityIds(event).stream()
                                  .map(this::getUsername)
                                  .filter(java.util.Objects::nonNull)
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
      return getAttendeeIdentityIds(modification.getEventId());
    }
    return Set.of();
  }

  private Set<Long> getAttendeeIdentityIds(long eventId) {
    try {
      return attendeeService.getEventAttendees(eventId)
                            .getEventAttendees()
                            .stream()
                            .map(EventAttendee::getIdentityId)
                            .collect(Collectors.toSet());
    } catch (Exception e) {
      LOG.warn("Error retrieving attendees of agenda event {} to refresh their badge", eventId, e);
      return Set.of();
    }
  }

  private String getUsername(long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity == null || identity.isDeleted() || !identity.isEnable() ? null : identity.getRemoteId();
  }

}
