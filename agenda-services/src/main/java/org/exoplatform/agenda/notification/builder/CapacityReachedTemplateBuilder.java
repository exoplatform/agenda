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

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.buildMessageSubjectAndBody;
import static org.exoplatform.agenda.util.NotificationUtils.buildTemplateCapacityReachedParameters;

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
 * Builds the "Event capacity reached" message of one channel: the event's
 * title under the line saying it filled up, and the link that opens it.
 */
public class CapacityReachedTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log       LOG = ExoLogger.getLogger(CapacityReachedTemplateBuilder.class);

  private final TemplateProvider templateProvider;

  private final ExoContainer     container;

  private final PluginKey        key;

  private final boolean          pushNotification;

  /**
   * @param templateProvider the provider of the channel's template
   * @param container the portal container
   * @param key the notification's plugin key
   * @param pushNotification whether the channel is the mobile push, whose
   *          subject is the event's link
   */
  public CapacityReachedTemplateBuilder(TemplateProvider templateProvider,
                                        ExoContainer container,
                                        PluginKey key,
                                        boolean pushNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.key = key;
    this.pushNotification = pushNotification;
  }

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

  @Override
  protected MessageInfo makeMessage(NotificationContext ctx) {
    NotificationInfo notification = ctx.getNotificationInfo();
    RequestLifeCycle.begin(container);
    try {
      TemplateContext templateContext = buildTemplateCapacityReachedParameters(templateProvider, notification);
      String pushNotificationURL = pushNotification ? notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL) : null;
      MessageInfo messageInfo = buildMessageSubjectAndBody(templateContext, notification, pushNotificationURL);
      Throwable exception = templateContext.getException();
      logException(notification, exception);
      ctx.setException(exception);
      return messageInfo;
    } catch (Throwable e) {// NOSONAR handle groovy exceptions of type
                           // java.lang.Error as well
      ctx.setException(e);
      logException(notification, e);
      return null;
    } finally {
      RequestLifeCycle.end();
    }
  }

  private void logException(NotificationInfo notification, Throwable e) {
    if (e != null) {
      if (LOG.isDebugEnabled()) {
        LOG.warn("Error building notification content: {}", notification, e);
      } else {
        LOG.warn("Error building notification content: {}, error: {}", notification, e.getMessage());
      }
    }
  }
}
