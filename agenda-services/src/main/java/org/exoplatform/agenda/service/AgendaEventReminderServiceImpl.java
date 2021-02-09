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

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.storage.*;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventReminderServiceImpl implements AgendaEventReminderService {

  private static final Log           LOG                     = ExoLogger.getLogger(AgendaEventReminderServiceImpl.class);

  private AgendaUserSettingsService  agendaUserSettingsService;

  private AgendaEventReminderStorage reminderStorage;

  private AgendaEventStorage         eventStorage;

  private AgendaEventAttendeeStorage attendeeStorage;

  private IdentityManager            identityManager;

  private SpaceService               spaceService;

  private ListenerService            listenerService;

  private long                       reminderComputingPeriod = 2;

  public AgendaEventReminderServiceImpl(AgendaEventReminderStorage reminderStorage,
                                        AgendaEventStorage eventStorage,
                                        AgendaEventAttendeeStorage attendeeStorage,
                                        AgendaUserSettingsService agendaUserSettingsService,
                                        IdentityManager identityManager,
                                        SpaceService spaceService,
                                        ListenerService listenerService,
                                        InitParams initParams) {
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.reminderStorage = reminderStorage;
    this.eventStorage = eventStorage;
    this.attendeeStorage = attendeeStorage;
    this.listenerService = listenerService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;

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
  public void saveEventReminders(Event event, List<EventReminder> reminders) {
    long eventId = event.getId();
    boolean isRecurrentEvent = event.getRecurrence() != null;
    boolean isOccurrence = event.getOccurrence() != null && event.getOccurrence().getId() != null;

    if (isOccurrence) {
      ZonedDateTime occurrenceId = event.getOccurrence().getId();
      reminders = reminders.stream()
                           .filter(reminder -> (reminder.getFromOccurrenceId() == null
                               || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                               || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                               && (reminder.getUntilOccurrenceId() == null
                                   || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                           .collect(Collectors.toList());
    }

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
        eventReminder = eventReminder.clone();

        try {
          ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
          eventReminder.setDatetime(reminderDate);
          eventReminder.setEventId(eventId);
          if (!isRecurrentEvent) {
            eventReminder.setFromOccurrenceId(null);
            eventReminder.setUntilOccurrenceId(null);
          }
          reminderStorage.saveEventReminder(eventReminder);
        } catch (AgendaException e) {
          LOG.warn("Error saving reminder of event with id {}", event.getId(), e);
        }
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", eventId, 0);
  }

  @Override
  public void saveUpcomingEventReminders(long eventId,
                                         ZonedDateTime occurrenceId,
                                         List<EventReminder> reminders,
                                         long identityId) throws AgendaException {
    Event recurringEvent = eventStorage.getEventById(eventId);
    if (recurringEvent.getRecurrence() == null) {
      throw new IllegalStateException("event is not recurrent");
    }

    if (reminders == null) {
      reminders = new ArrayList<>();
    } else {
      for (EventReminder eventReminder : reminders) {
        eventReminder.setId(0);
        eventReminder.setFromOccurrenceId(occurrenceId);
        eventReminder.setUntilOccurrenceId(null);
      }
      reminders = new ArrayList<>(reminders);
    }

    List<EventReminder> existingReminders = getEventReminders(eventId);
    // Filter on existing reminders that are registered for future events
    // to replace those reminders by the new ones
    existingReminders = existingReminders.stream()
                                         .filter((reminder -> reminder.getFromOccurrenceId() == null
                                             || reminder.getFromOccurrenceId().isBefore(occurrenceId)))
                                         .collect(Collectors.toList());
    for (EventReminder eventReminder : existingReminders) {
      // Apply until date only on reminders before the chosen date
      if (eventReminder.getUntilOccurrenceId() == null
          || eventReminder.getUntilOccurrenceId().isAfter(occurrenceId)) {
        eventReminder.setUntilOccurrenceId(occurrenceId);
      }
    }

    reminders.addAll(existingReminders);
    saveEventReminders(recurringEvent, occurrenceId, reminders, identityId);
  }

  @Override
  public void saveEventReminders(Event event,
                                 List<EventReminder> reminders,
                                 long identityId) throws AgendaException {
    saveEventReminders(event, null, reminders, identityId);
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

  private void saveEventReminders(Event event,
                                  ZonedDateTime fromOccurrenceId,
                                  List<EventReminder> reminders,
                                  long identityId) throws AgendaException {
    long eventId = event.getId();
    boolean isRecurrentEvent = event.getRecurrence() != null;
    if (event.getStatus() == EventStatus.CANCELLED) {
      // Delete all reminders of user when event is not confirmed yet
      reminders = null;
    }

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
        eventReminder = eventReminder.clone();

        ZonedDateTime reminderDate = computeReminderDateTime(event, eventReminder);
        eventReminder.setDatetime(reminderDate);
        eventReminder.setReceiverId(identityId);
        eventReminder.setEventId(eventId);
        if (!isRecurrentEvent) {
          eventReminder.setFromOccurrenceId(null);
          eventReminder.setUntilOccurrenceId(null);
        }
        reminderStorage.saveEventReminder(eventReminder);
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.reminders.saved", eventId, identityId);

    // Apply modification on exceptional occurrences as well
    if (isRecurrentEvent) {
      saveExceptionalOccurrencesReminders(eventId, fromOccurrenceId, reminders, identityId);
    }
  }

  private void saveExceptionalOccurrencesReminders(long eventId,
                                                   ZonedDateTime fromOccurrenceId,
                                                   List<EventReminder> reminders,
                                                   long identityId) throws AgendaException {
    List<Long> exceptionalOccurenceEventIds = eventStorage.getExceptionalOccurenceIds(eventId);
    for (long exceptionalOccurenceEventId : exceptionalOccurenceEventIds) {
      List<EventAttendee> eventAttendees = attendeeStorage.getEventAttendees(exceptionalOccurenceEventId);
      if (Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees)) {
        Event exceptionalOccurrenceEvent = eventStorage.getEventById(exceptionalOccurenceEventId);
        ZonedDateTime exceptionalOccurrenceId = exceptionalOccurrenceEvent.getOccurrence().getId();
        if (fromOccurrenceId != null && exceptionalOccurrenceId.isBefore(fromOccurrenceId)) {
          continue;
        }

        // Ensure to not erase parent recurrent event reminders
        List<EventReminder> occurrenceReminders = null;
        if (reminders != null && !reminders.isEmpty()) {
          occurrenceReminders = reminders.stream()
                                         .filter(reminder -> (reminder.getFromOccurrenceId() == null
                                             || reminder.getFromOccurrenceId().isEqual(exceptionalOccurrenceId)
                                             || reminder.getFromOccurrenceId().isBefore(exceptionalOccurrenceId))
                                             && (reminder.getUntilOccurrenceId() == null
                                                 || reminder.getUntilOccurrenceId().isAfter(exceptionalOccurrenceId)))
                                         .collect(Collectors.toList());
          occurrenceReminders.forEach(reminder -> reminder.setId(0));
        }
        saveEventReminders(exceptionalOccurrenceEvent, occurrenceReminders, identityId);
      }
    }
  }

  private ZonedDateTime computeReminderDateTime(Event event, EventReminder eventReminder) throws AgendaException {
    if (event.getStatus() != EventStatus.CONFIRMED) {
      return null;
    }
    ZonedDateTime eventStartDate = event.getStart();
    if (eventReminder.getBefore() < 0 || eventReminder.getBeforePeriodType() == null) {
      throw new AgendaException(AgendaExceptionType.REMINDER_DATE_CANT_COMPUTE);
    }
    if (event.isAllDay()) {
      ZoneId userTimeZone = event.getTimeZoneId();
      AgendaUserSettings userSettings = agendaUserSettingsService.getAgendaUserSettings(eventReminder.getReceiverId());
      if (userSettings != null && userSettings.getTimeZoneId() != null) {
        userTimeZone = ZoneId.of(userSettings.getTimeZoneId());
      } else if (userTimeZone == null) {
        userTimeZone = ZoneOffset.UTC;
      }
      eventStartDate = eventStartDate.toLocalDate().atStartOfDay(userTimeZone);
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
