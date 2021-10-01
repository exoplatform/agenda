// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * A listener that will be triggered when a new response is sent to an event.
 * This will update user reminders switch his/her default reminders. If the user
 * changed the response on an event from 'Yes' or 'May be' to 'No', then all
 * user reminders on the event has to be deleted automatically. When the user
 * changes his reponse from 'No' or 'Needs action' (default) to 'Yes' or 'May
 * be', then the default user reminders has to be added automatically. This
 * listener is triggered synchronously to ensure that the Event gets all its
 * updates (including user reminders) before returning the Event entity on REST
 * endpoint in order to display the updated response + added/removed reminders.
 */
public class AgendaEventReminderListener extends Listener<EventAttendee, EventAttendee> {

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaUserSettingsService  agendaUserSettingsService;

  private AgendaEventService         agendaEventService;

  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    EventAttendee oldAttendee = event.getSource();
    EventAttendee newAttendee = event.getData();

    boolean oldResponseNoReminders = oldAttendee == null || EventAttendeeResponse.DECLINED.equals(oldAttendee.getResponse())
        || EventAttendeeResponse.NEEDS_ACTION.equals(oldAttendee.getResponse());
    boolean newResponseNoReminders = EventAttendeeResponse.DECLINED.equals(newAttendee.getResponse())
        || EventAttendeeResponse.NEEDS_ACTION.equals(newAttendee.getResponse());

    if (oldResponseNoReminders != newResponseNoReminders) {
      long eventId = newAttendee.getEventId();
      long identityId = newAttendee.getIdentityId();
      if (newResponseNoReminders) {
        getAgendaEventReminderService().removeUserReminders(eventId, identityId);
      } else {
        org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
        AgendaUserSettings agendaUserSettings = getAgendaUserSettingsService().getAgendaUserSettings(identityId);
        List<EventReminderParameter> reminderParameters = agendaUserSettings.getReminders();
        if (reminderParameters != null && !reminderParameters.isEmpty()) {
          List<EventReminder> reminders =
                                        reminderParameters.stream()
                                                          .map(reminderParameter -> new EventReminder(identityId,
                                                                                                      reminderParameter.getBefore(),
                                                                                                      reminderParameter.getBeforePeriodType()))
                                                          .collect(Collectors.toList());
          getAgendaEventReminderService().saveEventReminders(agendaEvent, reminders, identityId);
        }
      }
    }
  }

  public AgendaUserSettingsService getAgendaUserSettingsService() {
    if (agendaUserSettingsService == null) {
      agendaUserSettingsService = ExoContainerContext.getService(AgendaUserSettingsService.class);
    }
    return agendaUserSettingsService;
  }

  public AgendaEventReminderService getAgendaEventReminderService() {
    if (agendaEventReminderService == null) {
      agendaEventReminderService = ExoContainerContext.getService(AgendaEventReminderService.class);
    }
    return agendaEventReminderService;
  }

  public AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }
}
