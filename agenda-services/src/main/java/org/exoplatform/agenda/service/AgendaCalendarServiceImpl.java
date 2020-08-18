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
package org.exoplatform.agenda.service;

import java.util.*;

import org.apache.commons.codec.binary.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaCalendarServiceImpl implements AgendaCalendarService {

  private AgendaCalendarStorage agendaCalendarStorage;

  private IdentityManager       identityManager;

  private SpaceService          spaceService;

  public AgendaCalendarServiceImpl(AgendaCalendarStorage agendaCalendarStorage,
                                   IdentityManager identityManager,
                                   SpaceService spaceService) {
    this.agendaCalendarStorage = agendaCalendarStorage;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Calendar> getCalendars(int offset, int limit, String username) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      return Collections.emptyList();
    }
    try {
      List<Long> identityIds = new ArrayList<>();
      identityIds.add(Long.parseLong(identity.getId()));
      addUserSpacesIdentities(username, identityIds);
      Long[] ownerIds = identityIds.toArray(new Long[0]);
      return this.agendaCalendarStorage.getCalendarsByOwners(offset, limit, ownerIds);
    } catch (Exception e) {
      throw new IllegalStateException("An unknown error occurred while accessing spaces list of user " + username);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countCalendars(String username) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      return 0;
    }
    try {
      List<Long> identityIds = new ArrayList<>();
      identityIds.add(Long.parseLong(identity.getId()));
      addUserSpacesIdentities(username, identityIds);
      Long[] ownerIds = identityIds.toArray(new Long[0]);
      return this.agendaCalendarStorage.countCalendarsByOwners(ownerIds);
    } catch (Exception e) {
      throw new IllegalStateException("An unknown error occurred while accessing spaces list of user " + username);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Calendar> getCalendarsByOwner(long ownerId,
                                            int offset,
                                            int limit,
                                            String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      return Collections.emptyList();
    }
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Owner with id " + ownerId + " wasn't found");
    }
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!StringUtils.equals(requestedOwner.getId(), String.valueOf(ownerId))) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user "
            + requestedOwner.getRemoteId());
      }
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!spaceService.isSuperManager(username)) {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        if (!spaceService.isMember(space, username)) {
          throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
              + space.getDisplayName());
        }
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId() + "' is not recognized");
    }
    return this.agendaCalendarStorage.getCalendarsByOwners(offset, limit, ownerId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countCalendarsByOwner(long ownerId, String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      return 0;
    }
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Owner with id " + ownerId + " wasn't found");
    }
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!StringUtils.equals(requestedOwner.getId(), String.valueOf(ownerId))) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user "
            + requestedOwner.getRemoteId());
      }
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!spaceService.isSuperManager(username)) {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        if (!spaceService.isMember(space, username)) {
          throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
              + space.getDisplayName());
        }
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId() + "' is not recognized");
    }
    return this.agendaCalendarStorage.countCalendarsByOwners(ownerId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar getCalendarById(long calendarId, String username) throws IllegalAccessException {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      return null;
    }
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    long ownerId = calendar.getOwnerId();
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Owner with id " + ownerId + " wasn't found");
    }
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!StringUtils.equals(requestedOwner.getId(), String.valueOf(ownerId))) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user "
            + requestedOwner.getRemoteId());
      }
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!spaceService.isSuperManager(username)) {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        if (!spaceService.isMember(space, username)) {
          throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
              + space.getDisplayName());
        }
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId() + "' is not recognized");
    }
    return calendar;
  }

  @Override
  public Calendar getCalendarById(long calendarId) {
    return agendaCalendarStorage.getCalendarById(calendarId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar createCalendar(Calendar calendar, String username) throws IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateCalendar(Calendar calendar, String username) throws IllegalAccessException, ObjectNotFoundException {
    // TODO Auto-generated method stub

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarById(long calendarId, String username) throws IllegalAccessException, ObjectNotFoundException {
    // TODO Auto-generated method stub

  }

  private void addUserSpacesIdentities(String username, List<Long> identityIds) throws Exception {
    ListAccess<Space> userSpaces = spaceService.getMemberSpaces(username);
    int spacesSize = userSpaces.getSize();
    int offsetToFetch = 0;
    int limitToFetch = spacesSize > 20 ? 20 : spacesSize;
    while (limitToFetch > 0) {
      Space[] spaces = userSpaces.load(offsetToFetch, limitToFetch);
      Arrays.stream(spaces).forEach(space -> {
        Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());
        identityIds.add(Long.parseLong(spaceIdentity.getId()));
      });
      offsetToFetch += limitToFetch;
      limitToFetch = (spacesSize - offsetToFetch) > 20 ? 20 : (spacesSize - offsetToFetch);
    }
  }

}
