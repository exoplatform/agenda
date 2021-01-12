package org.exoplatform.agenda.listener;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;

import java.util.HashMap;
import java.util.Map;

/**
 * A listener that will be triggered when an event is created.
 * This will add the user who created the event the dedicated gamification
 * points.
 */
@Asynchronous
public class AgendaGamificationIntegrationListener extends Listener<Long, Long> {

  private static final Log LOG = ExoLogger.getLogger(AgendaGamificationIntegrationListener.class);

  public static final String GAMIFICATION_GENERIC_EVENT = "exo.gamification.generic.action";

  public static final String GAMIFICATION_CREATE_EVENT_RULE_TITLE = "createEvent";

  public static final String GAMIFICATION_UPDATE_EVENT_RULE_TITLE = "updateEvent";

  public static final String GAMIFICATION_CANCEL_EVENT_RULE_TITLE = "cancelEvent";

  private PortalContainer container;

  private ListenerService listenerService;

  private AgendaEventService agendaEventService;


  public AgendaGamificationIntegrationListener(PortalContainer container, ListenerService listenerService) {
    this.container = container;
    this.listenerService = listenerService;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      String eventName = event.getEventName();
      Long eventId = event.getSource();
      Long earnerId = event.getData();
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      String eventURL = NotificationUtils.getEventURL(agendaEvent);
      String ruleTitle = "";
      if (StringUtils.equals(eventName, Utils.POST_CREATE_AGENDA_EVENT_EVENT)) {
        ruleTitle = GAMIFICATION_CREATE_EVENT_RULE_TITLE;
      } else if (StringUtils.equals(eventName, Utils.POST_UPDATE_AGENDA_EVENT_EVENT)) {
        ruleTitle = GAMIFICATION_UPDATE_EVENT_RULE_TITLE;
      } else if (StringUtils.equals(eventName, Utils.POST_DELETE_AGENDA_EVENT_EVENT)) {
        ruleTitle = GAMIFICATION_CANCEL_EVENT_RULE_TITLE;
        eventURL = "";
      }
      try {
        Map<String, String> gam = new HashMap<>();

        gam.put("ruleTitle", ruleTitle);
        gam.put("object", eventURL);
        gam.put("senderId", String.valueOf(earnerId)); // matches the gamification's earner id
        gam.put("receiverId", String.valueOf(earnerId));
        listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(eventId));
      } catch (Exception e) {
        LOG.error("Cannot broadcast gamification event");
      }

    } finally {
      RequestLifeCycle.end();
    }
  }

  public AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }
}
