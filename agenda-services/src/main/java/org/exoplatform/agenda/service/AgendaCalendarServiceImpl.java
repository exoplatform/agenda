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

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.xml.InitParams;
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

  private List<String>          defaultColors;

  public AgendaCalendarServiceImpl(AgendaCalendarStorage agendaCalendarStorage,
                                   IdentityManager identityManager,
                                   SpaceService spaceService,
                                   InitParams initParams) {
    this.agendaCalendarStorage = agendaCalendarStorage;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.defaultColors = initParams.getValuesParam("defaultColors").getValues();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Calendar> getCalendars(int offset, int limit, String username) throws Exception {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    if (identity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    List<Long> identityIds = new ArrayList<>();
    identityIds.add(Long.parseLong(identity.getId()));
    Utils.addUserSpacesIdentities(spaceService, identityManager, username, identityIds);
    Long[] ownerIds = identityIds.toArray(new Long[0]);
    List<Long> calendarsIds = this.agendaCalendarStorage.getCalendarIdsByOwnerIds(offset, limit, ownerIds);
    List<Calendar> calendars = new ArrayList<>();
    for (Long calendarId : calendarsIds) {
      Calendar calendar = getCalendarById(calendarId, username);
      if (calendar.isDeleted()) {
        continue;
      }
      calendars.add(calendar);
    }
    return calendars;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Calendar> getCalendarsByOwnerIds(List<Long> ownerIds, String username) throws IllegalAccessException {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    if (identity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    List<Long> calendarsIds = this.agendaCalendarStorage.getCalendarIdsByOwnerIds(0,
                                                                                  Integer.MAX_VALUE,
                                                                                  ownerIds.toArray(new Long[0]));
    List<Calendar> calendars = new ArrayList<>();
    for (Long calendarId : calendarsIds) {
      Calendar calendar = getCalendarById(calendarId, username);
      if (calendar.isDeleted()) {
        continue;
      }
      calendars.add(calendar);
    }
    return calendars;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countCalendars(String username) throws Exception {
    if (username == null) {
      throw new IllegalArgumentException("Username is mandatory");
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    if (identity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    List<Long> identityIds = new ArrayList<>();
    identityIds.add(Long.parseLong(identity.getId()));
    Utils.addUserSpacesIdentities(spaceService, identityManager, username, identityIds);
    Long[] ownerIds = identityIds.toArray(new Long[0]);
    return this.agendaCalendarStorage.countCalendarsByOwners(ownerIds);
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
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    if (userIdentity == null) {
      throw new IllegalStateException("User with name " + username + " is not found");
    }
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (calendar == null) {
      return null;
    }
    long ownerId = calendar.getOwnerId();
    Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (ownerIdentity == null) {
      calendar.setDeleted(true);
      calendar.setAcl(new CalendarPermission());
    } else {
      long userIdentityId = Long.parseLong(userIdentity.getId());
      if (!Utils.canAccessCalendar(identityManager, spaceService, ownerId, userIdentityId)) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
            + calendar.getTitle());
      } else {
        boolean canEditCalendar = Utils.canEditCalendar(identityManager,
                                                        spaceService,
                                                        ownerId,
                                                        Long.parseLong(userIdentity.getId()));
        boolean canCreateEvent = Utils.canCreateEvent(identityManager,
                                                      spaceService,
                                                      ownerId,
                                                      Long.parseLong(userIdentity.getId()));
        boolean hasRedactor = Utils.canInviteeEdit(identityManager,
                                                   spaceService,
                                                   ownerId);
        calendar.setAcl(new CalendarPermission(canCreateEvent, canEditCalendar, hasRedactor));
        resolveCalendarTitle(calendar);
      }
    }
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
    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (calendar == null) {
      return null;
    }
    Identity ownerIdentity = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    if (ownerIdentity == null) {
      calendar.setDeleted(true);
    } else {
      resolveCalendarTitle(calendar);
    }
    return calendar;
  }

  /**
   * {@inheritDoc} The default calendar of an owner is its system calendar
   * (the {@code isSystem} flag, not the oldest row): it is created lazily
   * here when absent — even when the owner already has user-created,
   * non-system calendars — and can never be deleted, so this method always
   * returns a usable default.
   */
  @Override
  public Calendar getOrCreateCalendarByOwnerId(long ownerId) {
    if (ownerId <= 0) {
      throw new IllegalArgumentException("Calendar ownerId has to be positive integer");
    }
    Identity userIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with technical identifier " + ownerId + " is not found");
    }
    Long systemCalendarId = agendaCalendarStorage.getSystemCalendarIdByOwnerId(ownerId);
    if (systemCalendarId == null) {
      Calendar calendar = createCalendarInstance(ownerId);
      calendar = agendaCalendarStorage.createCalendar(calendar);
      return calendar;
    } else {
      return this.getCalendarById(systemCalendarId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar createCalendarInstance(long ownerId) {
    return new Calendar(0, ownerId, true, null, null, null, null, getDefaultColor(ownerId), null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Calendar createCalendarInstance(long ownerId, long userIdentityId) throws IllegalAccessException {
    boolean canEditCalendar = Utils.canEditCalendar(identityManager, spaceService, ownerId, userIdentityId);
    boolean canCreateEvent = Utils.canCreateEvent(identityManager, spaceService, ownerId, userIdentityId);
    boolean canInviteeEdit = Utils.canInviteeEdit(identityManager, spaceService, ownerId);
    return new Calendar(0,
                        ownerId,
                        true,
                        null,
                        null,
                        null,
                        null,
                        getDefaultColor(ownerId),
                        new CalendarPermission(canCreateEvent, canEditCalendar, canInviteeEdit));
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
      Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
      if (userIdentity == null) {
        throw new IllegalStateException("User with name " + username + " is not found");
      }
      calendar.setOwnerId(Long.parseLong(userIdentity.getId()));
    } else {
      Utils.checkAclByCalendarOwner(identityManager, spaceService, calendar.getOwnerId(), username);
    }
    sanitizeAndValidateName(calendar);

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
    Utils.checkAclByCalendarOwner(identityManager, spaceService, calendar.getOwnerId(), username);
    sanitizeAndValidateName(calendar);
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
   * {@inheritDoc} A user-created (non-system) calendar is an organizational
   * label, not a lifecycle boundary: deleting it moves its events to the
   * owner's default (system) calendar — created lazily when absent — then
   * deletes the emptied calendar row, so no event is ever destroyed by a
   * user-initiated calendar deletion. The internal
   * {@link #deleteCalendarById(long)} overload keeps its cascading semantic
   * for the owner-identity-removal flow.
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
    Utils.checkAclByCalendarOwner(identityManager, spaceService, calendar.getOwnerId(), username);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    Calendar defaultCalendar = getOrCreateCalendarByOwnerId(calendar.getOwnerId());
    agendaCalendarStorage.moveCalendarEvents(calendarId,
                                             defaultCalendar.getId(),
                                             userIdentity == null ? 0 : Long.parseLong(userIdentity.getId()));
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
   * Computes the default color of a calendar from its owner identifier, so that
   * the color of a calendar that isn't created yet is stable: it's returned
   * identically by any previous read of the calendar, and it's the one that will
   * effectively be stored when the calendar gets created. Colors remain spread
   * over the configured palette to keep distinguishing calendars from each other.
   *
   * @param ownerId technical identifier of the calendar owner identity
   * @return default color of the calendar of the given owner
   */
  private String getDefaultColor(long ownerId) {
    int size = this.defaultColors.size();
    int index = (int) Math.abs(ownerId % size);
    return this.defaultColors.get(index);
  }

  /**
   * Refills, from the stored calendar, the fields a client is never allowed
   * to modify through UI or REST calls: creation date, owner, the
   * {@code system} flag (flipping it would make the undeletable default
   * calendar deletable, or promote a user calendar to undeletable), and the
   * synchronization identifier.
   *
   * @param calendar the {@link Calendar} received from the caller, mutated in
   *          place
   * @throws ObjectNotFoundException when no calendar is stored with this id
   */
  private void refillReadOnlyFields(Calendar calendar) throws ObjectNotFoundException {
    // Refill readonly fields from Database
    long calendarId = calendar.getId();
    Calendar storedCalendar = agendaCalendarStorage.getCalendarById(calendarId);
    if (storedCalendar == null) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " wasn't found");
    }
    calendar.setCreated(storedCalendar.getCreated());
    calendar.setOwnerId(storedCalendar.getOwnerId());
    calendar.setSystem(storedCalendar.isSystem());
    calendar.setSyncUid(storedCalendar.getSyncUid());
  }

  /**
   * Sanitizes then validates the user-defined name of a calendar: blank
   * names are normalized to {@code null} (meaning "derive the title from the
   * owner identity"), names are trimmed and limited to 200 characters, and a
   * name must stay unique (case-insensitively) among the calendars of the
   * same owner so two calendars are never indistinguishable. Uniqueness is
   * enforced softly here rather than by a database constraint to avoid
   * case/locale collation trouble across supported databases.
   *
   * @param calendar the {@link Calendar} to create or update, mutated in
   *          place
   * @throws IllegalArgumentException with a message code consumable by REST
   *           clients when the name exceeds 200 characters
   *           ({@code agenda.calendarNameExceedsMaxLength}) or is already
   *           used by another calendar of the same owner
   *           ({@code agenda.calendarNameAlreadyExists})
   */
  private void sanitizeAndValidateName(Calendar calendar) {
    String name = calendar.getName();
    if (StringUtils.isBlank(name)) {
      calendar.setName(null);
      return;
    }
    name = name.trim();
    if (name.length() > 200) {
      throw new IllegalArgumentException("agenda.calendarNameExceedsMaxLength");
    }
    calendar.setName(name);
    List<Long> ownerCalendarIds = agendaCalendarStorage.getCalendarIdsByOwnerIds(0,
                                                                                 Integer.MAX_VALUE,
                                                                                 calendar.getOwnerId());
    for (Long ownerCalendarId : ownerCalendarIds) {
      if (ownerCalendarId == null || ownerCalendarId == calendar.getId()) {
        continue;
      }
      Calendar ownerCalendar = agendaCalendarStorage.getCalendarById(ownerCalendarId);
      if (ownerCalendar != null && StringUtils.equalsIgnoreCase(name, ownerCalendar.getName())) {
        throw new IllegalArgumentException("agenda.calendarNameAlreadyExists");
      }
    }
  }

  /**
   * Resolves the displayed title of a calendar: the user-defined name wins
   * when present, else the title is derived from the owner identity display
   * name as before named calendars existed (which keeps every unnamed
   * calendar — including all space calendars — rendering exactly as before).
   *
   * @param calendar the {@link Calendar} whose title has to be resolved
   */
  private void resolveCalendarTitle(Calendar calendar) {
    if (StringUtils.isNotBlank(calendar.getName())) {
      calendar.setTitle(calendar.getName());
    } else {
      fillCalendarTitleByOwnerName(calendar);
    }
  }

  /**
   * Fills the calendar title from its owner identity display name: the user
   * full name for a personal calendar, the space display name for a space
   * calendar.
   *
   * @param calendar the {@link Calendar} whose title has to be filled
   */
  private void fillCalendarTitleByOwnerName(Calendar calendar) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    if (StringUtils.equals(requestedOwner.getProviderId(), OrganizationIdentityProvider.NAME)) {
      calendar.setTitle(requestedOwner.getProfile().getFullName());
    } else if (StringUtils.equals(requestedOwner.getProviderId(), SpaceIdentityProvider.NAME)) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      calendar.setTitle(space.getDisplayName());
    }
  }
}
