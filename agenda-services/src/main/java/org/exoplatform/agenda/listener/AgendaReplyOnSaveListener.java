package org.exoplatform.agenda.listener;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * A listener that will be triggered synchronously after saving (create OR
 * update) an event to change automatically response of users
 */
public class AgendaReplyOnSaveListener extends Listener<AgendaEventModification, Object> {

  private static final Log           LOG = ExoLogger.getExoLogger(AgendaReplyOnSaveListener.class);

  private final PortalContainer      container;

  private AgendaEventService         agendaEventService;

  private AgendaEventDatePollService agendaEventDatePollService;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  public AgendaReplyOnSaveListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification eventModification = event.getSource();
    long eventId = eventModification.getEventId();
    long modifierId = eventModification.getModifierId();
    try {
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      List<EventAttendee> eventAttendees = getAgendaEventAttendeeService().getEventAttendees(eventId).getEventAttendees(null);
      switch (agendaEvent.getStatus()) {
        case CONFIRMED:
          // Check if dates modification is made
          if (eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.ADDED)) {
            // Automatically change creator response to accepted
            // and for others to NEEDS_ACTION
            for (EventAttendee eventAttendee : eventAttendees) {
              EventAttendeeResponse response = modifierId == eventAttendee.getIdentityId() ? EventAttendeeResponse.ACCEPTED
                                                                                           : EventAttendeeResponse.NEEDS_ACTION;
              if (eventAttendee.getResponse() != response) {
                getAgendaEventAttendeeService().sendEventResponse(eventId, eventAttendee.getIdentityId(), response, false);
              }
            }
          }
          break;
        case TENTATIVE:
          if (getAgendaEventAttendeeService().isEventAttendee(eventId, modifierId)) {
            // Automatically change creator response to accepted for all
            // proposed Date Polls
            List<EventDateOption> eventDateOptions = getAgendaEventDatePollService().getEventDateOptions(eventId, ZoneOffset.UTC);
            List<Long> acceptedDatePollIds = eventDateOptions.stream().map(EventDateOption::getId).collect(Collectors.toList());
            getAgendaEventDatePollService().saveEventVotes(eventId, acceptedDatePollIds, modifierId);
          }
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.warn("An error occurred while changing event '{}' response of user '{}'", eventId, modifierId, e);
    }
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private AgendaEventDatePollService getAgendaEventDatePollService() {
    if (agendaEventDatePollService == null) {
      agendaEventDatePollService = container.getComponentInstanceOfType(AgendaEventDatePollService.class);
    }
    return agendaEventDatePollService;
  }

  private AgendaEventAttendeeService getAgendaEventAttendeeService() {
    if (agendaEventAttendeeService == null) {
      agendaEventAttendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return agendaEventAttendeeService;
  }
}
