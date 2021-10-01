// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * A listener that will be triggered when an event is created or updated. This
 * will add the user who created or updated the event the dedicated gamification
 * points.
 */
@Asynchronous
public class AgendaEventGamificationIntegrationListener extends Listener<Object, Object> {

  private static final Log   LOG                                      =
                                 ExoLogger.getLogger(AgendaEventGamificationIntegrationListener.class);

  public static final String GAMIFICATION_GENERIC_EVENT               = "exo.gamification.generic.action";

  public static final String GAMIFICATION_CREATE_EVENT_RULE_TITLE     = "CreateEvent";

  public static final String GAMIFICATION_UPDATE_EVENT_RULE_TITLE     = "UpdateEvent";

  public static final String GAMIFICATION_CREATE_DATE_POLL_RULE_TITLE = "CreateDatePoll";

  public static final String GAMIFICATION_VOTE_RULE_TITLE             = "VoteDatePoll";

  private PortalContainer    container;

  private ListenerService    listenerService;

  private AgendaEventService agendaEventService;

  public AgendaEventGamificationIntegrationListener(PortalContainer container, ListenerService listenerService) {
    this.container = container;
    this.listenerService = listenerService;
  }

  @Override
  public void onEvent(Event<Object, Object> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      String eventName = event.getEventName();
      Long eventId = null;
      Long earnerId = null;
      if (event.getSource() instanceof AgendaEventModification) {
        AgendaEventModification eventModification = (AgendaEventModification) event.getSource();
        eventId = eventModification.getEventId();
        earnerId = eventModification.getModifierId();
      } else if (event.getSource() instanceof Long) {
        eventId = (Long) event.getSource();
        earnerId = (Long) event.getData();
      }
      if (earnerId == null || eventId == null) {
        LOG.warn("Gamification points for event {} aren't collected because empty inputs: eventId: {}, modifierId: {}",
                 event.getEventName(),
                 eventId,
                 earnerId);
        return;
      }
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      String eventURL = NotificationUtils.getEventURL(agendaEvent);
      String ruleTitle = "";
      if (agendaEvent.getStatus() == EventStatus.TENTATIVE) {
        if (StringUtils.equals(eventName, Utils.POST_CREATE_AGENDA_EVENT_POLL)) {
          ruleTitle = GAMIFICATION_CREATE_DATE_POLL_RULE_TITLE;
          try {
            Map<String, String> gam = new HashMap<>();
            gam.put("ruleTitle", ruleTitle);
            gam.put("object", eventURL);
            gam.put("senderId", String.valueOf(earnerId)); // matches the
                                                           // gamification's
                                                           // earner id
            gam.put("receiverId", String.valueOf(earnerId));
            listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(eventId));
          } catch (Exception e) {
            LOG.error("Cannot broadcast gamification event", e);
          }
        } else if (StringUtils.equals(eventName, Utils.POST_VOTES_AGENDA_EVENT_POLL)) {
          ruleTitle = GAMIFICATION_VOTE_RULE_TITLE;
          try {
            Map<String, String> gam = new HashMap<>();
            gam.put("ruleTitle", ruleTitle);
            gam.put("object", eventURL);
            gam.put("senderId", String.valueOf(earnerId)); // matches the
                                                           // gamification's
                                                           // earner id
            gam.put("receiverId", String.valueOf(earnerId));
            listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(eventId));
          } catch (Exception e) {
            LOG.error("Cannot broadcast gamification event", e);
          }
        } else {
          LOG.warn("Not recognized event name {} for Agenda Poll gamification points collection. eventId: {}, modifierId: {}",
                   event.getEventName(),
                   eventId,
                   earnerId);
        }
      } else if (agendaEvent.getStatus() == EventStatus.CONFIRMED) {
        if (StringUtils.equals(eventName, Utils.POST_CREATE_AGENDA_EVENT_EVENT)) {
          ruleTitle = GAMIFICATION_CREATE_EVENT_RULE_TITLE;
        } else if (StringUtils.equals(eventName, Utils.POST_UPDATE_AGENDA_EVENT_EVENT)) {
          ruleTitle = GAMIFICATION_UPDATE_EVENT_RULE_TITLE;
        } else {
          LOG.warn("Not recognized event name {} for Agenda Event gamification points collection. eventId: {}, modifierId: {}",
                   event.getEventName(),
                   eventId,
                   earnerId);
          return;
        }

        try {
          Map<String, String> gam = new HashMap<>();
          gam.put("ruleTitle", ruleTitle);
          gam.put("object", eventURL);
          gam.put("senderId", String.valueOf(earnerId)); // matches the
                                                         // gamification's
                                                         // earner id
          gam.put("receiverId", String.valueOf(earnerId));
          listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, String.valueOf(eventId));
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
