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

import java.time.ZoneOffset;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.service.AgendaEventDatePollService;

import io.meeds.mcp.server.plugin.SpaceScopeResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells which space a proposed date of a date poll belongs to, for the space
 * scope of the tools that reach a date poll through one of its dates: the
 * space of the poll's event, by the same rule as
 * {@link AgendaEventSpaceScopeResolver}. A missing date, or one whose event
 * belongs to no space, belongs to no space. Read as the system, for the space
 * scope decision only.
 */
@Service("agendaEventDateOptionSpaceScopeResolver")
@Profile("mcp-server")
@Slf4j
public class AgendaEventDateOptionSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the agenda tools' {@code space_scope.resolver} gives. */
  public static final String                  NAME = "event_date_option";

  /** Reads the date option and its event id. */
  private final AgendaEventDatePollService    agendaEventDatePollService;

  /** Tells the space of the date option's event. */
  private final AgendaEventSpaceScopeResolver eventSpaceScopeResolver;

  /**
   * @param agendaEventDatePollService reads the date option
   * @param eventSpaceScopeResolver tells the space of its event
   */
  @Autowired
  public AgendaEventDateOptionSpaceScopeResolver(AgendaEventDatePollService agendaEventDatePollService,
                                                 AgendaEventSpaceScopeResolver eventSpaceScopeResolver) {
    this.agendaEventDatePollService = agendaEventDatePollService;
    this.eventSpaceScopeResolver = eventSpaceScopeResolver;
  }

  /**
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * @param objectId the date option id
   * @return the id of the space of the date option's event, or null for a
   *         missing date option, an event of no space, an id that isn't a
   *         positive number, or a lookup that fails
   */
  @Override
  public Long spaceOf(String objectId) {
    if (!StringUtils.isNumeric(objectId)) {
      return null;
    }
    try {
      long dateOptionId = Long.parseLong(objectId);
      if (dateOptionId <= 0) {
        return null;
      }
      EventDateOption dateOption = agendaEventDatePollService.getEventDateOption(dateOptionId, ZoneOffset.UTC);
      return dateOption == null ? null : eventSpaceScopeResolver.spaceOf(String.valueOf(dateOption.getEventId()));
    } catch (RuntimeException e) {
      log.debug("The space of agenda date option '{}' couldn't be read", objectId, e);
      return null;
    }
  }

}
