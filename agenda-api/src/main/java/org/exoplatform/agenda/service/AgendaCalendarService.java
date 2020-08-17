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

public interface AgendaCalendarService {

  /**
   * Retrieve list of calendars for a designated user
   * 
   * @param query Search query term
   * @param offset Offset of the search
   * @param limit Limit of results to retrieve
   * @param username User name accessing calendars
   * @return {@link List} of {@link Calendar}
   */
  List<Calendar> getCalendars(String query, int offset, int limit, String username);

  /**
   * Retrieve list of calendars for a designated user
   * 
   * @param ownerId calendar owner technical identity identifier
   * @param query Search query term
   * @param offset Offset of the search
   * @param limit Limit of results to retrieve
   * @param username User name accessing calendars
   * @return {@link List} of {@link Calendar}
   * @throws IllegalAccessException when user is not authorized to delete the
   *           calendar
   */
  List<Calendar> getCalendarsByOwner(long ownerId,
                                     String query,
                                     int offset,
                                     int limit,
                                     String username) throws IllegalAccessException;

  /**
   * Count available calendars for a designated user
   * 
   * @param query Search query term
   * @param username User name accessing calendars
   * @return count of available calendars
   */
  int countCalendars(String query, String username);

  /**
   * Count available calendars for a designated user
   * 
   * @param ownerId calendar owner technical identity identifier
   * @param query Search query term
   * @param username User name accessing calendars
   * @return count of available calendars
   */
  int countCalendarsByOwner(long ownerId, String query, String username);

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

}
