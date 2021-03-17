package org.exoplatform.agenda.notification.provider;

import org.exoplatform.agenda.notification.builder.*;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.xml.InitParams;

import static org.exoplatform.agenda.util.NotificationUtils.*;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/EventWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/EventWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/EventWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_REMINDER_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/EventReminderWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_REPLY_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/EventReplyWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_DATE_POLL_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/DatePollWebPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_VOTE_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/web/VoteWebPlugin.gtmpl")}
    )
public class WebTemplateProvider extends TemplateProvider {
  public WebTemplateProvider(ExoContainer container, InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(EVENT_ADDED_KEY, new AgendaTemplateBuilder(this, container, EVENT_ADDED_KEY, false));
    this.templateBuilders.put(EVENT_MODIFIED_KEY, new AgendaTemplateBuilder(this, container, EVENT_MODIFIED_KEY, false));
    this.templateBuilders.put(EVENT_CANCELLED_KEY, new AgendaTemplateBuilder(this, container, EVENT_CANCELLED_KEY, false));
    this.templateBuilders.put(EVENT_REMINDER_KEY, new ReminderTemplateBuilder(this, container, EVENT_REMINDER_KEY, false));
    this.templateBuilders.put(EVENT_REPLY_KEY, new ReplyTemplateBuilder(this, container, EVENT_REPLY_KEY, false));
    this.templateBuilders.put(EVENT_DATE_POLL_KEY, new DatePollNotificationBuilder(this, container, EVENT_DATE_POLL_KEY, false));
    this.templateBuilders.put(EVENT_DATE_VOTE_KEY, new VoteTemplateBuilder(this, container, EVENT_DATE_VOTE_KEY, false));
  }
}
