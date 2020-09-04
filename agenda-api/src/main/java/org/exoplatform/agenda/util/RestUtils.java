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
package org.exoplatform.agenda.util;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

public class RestUtils {

  public static final int DEFAULT_LIMIT = 10;

  private RestUtils() {
  }

  public static final String getCurrentUser() {
    return ConversationState.getCurrent().getIdentity().getUserId();
  }

  public static final Identity getCurrentUserIdentity(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
  }

  public static final long getCurrentUserIdentityId(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  public static String getBaseRestURI() {
    return getBasePortalURI() + "/" + PortalContainer.getCurrentRestContextName();
  }

  public static String getBasePortalURI() {
    return "/" + PortalContainer.getCurrentPortalContainerName();
  }

  public static String getIdentityId(IdentityEntity identityEntity, IdentityManager identityManager) {
    if (identityEntity == null) {
      return null;
    }
    String identityIdString = identityEntity.getId();
    String remoteId = identityEntity.getRemoteId();
    String providerId = identityEntity.getProviderId();

    if (StringUtils.isNotBlank(identityIdString)) {
      Identity identity = identityManager.getIdentity(identityIdString);
      if (identity == null) {
        // Wrong id, attempt with remoteId and providerId
        identityIdString = null;
      }
    }
    if (StringUtils.isBlank(identityIdString) && StringUtils.isNotBlank(remoteId) && StringUtils.isNotBlank(providerId)) {
      Identity identity = identityManager.getOrCreateIdentity(providerId, remoteId);
      if (identity != null) {
        identityIdString = identity.getId();
      }
    }
    return identityIdString;
  }

}
