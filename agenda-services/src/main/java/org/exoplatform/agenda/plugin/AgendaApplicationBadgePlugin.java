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
package org.exoplatform.agenda.plugin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.appcenter.plugin.ApplicationBadgePlugin;

/**
 * Reports how many invitations the user still has to answer, on the Agenda
 * application tile.
 * <p>
 * Carries no counting logic of its own: it reuses
 * {@link AgendaEventService#countPendingEvents(java.util.List, long)}, the very
 * same query behind the pending-invitation reminder already shown inside the
 * Agenda application, so the badge and that reminder can never disagree.
 */
@Component
public class AgendaApplicationBadgePlugin implements ApplicationBadgePlugin {

  private static final Log   LOG        = ExoLogger.getLogger(AgendaApplicationBadgePlugin.class);

  public static final String BADGE_NAME = "agendaPendingInvitations";

  @Autowired
  private AgendaEventService agendaEventService;

  @Autowired
  private IdentityManager    identityManager;

  /**
   * The url of the Application Center catalog entry pointing at Agenda. Made
   * configurable so a deployment that renamed the entry can rebind it without
   * an administrator having to set the binding by hand.
   */
  @Value("${agenda.badge.portletName:Agenda}")
  private String             portletName;

  @Override
  public String getName() {
    return BADGE_NAME;
  }

  @Override
  public String getPortletName() {
    return portletName;
  }

  @Override
  public long countBadge(String username) {
    long userIdentityId = getUserIdentityId(username);
    if (userIdentityId == 0) {
      return 0;
    }
    try {
      // A null owner list means "every calendar this user attends"
      return agendaEventService.countPendingEvents(null, userIdentityId);
    } catch (Exception e) {
      LOG.warn("Error counting pending agenda invitations of user {}", username, e);
      return 0;
    }
  }

  @Override
  public boolean isEnabled(String username) {
    return getUserIdentityId(username) > 0;
  }

  private long getUserIdentityId(String username) {
    if (StringUtils.isBlank(username)) {
      return 0;
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

}
