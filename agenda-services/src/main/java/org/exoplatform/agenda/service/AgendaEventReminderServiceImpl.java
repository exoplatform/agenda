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

import static org.exoplatform.agenda.util.NotificationUtils.AGENDA_REMINDER_NOTIFICATION_PLUGIN;
import static org.exoplatform.agenda.util.NotificationUtils.EVENT_AGENDA_REMINDER;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.storage.*;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.*;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventReminderServiceImpl implements AgendaEventReminderService {

  private AgendaEventReminderStorage   reminderStorage;

  private AgendaEventStorage           eventStorage;

  private AgendaEventAttendeeStorage   attendeeStorage;

  private IdentityManager              identityManager;

  private SpaceService                 spaceService;

  private ListenerService              listenerService;

  private List<EventReminderParameter> defaultReminders        = new ArrayList<>();

  private long                         reminderComputingPeriod = 2;

  public AgendaEventReminderServiceImpl(AgendaEventReminderStorage reminderStorage,
                                        AgendaEventStorage eventStorage,
                                        AgendaEventAttendeeStorage attendeeStorage,
                                        IdentityManager identityManager,
                                        SpaceService spaceService,
                                        ListenerService listenerService,
                                        InitParams initParams) {
    this.reminderStorage = reminderStorage;
    this.eventStorage = eventStorage;
    this.attendeeStorage = attendeeStorage;
    this.listenerService = listenerService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;

    Iterator<ObjectParameter> objectParamIterator = initParams.getObjectParamIterator();
    if (objectParamIterator != null) {
      while (objectParamIterator.hasNext()) {
        ObjectParameter objectParameter = objectParamIterator.next();
        Object objectParam = objectParameter.getObject();
        if (objectParam instanceof EventReminderParameter) {
          EventReminderParameter eventReminderParameter = (EventReminderParameter) objectParam;
          defaultReminders.add(eventReminderParameter);
        }
      }
    }

    ValueParam reminderComputingPeriodParam = initParams.getValueParam("period.computing.days");
    if (reminderComputingPeriodParam != null && reminderComputingPeriodParam.getValue() != null) {
      this.reminderComputingPeriod = Long.parseLong(reminderComputingPeriodParam.getValue());
    }
  }

  @Override
  public List<EventReminder> getEventReminders(long eventId, long userIdentityId) {
    return this.reminderStorage.getEventReminders(eventId, userIdentityId);
  }

  @Override
  public List<EventReminder> getEventReminders(long eventId) {
    return this.reminderStorage.getEventReminders(eventId);
  }

  @Override
  public void saveEventReminders(Event event, List<EventReminder> reminders) throws AgendaException {
    long eventId = event.getId();
    List<EventReminder> savedReminders = getEventReminders(eventId);
    List<EventReminder> newReminders = reminders == null ? Collections.emptyList() : reminders;
    List<EventReminder> remindersToDelete = savedReminders.stream()
                                                          .filter(reminder -> newReminders.stream()
                                                                                          .noneMatch(newReminder -> newReminder.getId() == reminder.getId()))
                                                          .collect(Collectors.toList());

    // Delete Reminders
    for (EventReminder eventReminder : remindersToDelete) {
      reminderStorage.removeEventReminder(eventReminder.getId());
    }

    // Create new Reminders
    if (reminders != null && !reminders.isEmpty()) {
      for (EventReminder eventReminder : reminders) {
        ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
        eventReminder.setDatetime(reminderDate);
        eventReminder.setEventId(eventId);
        reminderStorage.saveEventReminder(eventId, eventReminder);
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", eventId, 0);
  }

  @Override
  public void saveEventReminders(Event event,
                                 List<EventReminder> reminders,
                                 long identityId) throws AgendaException {
    long eventId = event.getId();
    List<EventReminder> savedReminders = getEventReminders(eventId, identityId);
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

    // Create new Reminders and update old ones
    if (reminders != null && !reminders.isEmpty()) {
      for (EventReminder eventReminder : reminders) {
        ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
        eventReminder.setDatetime(reminderDate);
        eventReminder.setReceiverId(identityId);
        eventReminder.setEventId(eventId);
        reminderStorage.saveEventReminder(eventId, eventReminder);
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", eventId, identityId);

    // Apply modification on exceptional occurrences as well
    if (eventStorage.isRecurrentEvent(eventId)) {
      List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId);
      for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
        List<EventAttendee> eventAttendees = attendeeStorage.getEventAttendees(exceptionalOccurenceEventId);
        if (Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees)) {
          Event exceptionalOccurrenceEvent = eventStorage.getEventById(exceptionalOccurenceEventId);

          // Ensure to not erase parent recurrent event reminders
          if (reminders != null && !reminders.isEmpty()) {
            reminders.forEach(reminder -> reminder.setId(0));
          }
          saveEventReminders(exceptionalOccurrenceEvent, reminders, identityId);
        }
      }
    }
  }

  @Override
  public void sendReminders() {
    ZonedDateTime currentMinute = ZonedDateTime.now(ZoneOffset.UTC).withNano(0).withSecond(0);
    ZonedDateTime endCurrentMinute = currentMinute.plusMinutes(1);

    List<EventReminder> reminders = reminderStorage.getEventReminders(currentMinute, endCurrentMinute);
    for (EventReminder eventReminder : reminders) {
      sendReminderNotification(eventReminder);
    }
  }

  @Override
  public void removeEventReminders(long eventId) {
    reminderStorage.removeEventReminders(eventId);
  }

  @Override
  public void removeUserReminders(long eventId, long identityId) {
    reminderStorage.removeEventReminders(eventId, identityId);
  }

  @Override
  public long getReminderComputingPeriod() {
    return reminderComputingPeriod;
  }

  @Override
  public void setReminderComputingPeriod(long reminderComputingPeriod) {
    this.reminderComputingPeriod = reminderComputingPeriod;
  }

  @Override
  public List<EventReminderParameter> getDefaultReminders() {
    return Collections.unmodifiableList(defaultReminders);
  }

  private ZonedDateTime computeReminderDateTime(Event event, EventReminder eventReminder) throws AgendaException {
    ZonedDateTime eventStartDate = event.getStart();
    if (eventReminder.getBefore() < 0 || eventReminder.getBeforePeriodType() == null) {
      throw new AgendaException(AgendaExceptionType.REMINDER_DATE_CANT_COMPUTE);
    }
    ZonedDateTime reminderDate = null;
    if (eventReminder.getBefore() > 0) {
      reminderDate = eventStartDate.minus(eventReminder.getBefore(), eventReminder.getBeforePeriodType().getTemporalUnit())
                                   .withZoneSameInstant(ZoneOffset.UTC);
    } else {
      reminderDate = eventStartDate;
    }
    return reminderDate;
  }

  private void sendReminderNotification(EventReminder eventReminder) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(EVENT_AGENDA_REMINDER, eventReminder);
    NotificationCommand command = ctx.makeCommand(PluginKey.key(AGENDA_REMINDER_NOTIFICATION_PLUGIN));
    ctx.getNotificationExecutor().with(command).execute(ctx);
  }

}
