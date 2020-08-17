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

import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventService {

  /**
   * Retrieves the list of events available for a designated user in a selected
   * period of time.
   * 
   * @param start {@link ZonedDateTime} as selected period start datetime
   * @param end {@link ZonedDateTime} as selected period start datetime
   * @param username User name accessing event
   * @return {@link List} of {@link Event} accessible to user
   */
  public List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, String username);

  /**
   * Retrieves the list of events for a designated owner in a selected period of
   * time.
   * 
   * @param ownerId {@link Identity} technical identifier of the owner
   * @param start {@link ZonedDateTime} as selected period start datetime
   * @param end {@link ZonedDateTime} as selected period start datetime
   * @param username User name accessing event
   * @return {@link List} of {@link Event} available in calendars of an owner
   * @throws IllegalAccessException when user is not allowed to access events of
   *           designated owner
   */
  public List<Event> getEventsByOwner(long ownerId,
                                      ZonedDateTime start,
                                      ZonedDateTime end,
                                      String username) throws IllegalAccessException;

  /**
   * Retrieves an event identified by its technical identifier.
   * 
   * @param eventId technical identifier of event
   * @param username User name accessing event
   * @return Corresponding {@link Event} or null if not found
   * @throws IllegalAccessException when user is not allowed to access event
   */
  public Event getEventById(long eventId, String username) throws IllegalAccessException;

  /**
   * Creates an event in designated calendar or in user personal calendar if
   * null
   * 
   * @param event {@link Event} to create
   * @param username User name creating event
   * @throws IllegalAccessException when user is not allowed to access event
   */
  public void createEvent(Event event, String username) throws IllegalAccessException;

  /**
   * Updates an existing event in-place when the user is owner of parent
   * {@link Calendar}, else this will duplicate the event and add it into his
   * personal calendar
   * 
   * @param event {@link Event} to create
   * @param username User name creating event
   */
  public void updateEvent(Event event, String username);

  /**
   * Deletes an existing event
   * 
   * @param eventId Event technical identifier to delete
   * @param username User name deleting event
   * @throws IllegalAccessException when user is not authorized to delete the
   *           event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   */
  void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException;

}
