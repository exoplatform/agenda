package org.exoplatform.agenda.notification.provider;

import org.exoplatform.agenda.notification.builder.AgendaTemplateBuilder;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.xml.InitParams;

import static org.exoplatform.agenda.util.NotificationUtils.AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN;
import static org.exoplatform.agenda.util.NotificationUtils.EVENT_ADDED_KEY;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/mail/EventMailPlugin.gtmpl") })
public class MailTemplateProvider extends TemplateProvider {
  public MailTemplateProvider(ExoContainer container, InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(EVENT_ADDED_KEY, new AgendaTemplateBuilder(this, container, EVENT_ADDED_KEY, false));
  }
}
