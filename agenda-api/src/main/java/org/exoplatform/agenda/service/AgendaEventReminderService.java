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

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventReminderService {

  /**
   * Retrieve list of reminders for a user. If there is no specific reminder,
   * the default user reminders will be used. (Combined between User Settings
   * and default values)
   * 
   * @param eventId {@link Event} technical identifier
   * @param userIdentityId User technical identifier ({@link Identity#getId()})
   * @return {@link List} of {@link EventReminder} corresponding to user
   *         preferences.
   */
  List<EventReminder> getEventReminders(long eventId, long userIdentityId);

  /**
   * Updates the list of {@link EventReminder} associated to a user on a
   * selected event
   * 
   * @param event {@link Event} on which the reminder will be attached
   * @param reminders {@link List} of {@link EventReminder}
   * @param userIdentityId User technical identifier ({@link Identity#getId()})
   *          updating his/her reminders on the event
   * @throws IllegalAccessException when user isn't an attendee of the event
   * @throws AgendaException when a reminder datetime can't be computed
   */
  void saveEventReminders(Event event, List<EventReminder> reminders, long userIdentityId) throws IllegalAccessException,
                                                                                           AgendaException;

  /**
   *
   * @param event
   * @param reminders
   * @param usersIdentityId
   * @return
   * @throws IllegalAccessException
   * @throws AgendaException
   */
  List<EventReminder> computeUpdatedTriggerDate(Event event,
                                                List<EventReminder> reminders,
                                                List<Long> usersIdentityId) throws IllegalAccessException, AgendaException;
}
