package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.List;

public class ReminderListener extends Listener<Long, Object> {
  private static final Log LOG = ExoLogger.getExoLogger(ReminderListener.class);

  private final AgendaEventReminderService eventReminderService;

  private final AgendaEventService         eventService;

  private final AgendaEventAttendeeService eventAttendeeService;

    public ReminderListener(AgendaEventReminderService eventReminderService, AgendaEventService eventService, AgendaEventAttendeeService eventAttendeeService) {
        this.eventReminderService = eventReminderService;
        this.eventService = eventService;
        this.eventAttendeeService = eventAttendeeService;
    }

    @Override
    public void onEvent(Event<Long, Object> event) throws Exception {
      if (eventReminderService != null) {
        Long eventId = event.getSource();
        if (Utils.POST_UPDATE_AGENDA_STARTDATE_EVENT.equals(event.getEventName())) {
            computeUpdatedTriggerDate(eventId,"update agenda event reminder trigger date");
        }
      }
    }

    private void computeUpdatedTriggerDate(Long eventId, String cause) throws AgendaException {
      LOG.debug("Notifying indexing service for event with id={}. Cause: {}", eventId, cause);
      org.exoplatform.agenda.model.Event event = eventService.getEventById(eventId);
      List<EventAttendee> eventAttendees = eventAttendeeService.getEventAttendees(event.getId());
      boolean hasAttendees = eventAttendees != null && !eventAttendees.isEmpty();
      if (hasAttendees) {
        for (EventAttendee eventAttendee : eventAttendees) {
          eventReminderService.computeUpdatedTriggerDate(event, eventAttendee.getIdentityId());
        }
      }
    }
}
