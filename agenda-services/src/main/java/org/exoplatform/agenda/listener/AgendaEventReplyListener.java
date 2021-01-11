package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

@Asynchronous
public class AgendaEventReplyListener extends Listener<EventAttendee, EventAttendee> {
  private AgendaEventService agendaEventService;

  private ExoContainer       container;

  public AgendaEventReplyListener(ExoContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      EventAttendee oldAttendee = event.getSource();
      EventAttendee newAttendee = event.getData();
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(oldAttendee.getEventId());
      if (oldAttendee.getResponse() != newAttendee.getResponse() && agendaEvent.getCreatorId() != oldAttendee.getIdentityId()) {
        sendReplyResponseNotification(agendaEvent, oldAttendee.getIdentityId(), newAttendee.getResponse());
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

  public void sendReplyResponseNotification(org.exoplatform.agenda.model.Event event,
                                             long participantId,
                                             EventAttendeeResponse response) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationUtils.EVENT_AGENDA, event);
    ctx.append(NotificationUtils.EVENT_PARTICIPANT_ID, participantId);
    ctx.append(NotificationUtils.EVENT_RESPONSE, response);
    ctx.getNotificationExecutor()
       .with(ctx.makeCommand(PluginKey.key(NotificationUtils.AGENDA_REPLY_NOTIFICATION_PLUGIN)))
       .execute(ctx);
  }
}
