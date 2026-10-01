/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.notification.builder;

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.buildTemplateCalendarSharedParameters;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;

/**
 * Renders the "a calendar was shared with you" notification (EXO-90357), for
 * the mail, on-site and push channels alike: everything the template needs
 * was stored on the notification when it was made, so nothing is read again
 * here.
 */
public class CalendarSharedTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log       LOG = ExoLogger.getLogger(CalendarSharedTemplateBuilder.class);

  private final TemplateProvider templateProvider;

  private final ExoContainer     container;

  private final PluginKey        key;

  private final boolean          pushNotification;

  /**
   * Builds the builder.
   *
   * @param templateProvider the channel's provider, holding the template path
   * @param container the portal container
   * @param key the plugin key
   * @param pushNotification whether the subject carries the link, as a push
   *          does
   */
  public CalendarSharedTemplateBuilder(TemplateProvider templateProvider,
                                       ExoContainer container,
                                       PluginKey key,
                                       boolean pushNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.key = key;
    this.pushNotification = pushNotification;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Template getTemplateEngine() {
    String templatePath = null;
    try {
      templatePath = templateProvider.getTemplateFilePathConfigs().get(key);
      String template = TemplateUtils.loadGroovyTemplate(templatePath);
      if (StringUtils.isBlank(template)) {
        throw new IllegalStateException("Template with path " + templatePath + " wasn't found");
      }
      return new GStringTemplateEngine().createTemplate(template);
    } catch (Exception e) {
      LOG.warn("Error while compiling template {}", templatePath, e);
      try {
        return new GStringTemplateEngine().createTemplate("");
      } catch (Exception e1) {
        LOG.warn("Error while creating empty template", e1);
        return null;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected MessageInfo makeMessage(NotificationContext ctx) {
    NotificationInfo notification = ctx.getNotificationInfo();
    RequestLifeCycle.begin(container);
    try {
      TemplateContext templateContext = buildTemplateCalendarSharedParameters(templateProvider, notification);
      MessageInfo messageInfo = new MessageInfo();
      String url = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL);
      if (pushNotification) {
        messageInfo.subject(url);
      } else {
        messageInfo.subject(TemplateUtils.processSubject(templateContext) + ":"
            + StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_CALENDAR_NAME)));
      }
      messageInfo.body(TemplateUtils.processGroovy(templateContext));
      Throwable exception = templateContext.getException();
      if (exception != null) {
        LOG.warn("Error building notification content: {}", notification, exception);
      }
      ctx.setException(exception);
      return messageInfo.end();
    } catch (Throwable e) { // NOSONAR handle groovy exceptions of type java.lang.Error as well
      ctx.setException(e);
      LOG.warn("Error building notification content: {}", notification, e);
      return null;
    } finally {
      RequestLifeCycle.end();
    }
  }

}
