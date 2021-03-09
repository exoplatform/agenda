package org.exoplatform.agenda.listener;

import java.util.Collections;

import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.WebSocketMessage;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaWebSocketService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * A listener that will be triggered synchronously after saving user reply to an
 * event to transmit modifications to the end user himself via WebSocket. This
 * will allow to update listed events when the response changed switch filters
 * used to display event in UI.
 */
@Asynchronous
public class AgendaWebSocketEventReplyListener extends Listener<EventAttendee, EventAttendee> {

  private PortalContainer        container;

  private AgendaEventService     agendaEventService;

  private AgendaWebSocketService agendaWebSocketService;

  private IdentityManager        identityManager;

  public AgendaWebSocketEventReplyListener(PortalContainer container,
                                           AgendaEventService agendaEventService,
                                           IdentityManager identityManager,
                                           AgendaWebSocketService agendaWebSocketService) {
    this.container = container;
    this.agendaEventService = agendaEventService;
    this.agendaWebSocketService = agendaWebSocketService;
    this.identityManager = identityManager;
  }

  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    RequestLifeCycle.begin(container);
    try {
      EventAttendee newAttendee = event.getData();
      long eventId = newAttendee.getEventId();
      long userIdentityId = newAttendee.getIdentityId();

      org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
      long calendarId = agendaEvent.getCalendarId();

      WebSocketMessage socketMessage = new WebSocketMessage(event.getEventName());
      socketMessage.addParam("eventId", eventId);
      socketMessage.addParam("calendarId", calendarId);

      Identity identity = identityManager.getIdentity(String.valueOf(userIdentityId));
      String username = identity.getRemoteId();
      agendaWebSocketService.sendMessage(socketMessage, Collections.singleton(username));
    } finally {
      RequestLifeCycle.end();
    }
  }

}
