// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.*;

/**
 * Listener that is triggered when a user sends a vote on an event. Once voted,
 * the response type of the user will be changed from NEEDS_ACTION to TENTATIVE
 * to determine whether the user has voted on the event before or not.
 */
public class AgendaVotesListener extends Listener<Long, Long> {

  private PortalContainer            container;

  private AgendaEventAttendeeService attendeeService;

  public AgendaVotesListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    long eventId = event.getSource();
    long identityId = event.getData();
    getAttendeeService().sendEventResponse(eventId, identityId, EventAttendeeResponse.TENTATIVE, false);
  }

  private AgendaEventAttendeeService getAttendeeService() {
    if (attendeeService == null) {
      attendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return attendeeService;
  }
}
