package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.model.AgendaEventModification;
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
public class AgendaESListener extends Listener<AgendaEventModification, Object> {
  private static final Log      LOG = ExoLogger.getExoLogger(AgendaESListener.class);

  private final PortalContainer container;

  private final IndexingService indexingService;

  private AgendaEventService    agendaEventService;

  public AgendaESListener(PortalContainer container, IndexingService indexingService) {
    this.container = container;
    this.indexingService = indexingService;
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      if (indexingService != null) {
        AgendaEventModification eventModifications = event.getSource();
        long eventId = eventModifications.getEventId();
        org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
        if (agendaEvent == null) {
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

  private void reindexAgendaEvent(long eventId, String cause) {
    LOG.debug("Notifying indexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.reindex(AgendaIndexingServiceConnector.TYPE, String.valueOf(eventId));
  }

  private void unindexAgendaEvent(long eventId, String cause) {
    LOG.debug("Notifying unindexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.unindex(AgendaIndexingServiceConnector.TYPE, String.valueOf(eventId));
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }
}
