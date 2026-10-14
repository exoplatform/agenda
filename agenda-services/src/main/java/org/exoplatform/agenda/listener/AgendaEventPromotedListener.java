/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * Tells the event's manager that an attendee on the waiting list got a seat,
 * with the "Answer to an invitation" notification worded as a Yes (EXO-90311).
 * A promotion is not an answer the attendee sent, so the reply listener, which
 * listens to the answers sent, never hears it. Glue only: the source is the
 * attendee row before the promotion, the data the row after it, starting at the
 * first date the attendee got a seat for on a series. Asynchronous, as no
 * decision on a seat waits for a notification.
 */
@Asynchronous
public class AgendaEventPromotedListener extends Listener<EventAttendee, EventAttendee> {

  private final ExoContainer container;

  private AgendaEventService agendaEventService;

  /**
   * Builds the listener.
   *
   * @param container the portal container the notification runs in
   */
  public AgendaEventPromotedListener(ExoContainer container) {
    this.container = container;
  }

  /**
   * Sends the manager's notification of a promotion on a confirmed event.
   *
   * @param event the promotion, whose data is the promoted attendee row
   * @throws Exception when the dispatch fails
   */
  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    EventAttendee promotedAttendee = event == null ? null : event.getData();
    if (promotedAttendee == null) {
      return;
    }
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(promotedAttendee.getEventId());
      if (agendaEvent == null || agendaEvent.getStatus() != EventStatus.CONFIRMED) {
        return;
      }
      AgendaEventReplyListener.sendReplyNotification(agendaEvent, promotedAttendee);
    } finally {
      RequestLifeCycle.end();
    }
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }
}
