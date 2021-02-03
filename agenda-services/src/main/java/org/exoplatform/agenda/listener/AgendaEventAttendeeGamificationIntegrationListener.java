package org.exoplatform.agenda.listener;

import java.util.HashMap;
import java.util.Map;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * A listener that will be triggered when a user reply to an event. This will
 * add the user who replied to the event the dedicated gamification points.
 */
@Asynchronous
public class AgendaEventAttendeeGamificationIntegrationListener extends Listener<EventAttendee, EventAttendee> {

  private static final Log   LOG                                    =
                                 ExoLogger.getLogger(AgendaEventAttendeeGamificationIntegrationListener.class);

  public static final String GAMIFICATION_GENERIC_EVENT             = "exo.gamification.generic.action";

  public static final String GAMIFICATION_REPLY_TO_EVENT_RULE_TITLE = "ReplyToEvent";

  private PortalContainer    container;

  private ListenerService    listenerService;

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
      EventAttendeeResponse oldResponse = oldAttendee == null ? null : oldAttendee.getResponse();
      EventAttendeeResponse newResponse = newAttendee.getResponse();

      if (oldResponse != newResponse) {
        String eventURL = NotificationUtils.getEventURL(agendaEvent);
        Long earnerId = newAttendee.getIdentityId();

        try {
          Map<String, String> gam = new HashMap<>();
          gam.put("ruleTitle", GAMIFICATION_REPLY_TO_EVENT_RULE_TITLE);
          gam.put("object", eventURL);
          gam.put("senderId", String.valueOf(earnerId)); // matches the
                                                         // gamification's
                                                         // earner id
          gam.put("receiverId", String.valueOf(earnerId));
          listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(agendaEvent.getId()));
        } catch (Exception e) {
          LOG.error("Cannot broadcast gamification event", e);
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
