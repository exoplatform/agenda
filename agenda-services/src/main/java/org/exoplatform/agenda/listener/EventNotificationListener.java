package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import static org.exoplatform.agenda.util.NotificationUtils.EVENT_PARAMETER;

@Asynchronous
public class EventNotificationListener extends Listener<Event, Boolean> {
  private static final Log           LOG = ExoLogger.getLogger(EventNotificationListener.class);

  private ExoContainer               container;

  private AgendaEventService agendaEventService;

  public EventNotificationListener(ExoContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<Event, Boolean> event) throws Exception {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      NotificationContext ctx = NotificationContextImpl.cloneInstance();
      ctx.append(EVENT_PARAMETER, event.getSource());
      String pluginId = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN;
      ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(pluginId))).execute(ctx);
    } finally {
      RequestLifeCycle.end();
    }
  }
}
