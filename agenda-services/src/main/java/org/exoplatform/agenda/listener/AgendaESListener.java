package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.search.AgendaIndexingServiceConnector;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

@Asynchronous
public class AgendaESListener extends Listener<Long, Object> {
  private static final Log      LOG = ExoLogger.getExoLogger(AgendaESListener.class);

  private final PortalContainer container;

  private final IndexingService indexingService;

  private AgendaEventService    agendaEventService;

  public AgendaESListener(PortalContainer container, IndexingService indexingService) {
    this.container = container;
    this.indexingService = indexingService;
  }

  @Override
  public void onEvent(Event<Long, Object> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      if (indexingService != null) {
        String eventId = String.valueOf(event.getSource());
        org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(event.getSource());
        if (agendaEvent == null || agendaEvent.getStatus() != EventStatus.CONFIRMED) {
          return;
        }

        if (Utils.POST_CREATE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
          reindexAgendaEvent(eventId, "create agenda event");
        } else if (Utils.POST_UPDATE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
          reindexAgendaEvent(eventId, "update agenda event");
        } else if (Utils.POST_DELETE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
          unindexAgendaEvent(eventId, "delete agenda event");
        }
      }
    } finally {
      RequestLifeCycle.end();
    }
  }

  private void reindexAgendaEvent(String eventId, String cause) {
    LOG.debug("Notifying indexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.reindex(AgendaIndexingServiceConnector.TYPE, eventId);
  }

  private void unindexAgendaEvent(String eventId, String cause) {
    LOG.debug("Notifying unindexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.unindex(AgendaIndexingServiceConnector.TYPE, eventId);
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }
}
