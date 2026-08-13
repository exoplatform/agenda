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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.appcenter.plugin.ApplicationBadgePlugin;
import io.meeds.appcenter.service.ApplicationBadgePluginRegistry;

import jakarta.annotation.PostConstruct;

/**
 * Reports how many invitations the user still has to answer, on the Agenda
 * application tile.
 * <p>
 * Carries no counting logic of its own: it sums
 * {@link AgendaEventService#countPendingEvents(java.util.List, long)} and
 * {@link AgendaEventService#countEventDatePolls(java.util.List, long)}, exactly
 * as {@code AgendaPendingInvitationBadge.vue} does for the reminder already
 * shown inside the Agenda application — so the badge and that reminder can
 * never disagree. Counting only pending events would silently drop date polls,
 * which the functional specification requires ("event or datepoll").
 */
@Component
@ConditionalOnClass(ApplicationBadgePlugin.class)
public class AgendaApplicationBadgePlugin implements ApplicationBadgePlugin {

  private static final Log   LOG        = ExoLogger.getLogger(AgendaApplicationBadgePlugin.class);

  public static final String BADGE_NAME = "agendaPendingInvitations";

  /**
   * Optional on purpose: the badge is a nicety, not something Agenda depends
   * on. When the Application Center registry is absent — a deployment without
   * the addon, or this module's own Spring test context — the plugin simply
   * does not register instead of failing the whole context.
   */
  @Autowired(required = false)
  private ApplicationBadgePluginRegistry applicationBadgePluginRegistry;

  @Autowired
  private AgendaEventService             agendaEventService;

  @Autowired
  private IdentityManager    identityManager;

  /**
   * The urls of the Application Center catalog entries pointing at Agenda. Both
   * the calendar and its timeline report the same pending invitations, so both
   * carry the badge. Comma-separated and configurable, so a deployment that
   * renamed or added an entry can rebind it without an administrator having to
   * set the binding by hand.
   */
  @Value("${agenda.badge.portletNames:agenda/Agenda,agenda/AgendaTimeline}")
  private List<String>       portletNames;

  @PostConstruct
  public void init() {
    if (applicationBadgePluginRegistry == null) {
      LOG.debug("Application Center badge registry not available, Agenda badge not registered");
      return;
    }
    applicationBadgePluginRegistry.addPlugin(this);
  }

  @Override
  public String getName() {
    return BADGE_NAME;
  }

  @Override
  public List<String> getPortletNames() {
    return portletNames;
  }

  @Override
  public long countBadge(String username) {
    long userIdentityId = getUserIdentityId(username);
    if (userIdentityId == 0) {
      return 0;
    }
    try {
      // A null owner list means "every calendar this user attends".
      // Pending events and date polls are two distinct queries, and the badge
      // reports their sum — same as the in-app reminder
      return agendaEventService.countPendingEvents(null, userIdentityId)
             + agendaEventService.countEventDatePolls(null, userIdentityId);
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
