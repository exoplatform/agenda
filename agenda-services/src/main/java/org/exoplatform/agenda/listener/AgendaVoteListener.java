package org.exoplatform.agenda.listener;

import java.time.ZoneOffset;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.*;

/**
 * Listener that is triggered when a user sends a vote on an event. Once voted,
 * the response type of the user will be changed from NEEDS_ACTION to TENTATIVE
 * to determine whether the user has voted on the event before or not.
 */
@Asynchronous
public class AgendaVoteListener extends Listener<Long, Long> {

  private PortalContainer            container;

  private AgendaEventDatePollService datePollService;

  private AgendaEventAttendeeService attendeeService;

  public AgendaVoteListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    long dateOptionId = event.getSource();
    long identityId = event.getData();
    EventDateOption dateOption = getDatePollService().getEventDateOption(dateOptionId, ZoneOffset.UTC);
    long eventId = dateOption.getEventId();
    getAttendeeService().sendEventResponse(eventId, identityId, EventAttendeeResponse.TENTATIVE);
  }

  private AgendaEventAttendeeService getAttendeeService() {
    if (attendeeService == null) {
      attendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return attendeeService;
  }

  private AgendaEventDatePollService getDatePollService() {
    if (datePollService == null) {
      datePollService = container.getComponentInstanceOfType(AgendaEventDatePollService.class);
    }
    return datePollService;
  }
}
