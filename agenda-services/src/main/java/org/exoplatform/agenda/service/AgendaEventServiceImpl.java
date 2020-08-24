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

import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventServiceImpl implements AgendaEventService {

  private AgendaEventStorage agendaEventStorage;

  private IdentityManager    identityManager;

  private SpaceService       spaceService;

  public AgendaEventServiceImpl(AgendaEventStorage agendaEventStorage,
                                IdentityManager identityManager,
                                SpaceService spaceService) {
    this.agendaEventStorage = agendaEventStorage;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  @Override
  public List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, String username) {
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    List<Long> calendarOwners = new ArrayList<>();
    String userIdentityId = userIdentity.getId();
    calendarOwners.add(Long.parseLong(userIdentityId));
    try {
      Utils.addUserSpacesIdentities(spaceService, identityManager, username, calendarOwners);
    } catch (Exception e) {
      throw new IllegalStateException("Error while retrieving spaces of user with id: " + userIdentityId, e);
    }

    TimeZone userTimezone = AgendaDateUtils.getUserTimezone(userIdentity);
    List<Event> events = this.agendaEventStorage.getEvents(start, end, userTimezone, calendarOwners.toArray(new Long[0]));
    if (events == null || events.isEmpty()) {
      return Collections.emptyList();
    }
    List<Event> computedEvents = new ArrayList<>();
    for (Event event : events) {
      if (event.getRecurrence() != null) {
        List<Event> occurrences = Utils.getOccurrences(event, start.toLocalDate(), end.toLocalDate(), userTimezone);
        // TODO
      }
    }
    return computedEvents;
  }

  @Override
  public List<Event> getEventsByOwner(long ownerId,
                                      ZonedDateTime start,
                                      ZonedDateTime end,
                                      String username) throws IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Event getEventById(long eventId, String username) throws IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Event getEventById(long eventId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void createEvent(Event event, String username) throws IllegalAccessException {
    // TODO Auto-generated method stub

  }

  @Override
  public void updateEvent(Event event, String username) {
    // TODO Auto-generated method stub

  }

  @Override
  public void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException {
    // TODO Auto-generated method stub

  }

}
