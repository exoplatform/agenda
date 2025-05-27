/**
 * Copyright (C) 2025 eXo Platform SAS.
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
package org.exoplatform.agenda.plugin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.portal.plugin.AclPlugin;

import jakarta.annotation.PostConstruct;

@Component
public class AgendaEventAclPlugin implements AclPlugin {

  public static final String OBJECT_TYPE = "agendaEvent";

  @Autowired
  private AgendaEventService agendaEventService;

  @Autowired
  private PortalContainer    container;

  @Autowired
  private IdentityManager    identityManager;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(UserACL.class).addAclPlugin(this);
  }

  @Override
  public String getObjectType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean hasPermission(String objectId,
                               String permissionType,
                               Identity identity) {
    Event event = agendaEventService.getEventById(Long.parseLong(objectId));
    if (event == null || identity == null) {
      return false;
    }
    return switch (permissionType) {
    case VIEW_PERMISSION_TYPE: {
      yield agendaEventService.canAccessEvent(event, getUserIdentityId(identity.getUserId()));
    }
    case EDIT_PERMISSION_TYPE, DELETE_PERMISSION_TYPE: {
      yield agendaEventService.canUpdateEvent(event, getUserIdentityId(identity.getUserId()));
    }
    default:
      yield false;
    };
  }

  private long getUserIdentityId(String username) {
    return Long.parseLong(identityManager.getOrCreateUserIdentity(username)
                                         .getId());
  }

}
