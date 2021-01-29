package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;

/**
 * Listener that is triggered when a user sends a vote on an event. Once voted,
 * the response type of the user will be changed from NEEDS_ACTION to TENTATIVE
 * to determine whether the user has voted on the event before or not.
 */
@Asynchronous
public class AgendaVotesListener extends Listener<Long, Long> {

  private PortalContainer            container;

  private AgendaEventAttendeeService attendeeService;

  private AgendaEventService         agendaEventService;

  public AgendaVotesListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      long eventId = event.getSource();
      long identityId = event.getData();
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      getAttendeeService().sendEventResponse(eventId, identityId, EventAttendeeResponse.TENTATIVE);
      if (agendaEvent.getCreatorId() != identityId) {
        sendVoteNotification(agendaEvent, identityId, EventAttendeeResponse.TENTATIVE);
      }
    } finally {
      RequestLifeCycle.end();
    }
  }

  private AgendaEventAttendeeService getAttendeeService() {
    if (attendeeService == null) {
      attendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return attendeeService;
  }

  public void sendVoteNotification(org.exoplatform.agenda.model.Event event, long participantId, EventAttendeeResponse response) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationUtils.EVENT_AGENDA, event);
    ctx.append(NotificationUtils.EVENT_PARTICIPANT_ID, participantId);
    ctx.append(NotificationUtils.EVENT_RESPONSE, response);
    ctx.getNotificationExecutor()
       .with(ctx.makeCommand(PluginKey.key(NotificationUtils.AGENDA_VOTE_NOTIFICATION_PLUGIN)))
       .execute(ctx);
  }

  public AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

}
