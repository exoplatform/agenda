// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * A listener that will be triggered when updating or creating an agenda event.
 * It will delete all attached reminders of a cancelled event.
 */
public class AgendaEventReminderCleanerListener extends Listener<AgendaEventModification, Object> {

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaEventService         agendaEventService;

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    long eventId = event.getSource().getEventId();
    org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);

    if (agendaEvent != null && agendaEvent.getStatus() == EventStatus.CANCELLED) {
      getAgendaEventReminderService().removeEventReminders(eventId);
    }
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
