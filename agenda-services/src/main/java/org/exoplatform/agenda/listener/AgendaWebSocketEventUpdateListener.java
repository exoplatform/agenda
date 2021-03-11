package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaWebSocketService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;

/**
 * A listener that will be triggered synchronously after saving (create OR
 * update) an event to transmit modifications to end users via WebSocket
 */
@Asynchronous
public class AgendaWebSocketEventUpdateListener extends Listener<AgendaEventModification, Object> {

  private PortalContainer        container;

  private AgendaWebSocketService agendaWebSocketService;

  public AgendaWebSocketEventUpdateListener(PortalContainer container,
                                            AgendaWebSocketService agendaWebSocketService) {
    this.container = container;
    this.agendaWebSocketService = agendaWebSocketService;
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    RequestLifeCycle.begin(container);
    try {
      AgendaEventModification eventModification = event.getSource();
      agendaWebSocketService.sendMessage(event.getEventName(), eventModification);
    } finally {
      RequestLifeCycle.end();
    }
  }

}
