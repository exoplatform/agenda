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
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Permission;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaCalendarServiceImpl implements AgendaCalendarService {

  private static final Log      LOG = ExoLogger.getLogger(AgendaCalendarServiceImpl.class);

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
  public List<Calendar> getCalendars(int offset, int limit, String username) throws Exception {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    List<Long> identityIds = new ArrayList<>();
    identityIds.add(Long.parseLong(identity.getId()));
    addUserSpacesIdentities(username, identityIds);
    Long[] ownerIds = identityIds.toArray(new Long[0]);
    List<Long> calendarsIds = this.agendaCalendarStorage.getCalendarIdsByOwnerIds(offset, limit, ownerIds);
    return calendarsIds.stream().map(calendarId -> {
      try {
        return getCalendarById(calendarId, username);
      } catch (IllegalAccessException e) {
        LOG.error("Impossible use case happened, this must be a bug. The user should be able to access calendar with id : "
            + calendarId, e);
        return null;
      }
    }).filter(calendar -> calendar != null).collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countCalendars(String username) throws Exception {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    List<Long> identityIds = new ArrayList<>();
    identityIds.add(Long.parseLong(identity.getId()));
    addUserSpacesIdentities(username, identityIds);
    Long[] ownerIds = identityIds.toArray(new Long[0]);
    return this.agendaCalendarStorage.countCalendarsByOwners(ownerIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Calendar> getCalendarsByOwnerId(long ownerId,
                                              int offset,
                                              int limit,
                                              String username) throws IllegalAccessException {
    if (ownerId <= 0) {
      throw new IllegalArgumentException("Owner id is mandatory");
    }
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    checkAclByCalendarOwner(ownerId, username, true);
    List<Long> calendarsIds = this.agendaCalendarStorage.getCalendarIdsByOwnerIds(offset, limit, ownerId);
    return calendarsIds.stream().map(calendarId -> {
      try {
        return getCalendarById(calendarId, username);
      } catch (IllegalAccessException e) {
        LOG.error("Impossible use case happened, this must be a bug. The user should be able to access calendar with id : "
            + calendarId, e);
        return null;
      }
    }).filter(calendar -> calendar != null).collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countCalendarsByOwnerId(long ownerId, String username) throws IllegalAccessException {
    if (ownerId <= 0) {
      throw new IllegalArgumentException("Owner id is mandatory");
    }
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    checkAclByCalendarOwner(ownerId, username, true);
    return this.agendaCalendarStorage.countCalendarsByOwners(ownerId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar getCalendarById(long calendarId, String username) throws IllegalAccessException {
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (calendar == null) {
      return null;
    }
    boolean canEditCalendar = checkAclByCalendarOwner(calendar.getOwnerId(), username, true);
    calendar.setAcl(new Permission(canEditCalendar));
    return calendar;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar getCalendarById(long calendarId) {
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }
    return agendaCalendarStorage.getCalendarById(calendarId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar createCalendar(Calendar calendar, String username) throws IllegalAccessException {
    if (calendar == null) {
      throw new IllegalArgumentException("Calendar is mandatory");
    }
    if (calendar.getId() != 0) {
      throw new IllegalArgumentException("Calendar id must be equal to 0");
    }
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    long ownerId = calendar.getOwnerId();
    if (ownerId <= 0) {
      // Automatically set owner of calendar, the currently authenticated user
      // if no owner has been specified
      Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
      if (userIdentity == null) {
        throw new IllegalStateException("User with name " + username + " is not found");
      }
      calendar.setOwnerId(Long.parseLong(userIdentity.getId()));
    } else {
      checkAclByCalendarOwner(calendar.getOwnerId(), username, false);
    }

    // User had created the calendar manually
    calendar.setSystem(false);
    calendar = agendaCalendarStorage.createCalendar(calendar);
    return getCalendarById(calendar.getId(), username);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar createCalendar(Calendar calendar) {
    if (calendar == null) {
      throw new IllegalArgumentException("Calendar is mandatory");
    }
    if (calendar.getId() != 0) {
      throw new IllegalArgumentException("Calendar id must be equal to 0");
    }
    if (calendar.getOwnerId() <= 0) {
      throw new IllegalArgumentException("Calendar owner is missing");
    }
    Identity calendarOwnerIdentity = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    if (calendarOwnerIdentity == null) {
      throw new IllegalStateException("Calendar owner is not found");
    }
    if (!StringUtils.equals(OrganizationIdentityProvider.NAME, calendarOwnerIdentity.getProviderId())
        && !StringUtils.equals(SpaceIdentityProvider.NAME, calendarOwnerIdentity.getProviderId())) {
      throw new IllegalStateException("Calendar owner providerId '" + calendarOwnerIdentity.getProviderId()
          + "' is not managed by Calendar API");
    }

    // System had created the calendar manually
    calendar.setSystem(true);
    return agendaCalendarStorage.createCalendar(calendar);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateCalendar(Calendar calendar, String username) throws IllegalAccessException, ObjectNotFoundException {
    if (calendar == null) {
      throw new IllegalArgumentException("Calendar is mandatory");
    }
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    if (calendar.getId() <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }

    // Refill readonly fields from Database to avoid letting users modifying
    // data using UI or REST calls
    refillReadOnlyFields(calendar);
    checkAclByCalendarOwner(calendar.getOwnerId(), username, false);
    agendaCalendarStorage.updateCalendar(calendar);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateCalendar(Calendar calendar) throws ObjectNotFoundException {
    if (calendar == null) {
      throw new IllegalArgumentException("Calendar is mandatory");
    }
    if (calendar.getId() <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }
    refillReadOnlyFields(calendar);
    agendaCalendarStorage.updateCalendar(calendar);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarById(long calendarId, String username) throws IllegalAccessException, ObjectNotFoundException {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (calendar == null) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " wasn't found");
    }
    if (calendar.isSystem()) {
      throw new IllegalStateException("Calendar with id " + calendarId + " is a system calendar, thus it couldn't be deleted");
    }
    checkAclByCalendarOwner(calendar.getOwnerId(), username, false);
    deleteCalendarById(calendarId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarById(long calendarId) throws ObjectNotFoundException {
    if (calendarId <= 0) {
      throw new IllegalArgumentException("Calendar id has to be positive integer");
    }
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (calendar == null) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " doesn't exists");
    }

    agendaCalendarStorage.deleteCalendarById(calendarId);
  }

  /**
   * @param calendarOwnerId calendar owner {@link Identity} technical identifier
   * @param username name of user accessing calendar data
   * @param readonly whether the access is to read or to write
   * @return true if user can modify calendar, else return false
   * @throws IllegalAccessException when the user ACL fails
   */
  private boolean checkAclByCalendarOwner(long calendarOwnerId, String username, boolean readonly) throws IllegalAccessException {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(calendarOwnerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Calendar owner with id " + calendarOwnerId + " wasn't found");
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!StringUtils.equals(requestedOwner.getRemoteId(), username)) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user "
            + requestedOwner.getRemoteId());
      }
      return true;
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (spaceService.isSuperManager(username)) {
        return true;
      } else {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        if (!spaceService.isMember(space, username)) {
          throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
              + requestedOwner.getRemoteId());
        }
        boolean isManager = spaceService.isManager(space, username);
        if (!readonly && !isManager) {
          throw new IllegalAccessException("User " + username + " is not allowed to write calendar data of space "
              + space.getDisplayName());
        }
        return isManager;
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId()
          + "' is not managed in calendar owner field");
    }
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

  private void refillReadOnlyFields(Calendar calendar) throws ObjectNotFoundException {
    // Refill readonly fields from Database
    long calendarId = calendar.getId();
    Calendar storedCalendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (storedCalendar == null) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " wasn't found");
    }
    calendar.setCreated(storedCalendar.getCreated());
    calendar.setOwnerId(storedCalendar.getOwnerId());
  }

}
