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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.storage.AgendaEventReminderStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.*;
import org.exoplatform.services.listener.ListenerService;

import static org.exoplatform.agenda.util.NotificationUtils.*;

public class AgendaEventReminderServiceImpl implements AgendaEventReminderService {

  private static final String          AGENDA_USER_REMINDER_SETTING_SEPARATOR = "@@";

  private static final String          AGENDA_USER_REMINDER_SETTING_KEY       = "Reminders";

  private static final Scope           AGENDA_USER_REMINDER_SETTING_SCOPE     = Scope.APPLICATION.id("Agenda");

  private AgendaEventReminderStorage   reminderStorage;

  private SettingService               settingService;

  private ListenerService              listenerService;

  private List<EventReminderParameter> defaultReminders                       = new ArrayList<>();

  private long                         reminderComputingPeriod                = 2;

  public AgendaEventReminderServiceImpl(AgendaEventReminderStorage reminderStorage,
                                        SettingService settingService,
                                        ListenerService listenerService,
                                        InitParams initParams) {
    this.reminderStorage = reminderStorage;
    this.settingService = settingService;
    this.listenerService = listenerService;

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
    for (EventReminder eventReminder : newReminders) {
      ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
      eventReminder.setDatetime(reminderDate);
      reminderStorage.saveEventReminder(eventId, eventReminder);
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", eventId, 0);
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

    // Create new Reminders and update old ones
    if (reminders != null && !reminders.isEmpty()) {
      for (EventReminder eventReminder : reminders) {
        ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
        eventReminder.setDatetime(reminderDate);
        eventReminder.setReceiverId(userId);
        reminderStorage.saveEventReminder(event.getId(), eventReminder);
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", event.getId(), userId);
  }

  @Override
  public void saveUserDefaultRemindersSetting(long identityId, List<EventReminderParameter> eventReminderParameters) {
    if (eventReminderParameters == null || eventReminderParameters.isEmpty()) {
      this.settingService.set(Context.USER.id(String.valueOf(identityId)),
                              AGENDA_USER_REMINDER_SETTING_SCOPE,
                              AGENDA_USER_REMINDER_SETTING_KEY,
                              SettingValue.create(""));
    } else {
      String remindersSettingString = StringUtils.join(eventReminderParameters, AGENDA_USER_REMINDER_SETTING_SEPARATOR);
      this.settingService.set(Context.USER.id(String.valueOf(identityId)),
                              AGENDA_USER_REMINDER_SETTING_SCOPE,
                              AGENDA_USER_REMINDER_SETTING_KEY,
                              SettingValue.create(remindersSettingString));
    }
  }

  @Override
  public List<EventReminderParameter> getUserDefaultRemindersSettings(long identityId) {
    SettingValue<?> settingValue = this.settingService.get(Context.USER.id(String.valueOf(identityId)),
                                                           AGENDA_USER_REMINDER_SETTING_SCOPE,
                                                           AGENDA_USER_REMINDER_SETTING_KEY);

    if (settingValue == null) {
      return getDefaultReminders();
    } else if (settingValue.getValue() == null || StringUtils.isBlank(settingValue.getValue().toString())) {
      return Collections.emptyList();
    } else {
      String remindersSettingString = settingValue.getValue().toString();
      String[] values = StringUtils.split(remindersSettingString, AGENDA_USER_REMINDER_SETTING_SEPARATOR);
      return Arrays.stream(values).map(EventReminderParameter::fromString).collect(Collectors.toList());
    }
  }

  @Override
  public void saveUserDefaultReminders(Event event, long identityId) throws AgendaException {
    List<EventReminderParameter> userRemindersSettings = getUserDefaultRemindersSettings(identityId);
    List<EventReminder> eventReminders =
                                       userRemindersSettings.stream()
                                                            .map(reminderParameter -> new EventReminder(identityId,
                                                                                                        reminderParameter.getBefore(),
                                                                                                        reminderParameter.toPeriodTypeEnum()))
                                                            .collect(Collectors.toList());

    saveEventReminders(event, eventReminders, identityId);
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
