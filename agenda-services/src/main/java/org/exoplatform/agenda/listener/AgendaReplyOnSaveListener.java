package org.exoplatform.agenda.listener;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventDateOption;
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
public class AgendaReplyOnSaveListener extends Listener<Long, Long> {

  private static final Log           LOG = ExoLogger.getExoLogger(AgendaReplyOnSaveListener.class);

  private final PortalContainer      container;

  private AgendaEventService         agendaEventService;

  private AgendaEventDatePollService agendaEventDatePollService;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  public AgendaReplyOnSaveListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    if (event.getSource() == null) {
      LOG.warn("Event identifier can't be null. The user response will not be modified on event.");
      return;
    }
    if (event.getData() == null) {
      LOG.warn("Event modfier can't be null. The creator response will not be modified on event.");
      return;
    }

    long eventId = event.getSource();
    long modifierId = event.getData();
    try {
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);

      switch (agendaEvent.getStatus()) {
        case CONFIRMED:
          // Automatically change creator response to accepted
          if (getAgendaEventAttendeeService().isEventAttendee(eventId, modifierId)) {
            getAgendaEventAttendeeService().sendEventResponse(eventId, modifierId, EventAttendeeResponse.ACCEPTED);
          }
          break;
        case TENTATIVE:
          if (getAgendaEventAttendeeService().isEventAttendee(eventId, modifierId)) {
            // Automatically change creator response to accepted for all
            // proposed
            // Date Polls
            List<EventDateOption> eventDateOptions = getAgendaEventDatePollService().getEventDateOptions(eventId, ZoneOffset.UTC);
            List<Long> acceptedDatePollIds = eventDateOptions.stream().map(EventDateOption::getId).collect(Collectors.toList());
            getAgendaEventDatePollService().saveEventVotes(eventId, acceptedDatePollIds, modifierId);
          }
          break;
        case CANCELLED:
          // Automatically change creator response to accepted for all proposed
          // Date Polls
          List<EventAttendee> eventAttendees = getAgendaEventAttendeeService().getEventAttendees(eventId);
          for (EventAttendee eventAttendee : eventAttendees) {
            getAgendaEventAttendeeService().sendEventResponse(eventId,
                                                              eventAttendee.getIdentityId(),
                                                              EventAttendeeResponse.DECLINED);
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
