package org.exoplatform.agenda.listener;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.job.DailyReminderComputingJob;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * This listener will persist occurrences of recurrent event, that will trigger
 * its reminders before
 * {@link AgendaEventReminderService#getReminderComputingPeriod()} (in days). In
 * fact this listener will ensure that the processing made in
 * {@link DailyReminderComputingJob} is applied on all events, even those that
 * are created the same day.
 */
public class AgendaEventReminderComputingListener extends Listener<AgendaEventModification, Object> {

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaEventService         agendaEventService;

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    long eventId = event.getSource().getEventId();
    org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
    if (agendaEvent == null || agendaEvent.getStatus() != EventStatus.CONFIRMED) {
      return;
    }

    if (agendaEvent.getRecurrence() != null) {
      ZonedDateTime start = ZonedDateTime.now();
      ZonedDateTime end = start.plusDays(getAgendaEventReminderService().getReminderComputingPeriod());

      List<org.exoplatform.agenda.model.Event> occurrences = getAgendaEventService().getEventOccurrencesInPeriod(agendaEvent,
                                                                                                                 start,
                                                                                                                 end,
                                                                                                                 ZoneId.systemDefault(),
                                                                                                                 0);
      for (org.exoplatform.agenda.model.Event occurrence : occurrences) {
        ZonedDateTime occurrenceId = occurrence.getOccurrence().getId();
        getAgendaEventService().saveEventExceptionalOccurrence(eventId, occurrenceId);
      }
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
