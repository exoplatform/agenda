// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
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
   * @return {@link Set} of {@link AgendaEventModificationType} containing
   *         modifications made on event reminders
   * @throws IllegalAccessException when user isn't an attendee of the event
   * @throws AgendaException when a reminder datetime can't be computed
   */
  Set<AgendaEventModificationType> saveEventReminders(Event event,
                                                      List<EventReminder> reminders,
                                                      long userIdentityId) throws IllegalAccessException,
                                                                           AgendaException;

  /**
   * Saves the {@link List} of {@link EventReminder} of a chosen {@link Event}
   * 
   * @param event {@link Event} for which saving reminders
   * @param reminders {@link List} of {@link EventReminder}
   */
  void saveEventReminders(Event event, List<EventReminder> reminders);

  /**
   * Save reminders for an upcoming of an occurrence of a recurring event for a
   * specific user
   * 
   * @param eventId technical identifier of {@link Event}
   * @param occurrenceId {@link Event} occurrence identifier on which the
   *          modification will be applied
   * @param reminders {@link List} of {@link EventReminder}
   * @param userIdentityId User technical identifier ({@link Identity#getId()})
   *          updating his/her reminders on the event
   * @throws AgendaException when an error occurs while saving reminders
   */
  void saveUpcomingEventReminders(long eventId,
                                  ZonedDateTime occurrenceId,
                                  List<EventReminder> reminders,
                                  long userIdentityId) throws AgendaException;

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
