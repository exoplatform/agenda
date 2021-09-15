/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
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

import org.apache.commons.lang.StringEscapeUtils;

import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class IdentityEntityBuilder {

  private static final Log           LOG             =
                                         ExoLogger.getLogger(IdentityEntityBuilder.class);

  /** Group Space Binding */
  private static final String        USERS_TYPE      = "users";

  private static final String        IDENTITIES_TYPE = "identities";

  private static SpaceService        spaceService;

  private static OrganizationService organizationService;

  private IdentityEntityBuilder() {
    // Static class for utilities, thus a private constructor is declared
  }

  /**
   * Get a IdentityEntity from an identity in order to build a json object for
   * the rest service
   * 
   * @param identity the provided identity
   * @param restPath base REST path
   * @return a hash map
   */
  public static IdentityEntity buildEntityIdentity(Identity identity, String restPath) {
    IdentityEntity identityEntity = new IdentityEntity(identity.getId());
    identityEntity.setProviderId(identity.getProviderId());
    identityEntity.setGlobalId(identity.getGlobalId());
    identityEntity.setRemoteId(identity.getRemoteId());
    identityEntity.setDeleted(identity.isDeleted());
    if (identity.isUser()) {
      identityEntity.setProfile(buildEntityProfile(identity.getProfile(), restPath));
    } else if (identity.isSpace()) {
      Space space = getSpaceService().getSpaceByPrettyName(identity.getRemoteId());
      identityEntity.setSpace(buildEntityFromSpace(space));
    }
    return identityEntity;
  }

  private static ProfileEntity buildEntityProfile(Profile profile, String restPath) {
    ProfileEntity userEntity = new ProfileEntity(profile.getId());
    userEntity.setHref(RestUtils.getRestUrl(USERS_TYPE, profile.getIdentity().getRemoteId(), restPath));
    userEntity.setIdentity(RestUtils.getRestUrl(IDENTITIES_TYPE, profile.getIdentity().getId(), restPath));
    userEntity.setUsername(profile.getIdentity().getRemoteId());
    userEntity.setFirstname((String) profile.getProperty(Profile.FIRST_NAME));
    userEntity.setLastname((String) profile.getProperty(Profile.LAST_NAME));
    userEntity.setFullname(profile.getFullName());
    userEntity.setGender(profile.getGender());
    userEntity.setPosition(profile.getPosition());
    userEntity.setEmail(profile.getEmail());
    userEntity.setAboutMe((String) profile.getProperty(Profile.ABOUT_ME));
    userEntity.setAvatar(profile.getAvatarUrl());
    userEntity.setBanner(profile.getBannerUrl());
    if (profile.getProperty(Profile.ENROLLMENT_DATE) != null) {
      userEntity.setEnrollmentDate(profile.getProperty(Profile.ENROLLMENT_DATE).toString());
    }
    if (profile.getProperty(Profile.SYNCHRONIZED_DATE) != null) {
      userEntity.setSynchronizedDate((String) profile.getProperty(Profile.SYNCHRONIZED_DATE));
    }
    try {
      OrganizationService organizationService = getOrganizationService();
      User user = organizationService.getUserHandler().findUserByName(userEntity.getUsername(), UserStatus.ANY);
      if (user != null) {
        userEntity.setIsInternal(user.isInternalStore());
        if (user.getCreatedDate() != null) {
          userEntity.setCreatedDate(String.valueOf(user.getCreatedDate().getTime()));
        }
        if (user.getLastLoginTime() != null && !user.getCreatedDate().equals(user.getLastLoginTime())) {
          userEntity.setLastLoginTime(String.valueOf(user.getLastLoginTime().getTime()));
        }
      }
    } catch (Exception e) {
      LOG.warn("Error when searching user {}", userEntity.getUsername(), e);
    }
    userEntity.setDeleted(profile.getIdentity().isDeleted());
    userEntity.setEnabled(profile.getIdentity().isEnable());
    if (profile.getProperty(Profile.EXTERNAL) != null) {
      userEntity.setIsExternal((String) profile.getProperty(Profile.EXTERNAL));
    }
    userEntity.setCompany((String) profile.getProperty(Profile.COMPANY));
    userEntity.setLocation((String) profile.getProperty(Profile.LOCATION));
    userEntity.setDepartment((String) profile.getProperty(Profile.DEPARTMENT));
    userEntity.setTeam((String) profile.getProperty(Profile.TEAM));
    userEntity.setProfession((String) profile.getProperty(Profile.PROFESSION));
    userEntity.setCountry((String) profile.getProperty(Profile.COUNTRY));
    userEntity.setCity((String) profile.getProperty(Profile.CITY));
    return userEntity;
  }

  /**
   * Get a hash map from a space in order to build a json object for the rest
   * service
   * 
   * @param space the provided space
   * @return a hash map
   */
  private static SpaceEntity buildEntityFromSpace(Space space) {
    SpaceEntity spaceEntity = new SpaceEntity(space.getId());
    spaceEntity.setDisplayName(space.getDisplayName());
    spaceEntity.setLastUpdatedTime(space.getLastUpdatedTime());
    spaceEntity.setCreatedTime(String.valueOf(space.getCreatedTime()));
    spaceEntity.setTemplate(space.getTemplate());
    spaceEntity.setPrettyName(space.getPrettyName());
    spaceEntity.setGroupId(space.getGroupId());
    spaceEntity.setDescription(StringEscapeUtils.unescapeHtml(space.getDescription()));
    spaceEntity.setAvatarUrl(space.getAvatarUrl());
    spaceEntity.setBannerUrl(space.getBannerUrl());
    spaceEntity.setVisibility(space.getVisibility());
    spaceEntity.setSubscription(space.getRegistration());
    spaceEntity.setMembersCount(space.getMembers() == null ? 0 : space.getMembers().length);
    return spaceEntity;
  }

  private static SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = ExoContainerContext.getService(SpaceService.class);
    }
    return spaceService;
  }

  private static OrganizationService getOrganizationService() {
    if (organizationService == null) {
      organizationService = ExoContainerContext.getService(OrganizationService.class);
    }
    return organizationService;
  }

}
