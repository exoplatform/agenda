package org.exoplatform.agenda.notification.provider;

import org.exoplatform.agenda.notification.builder.AgendaTemplateBuilder;
import org.exoplatform.agenda.notification.builder.ReminderTemplateBuilder;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.xml.InitParams;

import static org.exoplatform.agenda.util.NotificationUtils.*;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_CANCELED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_REMINDER_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventReminderPushPlugin.gtmpl")})
public class MobilePushTemplateProvider extends TemplateProvider {
  public MobilePushTemplateProvider(ExoContainer container, InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(EVENT_ADDED_KEY, new AgendaTemplateBuilder(this, container, EVENT_ADDED_KEY, true));
    this.templateBuilders.put(EVENT_MODIFIED_KEY, new AgendaTemplateBuilder(this, container, EVENT_MODIFIED_KEY, true));
    this.templateBuilders.put(EVENT_CANCELED_KEY, new AgendaTemplateBuilder(this, container, EVENT_CANCELED_KEY, true));
    this.templateBuilders.put(EVENT_REMINDER_KEY, new ReminderTemplateBuilder(this, container, EVENT_REMINDER_KEY, true));
  }
}
