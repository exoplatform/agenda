/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.rest.util;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.rest.model.AttendeeUserEntity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;

public class AttendeeUserEntityBuilder {

  private static final String AVATAR_PATH = "/portal/rest/v1/social/users/%s/avatar";

  /**
   * Not instantiable: a builder of entities holds no state.
   */
  private AttendeeUserEntityBuilder() {
  }

  /**
   * @param identity the identity of a platform user
   * @return the user as the participants drawer adds them: their name, the
   *         username when the profile has none, and their avatar, the
   *         platform's avatar of the username when the profile has none
   */
  public static AttendeeUserEntity toEntity(Identity identity) {
    Profile profile = identity.getProfile();
    AttendeeUserEntity entity = new AttendeeUserEntity();
    entity.setId(identity.getId());
    entity.setProviderId(identity.getProviderId());
    entity.setRemoteId(identity.getRemoteId());
    String fullName = profile == null ? null : profile.getFullName();
    entity.setFullName(StringUtils.isBlank(fullName) ? identity.getRemoteId() : fullName);
    String avatarUrl = profile == null ? null : profile.getAvatarUrl();
    entity.setAvatarUrl(StringUtils.isBlank(avatarUrl) ? String.format(AVATAR_PATH, identity.getRemoteId()) : avatarUrl);
    return entity;
  }

}
