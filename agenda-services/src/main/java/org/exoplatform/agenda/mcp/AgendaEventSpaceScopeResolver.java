/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.mcp;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.mcp.server.plugin.SpaceScopeResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells which space an agenda event belongs to, for the space scope of the
 * agenda tools that reach an event by its id: the space that owns the
 * calendar the event is in. The rule is one for every scoped tool: an event
 * belongs to the space that owns its calendar, and a space that is only
 * invited does not own it, so the scoped listing and the scoped search keep
 * the same events as this resolver. An event of a personal calendar belongs to no
 * space, and so does one of a calendar whose owner is gone, and a missing
 * event. The event's attendees play no part: an event of a space calendar
 * that invites people from outside is still that space's, and an event of a
 * personal calendar that invites a space is still no space's. Read as the
 * system, for the space scope decision only.
 */
@Service("agendaEventSpaceScopeResolver")
@Profile("mcp-server")
@Slf4j
public class AgendaEventSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the agenda tools' {@code space_scope.resolver} gives. */
  public static final String NAME = "event";

  /** Reads the event and its calendar. */
  private final AgendaEventService agendaEventService;

  /** Reads the calendar's owner. */
  private final AgendaCalendarService agendaCalendarService;

  /** Reads the owner identity of the calendar. */
  private final IdentityManager identityManager;

  /** Reads the space behind the owner identity. */
  private final SpaceService spaceService;

  /**
   * @param agendaEventService    reads the event
   * @param agendaCalendarService reads the calendar of the event
   * @param identityManager       reads the owner of the calendar
   * @param spaceService          reads the space the owner stands for
   */
  @Autowired
  public AgendaEventSpaceScopeResolver(AgendaEventService agendaEventService,
                                       AgendaCalendarService agendaCalendarService,
                                       IdentityManager identityManager,
                                       SpaceService spaceService) {
    this.agendaEventService = agendaEventService;
    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  /**
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * @param objectId the event id
   * @return the id of the space that owns the event's calendar, or null for
   *         an event of a personal calendar, a missing event, calendar,
   *         owner or space, an id that isn't one, or a lookup that fails
   */
  @Override
  public Long spaceOf(String objectId) {
    if (!StringUtils.isNumeric(objectId)) {
      return null;
    }
    try {
      Event event = agendaEventService.getEventById(Long.parseLong(objectId));
      Calendar calendar = event == null ? null : agendaCalendarService.getCalendarById(event.getCalendarId());
      Identity owner = calendar == null ? null : identityManager.getIdentity(calendar.getOwnerId());
      if (owner == null || owner.isDeleted() || !owner.isSpace()) {
        return null;
      }
      Space space = spaceService.getSpaceByPrettyName(owner.getRemoteId());
      return space != null && StringUtils.isNumeric(space.getId()) ? Long.valueOf(space.getId()) : null;
    } catch (RuntimeException e) {
      log.debug("The space of agenda event '{}' couldn't be read", objectId, e);
      return null;
    }
  }

}
