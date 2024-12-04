/*
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.notification.provider;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import org.exoplatform.agenda.notification.builder.*;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.xml.InitParams;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_REMINDER_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventReminderPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_REPLY_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/EventReplyPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_DATE_POLL_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/DatePollPushPlugin.gtmpl"),
    @TemplateConfig(pluginId = AGENDA_VOTE_NOTIFICATION_PLUGIN, template = "war:/conf/agenda/templates/notification/push/VotePushPlugin.gtmpl")}
    )
public class WebTemplateProvider extends TemplateProvider {
  public WebTemplateProvider(ExoContainer container, InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(EVENT_ADDED_KEY, new AgendaTemplateBuilder(this, container, EVENT_ADDED_KEY, false, true));
    this.templateBuilders.put(EVENT_MODIFIED_KEY, new AgendaTemplateBuilder(this, container, EVENT_MODIFIED_KEY, false, true));
    this.templateBuilders.put(EVENT_CANCELLED_KEY, new AgendaTemplateBuilder(this, container, EVENT_CANCELLED_KEY, false, true));
    this.templateBuilders.put(EVENT_REMINDER_KEY, new ReminderTemplateBuilder(this, container, EVENT_REMINDER_KEY, false));
    this.templateBuilders.put(EVENT_REPLY_KEY, new ReplyTemplateBuilder(this, container, EVENT_REPLY_KEY, false));
    this.templateBuilders.put(EVENT_DATE_POLL_KEY, new DatePollNotificationBuilder(this, container, EVENT_DATE_POLL_KEY, false));
    this.templateBuilders.put(EVENT_DATE_VOTE_KEY, new VoteTemplateBuilder(this, container, EVENT_DATE_VOTE_KEY, false));
  }
}
