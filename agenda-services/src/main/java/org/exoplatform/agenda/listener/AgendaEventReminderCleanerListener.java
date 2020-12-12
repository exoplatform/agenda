package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * A listener that will be triggered when updating or creating an agenda event.
 * It will delete all attached reminders of a cancelled event.
 */
public class AgendaEventReminderCleanerListener extends Listener<Long, Object> {

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaEventService         agendaEventService;

  @Override
  public void onEvent(Event<Long, Object> event) throws Exception {
    Long eventId = event.getSource();
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
