package org.exoplatform.agenda.search.listener;

import org.exoplatform.agenda.search.AgendaIndexingServiceConnector;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.listener.Listener;

public class AgendaESListener extends Listener<Long, Object> {
  private static final Log LOG = ExoLogger.getExoLogger(AgendaESListener.class);

  private final IndexingService indexingService;

  public AgendaESListener(IndexingService indexingService) {
    this.indexingService = indexingService;
  }

  @Override
  public void onEvent(Event<Long, Object> event) throws Exception {
    if(indexingService != null) {
      String eventId = Long.toString(event.getSource());
      if(AgendaEventService.POST_CREATE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
        reindexAgendaEvent(eventId, "create agenda event");
      } else if(AgendaEventService.POST_UPDATE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
        reindexAgendaEvent(eventId, "update agenda event");
      } else if(AgendaEventService.POST_DELETE_AGENDA_EVENT_EVENT.equals(event.getEventName())) {
        unindexAgendaEvent(eventId, "delete agenda event");
      }
    }
  }

  private void reindexAgendaEvent(String eventId, String cause) {
    IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
    LOG.debug("Notifying indexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.reindex(AgendaIndexingServiceConnector.TYPE, eventId);
  }

  private void unindexAgendaEvent(String eventId, String cause) {
    IndexingService indexingService = CommonsUtils.getService(IndexingService.class);
    LOG.debug("Notifying unindexing service for event with id={}. Cause: {}", eventId, cause);
    indexingService.unindex(AgendaIndexingServiceConnector.TYPE, eventId);
  }

}
