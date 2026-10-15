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

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_ID;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_IS_GUEST;
import static org.exoplatform.agenda.util.NotificationUtils.buildMessageSubjectAndBody;
import static org.exoplatform.agenda.util.NotificationUtils.buildTemplateParticipationConfirmedParameters;

import java.time.ZoneId;
import java.time.ZoneOffset;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.agenda.util.Utils;
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
import org.exoplatform.social.core.manager.IdentityManager;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;

/**
 * Builds the "Participation confirmation" message of one channel. A guest
 * without an account is told by mail only, the channel the invitation reached
 * them through: the other channels build nothing for them.
 */
public class ParticipationConfirmedTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log          LOG = ExoLogger.getLogger(ParticipationConfirmedTemplateBuilder.class);

  private final TemplateProvider    templateProvider;

  private final ExoContainer        container;

  private final PluginKey           key;

  private final boolean             pushNotification;

  private final boolean             mailNotification;

  private AgendaEventService        agendaEventService;

  private AgendaUserSettingsService agendaUserSettingsService;

  private IdentityManager           identityManager;

  /**
   * @param templateProvider the provider of the channel's template
   * @param container the portal container
   * @param key the notification's plugin key
   * @param pushNotification whether the channel is the mobile push, whose
   *          subject is the event's link
   * @param mailNotification whether the channel is the mail, the only one a
   *          guest is told through
   */
  public ParticipationConfirmedTemplateBuilder(TemplateProvider templateProvider,
                                               ExoContainer container,
                                               PluginKey key,
                                               boolean pushNotification,
                                               boolean mailNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.key = key;
    this.pushNotification = pushNotification;
    this.mailNotification = mailNotification;
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
    boolean guest = Boolean.parseBoolean(notification.getValueOwnerParameter(STORED_PARAMETER_IS_GUEST));
    if (guest && !mailNotification) {
      return null;
    }
    RequestLifeCycle.begin(container);
    try {
      Event event = getEventService().getEventById(NumberUtils.toLong(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID)));
      if (event == null) {
        return null;
      }
      TemplateContext templateContext = buildTemplateParticipationConfirmedParameters(templateProvider,
                                                                                      notification,
                                                                                      recipientZone(notification, event, guest));
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

  public TemplateProvider getTemplateProvider() {
    return templateProvider;
  }

  /**
   * @return the zone a user set in their agenda settings, else the event's: a
   *         guest has no settings
   */
  private ZoneId recipientZone(NotificationInfo notification, Event event, boolean guest) {
    if (!guest) {
      long identityId = Utils.getIdentityIdByUsername(getIdentityManager(), notification.getTo());
      AgendaUserSettings agendaUserSettings = identityId > 0 ? getAgendaUserSettingsService().getAgendaUserSettings(identityId)
                                                             : null;
      if (agendaUserSettings != null && agendaUserSettings.getTimeZoneId() != null) {
        return ZoneId.of(agendaUserSettings.getTimeZoneId());
      }
    }
    return event.getTimeZoneId() == null ? ZoneOffset.UTC : event.getTimeZoneId();
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

  private AgendaEventService getEventService() {
    if (agendaEventService == null) {
      agendaEventService = this.container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private AgendaUserSettingsService getAgendaUserSettingsService() {
    if (agendaUserSettingsService == null) {
      agendaUserSettingsService = this.container.getComponentInstanceOfType(AgendaUserSettingsService.class);
    }
    return agendaUserSettingsService;
  }

  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = this.container.getComponentInstanceOfType(IdentityManager.class);
    }
    return identityManager;
  }
}
