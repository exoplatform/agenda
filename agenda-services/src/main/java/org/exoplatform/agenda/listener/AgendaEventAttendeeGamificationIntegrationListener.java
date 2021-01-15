package org.exoplatform.agenda.listener;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A listener that will be triggered when a user reply to an event.
 * This will add the user who replied to the event the dedicated
 * gamification points.
 */
@Asynchronous
public class AgendaEventAttendeeGamificationIntegrationListener extends Listener<EventAttendee, EventAttendee> {

  private static final Log LOG = ExoLogger.getLogger(AgendaEventAttendeeGamificationIntegrationListener.class);

  public static final String GAMIFICATION_GENERIC_EVENT = "exo.gamification.generic.action";

  public static final String GAMIFICATION_REPLY_TO_EVENT_RULE_TITLE = "ReplyToEvent";

  public static final String GAMIFICATION_UPDATE_EVENT_RULE_TITLE = "updateEvent";

  public static final String GAMIFICATION_CANCEL_EVENT_RULE_TITLE = "cancelEvent";

  private PortalContainer container;

  private ListenerService listenerService;

  private AgendaEventService agendaEventService;


  public AgendaEventAttendeeGamificationIntegrationListener(PortalContainer container, ListenerService listenerService) {
    this.container = container;
    this.listenerService = listenerService;
  }

  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      EventAttendee oldAttendee = event.getSource();
      EventAttendee newAttendee = event.getData();
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(newAttendee.getEventId());

      if (oldAttendee.getResponse() != newAttendee.getResponse()) {
        String eventURL = NotificationUtils.getEventURL(agendaEvent);
        Long earnerId = oldAttendee.getIdentityId();

        try {
          Map<String, String> gam = new HashMap<>();
          gam.put("ruleTitle", GAMIFICATION_REPLY_TO_EVENT_RULE_TITLE);
          gam.put("object", eventURL);
          gam.put("senderId", String.valueOf(earnerId)); // matches the gamification's earner id
          gam.put("receiverId", String.valueOf(earnerId));
          listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(agendaEvent.getId()));
        } catch (Exception e) {
          LOG.error("Cannot broadcast gamification event");
        }
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
