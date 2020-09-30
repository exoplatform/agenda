package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import java.util.ArrayList;
import java.util.List;

import static org.exoplatform.agenda.util.NotificationUtils.*;

@Asynchronous
public class EventNotificationListener extends Listener<Long, Boolean> {

  private ExoContainer       container;

  private AgendaEventService eventService;

  public EventNotificationListener(ExoContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Long, Boolean> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      NotificationContext ctx = NotificationContextImpl.cloneInstance();
      long eventId = event.getSource();
      ctx.append(EVENT_ID, eventId);
      ctx.append(EVENT_NAME, getEventService().getEventById(eventId).getSummary());
      dispatch(ctx, AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    } finally {
      RequestLifeCycle.end();
    }
  }

  private void dispatch(NotificationContext ctx, String... pluginId) {
    List<NotificationCommand> commands = new ArrayList<>(pluginId.length);
    for (String p : pluginId) {
      commands.add(ctx.makeCommand(PluginKey.key(p)));
    }

    ctx.getNotificationExecutor().with(commands).execute(ctx);
  }
  
  private AgendaEventService getEventService() {
    if (eventService == null) {
      eventService = container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return eventService;
  }

}
