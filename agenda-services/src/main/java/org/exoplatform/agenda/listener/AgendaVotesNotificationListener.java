package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

public class AgendaVotesNotificationListener extends Listener<Long, Long> {

  private PortalContainer            container;

  private AgendaEventService         agendaEventService;

  private NotificationService notificationService;

  public AgendaVotesNotificationListener(PortalContainer container, NotificationService notificationService) {
    this.container = container;
    this.notificationService = notificationService;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      long eventId = event.getSource();
      long identityId = event.getData();
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      if (agendaEvent.getCreatorId() != identityId) {
        sendVoteNotification(agendaEvent, identityId, EventAttendeeResponse.TENTATIVE);
      }
    } finally {
      RequestLifeCycle.end();
    }
  }

  public void sendVoteNotification(org.exoplatform.agenda.model.Event event, long participantId, EventAttendeeResponse response) {
    NotificationContext ctx = notificationService.createNotificationContextInstance();
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
