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

import java.util.List;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaCalendarService {

  /**
   * Retrieve list of calendars for a designated user
   * 
   * @param offset Offset of the search
   * @param limit Limit of results to retrieve
   * @param username User name accessing calendars
   * @return {@link List} of {@link Calendar}
   * @throws Exception when an error occurs while retrieving calendars from
   *           database
   */
  List<Calendar> getCalendars(int offset, int limit, String username) throws Exception; // NOSONAR

  /**
   * Count available calendars for a designated user
   * 
   * @param username User name accessing calendars
   * @return count of available calendars
   * @throws Exception when an error occurs while accessing database
   */
  int countCalendars(String username) throws Exception; // NOSONAR

  /**
   * Retrieves a calendar identified by its technical identifier.
   * 
   * @param calendarId technical identifier of a calendar
   * @param username User name accessing calendar
   * @return A {@link Calendar} object
   * @throws IllegalAccessException when user is not authorized to access
   *           calendar
   */
  Calendar getCalendarById(long calendarId, String username) throws IllegalAccessException;

  /**
   * Retrieves a calendar identified by its technical identifier.
   * 
   * @param calendarId technical identifier of a calendar
   * @return A {@link Calendar} object
   */
  Calendar getCalendarById(long calendarId);

  /**
   * @param ownerId {@link Calendar} technical identifier
   * @return {@link Calendar} if found in database else create it
   */
  Calendar getOrCreateCalendarByOwnerId(long ownerId);

  /**
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param username User accessing list of calendars
   * @return {@link List} {@link Calendar} corresponding to calendar objects of
   *         owners.
   * @throws IllegalAccessException when user doesn't have acces to one of the
   *           calendars
   */
  List<Calendar> getCalendarsByOwnerIds(List<Long> ownerIds, String username) throws IllegalAccessException;

  /**
   * Creates a new calendar
   * 
   * @param calendar {@link Calendar} object to create
   * @param username User name creating calendar
   * @return created {@link Calendar} with generated technical identifier
   * @throws IllegalAccessException when user is not authorized to create a
   *           calendar for the designated owner defined in object
   */
  Calendar createCalendar(Calendar calendar, String username) throws IllegalAccessException;

  /**
   * Creates a new calendar
   * 
   * @param calendar {@link Calendar} object to create
   * @return created {@link Calendar} with generated technical identifier
   */
  Calendar createCalendar(Calendar calendar);

  /**
   * Create new {@link Calendar} object instance
   * 
   * @param ownerId {@link Identity} technical id of the {@link Calendar}
   * @return {@link Calendar} object instance
   */
  Calendar createCalendarInstance(long ownerId);

  /**
   * Create new {@link Calendar} object instance
   * 
   * @param ownerId {@link Identity} technical id of the {@link Calendar}
   * @param username user accessing {@link Calendar}
   * @return {@link Calendar} object instance
   * @throws IllegalAccessException when user is not authorized to access
   *           calendar of the designated owner
   */
  Calendar createCalendarInstance(long ownerId, String username) throws IllegalAccessException;

  /**
   * Updates an existing calendar
   * 
   * @param calendar {@link Calendar} object to update
   * @param username User name updating calendar
   * @throws IllegalAccessException when user is not authorized to update the
   *           calendar
   * @throws ObjectNotFoundException when the calendar identified by its
   *           technical identifier is not found
   */
  void updateCalendar(Calendar calendar, String username) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Updates an existing calendar
   * 
   * @param calendar {@link Calendar} object to update
   * @throws ObjectNotFoundException when the calendar identified by its
   *           technical identifier is not found
   */
  void updateCalendar(Calendar calendar) throws ObjectNotFoundException;

  /**
   * Deletes an existing calendar
   * 
   * @param calendarId Calendar technical identifier to delete
   * @param username User name deleting calendar
   * @throws IllegalAccessException when user is not authorized to delete the
   *           calendar
   * @throws ObjectNotFoundException when the calendar identified by its
   *           technical identifier is not found
   */
  void deleteCalendarById(long calendarId, String username) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Deletes an existing calendar
   * 
   * @param calendarId Calendar technical identifier to delete
   * @throws ObjectNotFoundException when the calendar identified by its
   *           technical identifier is not found
   */
  void deleteCalendarById(long calendarId) throws ObjectNotFoundException;

}
