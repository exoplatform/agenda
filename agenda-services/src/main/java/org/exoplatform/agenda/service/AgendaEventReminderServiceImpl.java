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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.storage.AgendaEventReminderStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventReminderServiceImpl implements AgendaEventReminderService {

  private AgendaEventReminderStorage reminderStorage;

  private ListenerService            listenerService;

  public AgendaEventReminderServiceImpl(AgendaEventReminderStorage reminderStorage, ListenerService listenerService) {
    this.reminderStorage = reminderStorage;
    this.listenerService = listenerService;
  }

  @Override
  public List<EventReminder> getEventReminders(long eventId, long userIdentityId) {
    return this.reminderStorage.getEventReminders(eventId, userIdentityId);
  }

  @Override
  public void saveEventReminders(Event event,
                                 List<EventReminder> reminders,
                                 long userId) throws AgendaException {
    List<EventReminder> savedReminders = getEventReminders(event.getId(), userId);
    List<EventReminder> newReminders = reminders == null ? Collections.emptyList() : reminders;
    List<EventReminder> remindersToDelete =
                                          savedReminders.stream()
                                                        .filter(reminder -> newReminders.stream()
                                                                                        .noneMatch(newReminder -> newReminder.getId() == reminder.getId()))
                                                        .collect(Collectors.toList());

    // Delete Reminders
    for (EventReminder eventReminder : remindersToDelete) {
      reminderStorage.removeEventReminder(eventReminder.getId());
    }

    // Create new Reminders
    for (EventReminder eventReminder : newReminders) {
      ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
      eventReminder.setDatetime(reminderDate);
      eventReminder.setReceiverId(userId);
      reminderStorage.saveEventReminder(event.getId(), eventReminder);
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", event.getId(), 0);
  }

  @Override
  public List<EventReminder> computeUpdatedTriggerDate(Event event, List<EventReminder> reminders, List<Long> usersIdentityId) throws IllegalAccessException, AgendaException {
    // TODO
    return null;
  }

  private ZonedDateTime computeReminderDateTime(Event event, EventReminder eventReminder) throws AgendaException {
    ZonedDateTime eventStartDate = event.getStart();
    if (eventReminder.getBefore() <= 0 || eventReminder.getBeforePeriodType() == null) {
      throw new AgendaException(AgendaExceptionType.REMINDER_DATE_CANT_COMPUTE);
    }
    ZonedDateTime reminderDate = null;
    if (eventReminder.getBefore() != 0) {
      switch (eventReminder.getBeforePeriodType()) {
      case MINUTE:
        reminderDate = eventStartDate.minusMinutes(eventReminder.getBefore());
        break;
      case HOUR:
        reminderDate = eventStartDate.minusHours(eventReminder.getBefore());
        break;
      case DAY:
        reminderDate = eventStartDate.minusDays(eventReminder.getBefore());
        break;
      case WEEK:
        reminderDate = eventStartDate.minusWeeks(eventReminder.getBefore());
        break;
      }
    } else {
      reminderDate = eventStartDate;
    }
    return reminderDate;
  }

}
