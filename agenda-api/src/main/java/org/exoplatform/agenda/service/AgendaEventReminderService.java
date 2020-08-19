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

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public interface AgendaEventReminderService {

  /**
   * Retrieve list of reminders for current user. If there is no specific
   * reminder, the default user reminders will be used. (Combined between User
   * Settings and default values)
   * 
   * @param eventId {@link Event} technical identifier
   * @param username User accessing reminders of the event
   * @return {@link List} of {@link EventReminder} corresponding to user
   *         preferences.
   * @throws IllegalAccessException when user attempts to acces non authorized
   *           event
   * @throws ObjectNotFoundException when user attempts to acces not found event
   */
  List<EventReminder> getEventReminders(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Updates the list of {@link EventReminder} associated to a user on a
   * selected event
   * 
   * @param eventId {@link Event} technical identifier
   * @param reminders {@link List} of {@link EventReminder}
   * @param username User updating his/her reminders on the event
   * @throws IllegalAccessException when user attempts to acces non authorized
   *           event
   * @throws ObjectNotFoundException when user attempts to acces not found event
   */
  void updateEventReminders(long eventId, List<EventReminder> reminders, String username) throws IllegalAccessException,
                                                                                          ObjectNotFoundException;

}
