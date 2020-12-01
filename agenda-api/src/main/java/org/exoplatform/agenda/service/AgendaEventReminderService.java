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
import org.exoplatform.agenda.model.*;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventReminderService {

  /**
   * Retrieves list of reminders of an event for a user. If there is no specific
   * reminder, the default user reminders will be used. (Combined between User
   * Settings and default values)
   * 
   * @param eventId {@link Event} technical identifier
   * @param userIdentityId User technical identifier ({@link Identity#getId()})
   * @return {@link List} of {@link EventReminder} corresponding to user
   *         preferences.
   */
  List<EventReminder> getEventReminders(long eventId, long userIdentityId);

  /**
   * Retrieves list of reminders of an event. If there is no specific reminder,
   * the default user reminders will be used. (Combined between User Settings
   * and default values)
   * 
   * @param eventId {@link Event} technical identifier
   * @return {@link List} of {@link EventReminder}.
   */
  List<EventReminder> getEventReminders(long eventId);

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
   * Saves the {@link List} of {@link EventReminder} of a chosen {@link Event}
   * 
   * @param event {@link Event} for which saving reminders
   * @param reminders {@link List} of {@link EventReminder}
   * @throws AgendaException when an error occurs while saving reminders
   */
  void saveEventReminders(Event event, List<EventReminder> reminders) throws AgendaException;

  /**
   * @return period used to compute reminder of occurrences of a recurrent event
   *         in days
   */
  long getReminderComputingPeriod();

  /**
   * @param reminderComputingPeriod value of period used to compute reminder of
   *          occurrences of a recurrent event in days
   */
  void setReminderComputingPeriod(long reminderComputingPeriod);

  /**
   * @return {@link List} of {@link EventReminderParameter} that will be used
   *         for users who didn't changed default settings about preferred
   *         reminders
   */
  List<EventReminderParameter> getDefaultReminders();

  /**
   * Deletes all reminders of a user on a given event identified by its
   * identifier.
   * 
   * @param eventId technical identifier of {@link Event}
   * @param identityId technical identifier of {@link Identity}
   */
  void removeUserReminders(long eventId, long identityId);

  /**
   * Removes all reminders of a given {@link Event}
   * 
   * @param eventId technical identifier of {@link Event}
   */
  void removeEventReminders(long eventId);

  /**
   * Send reminders of upcoming events of next minute
   */
  void sendReminders();
}
