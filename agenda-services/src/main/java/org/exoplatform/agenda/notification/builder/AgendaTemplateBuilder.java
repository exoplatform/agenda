package org.exoplatform.agenda.notification.builder;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import java.io.Writer;
import static org.exoplatform.agenda.util.NotificationUtils.*;

public class AgendaTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log   LOG = ExoLogger.getLogger(AgendaTemplateBuilder.class);

  private AgendaEventService agendaEventService;

  private TemplateProvider   templateProvider;

  private ExoContainer       container;

  private boolean            isPushNotification;

  private PluginKey          key;

  public AgendaTemplateBuilder(TemplateProvider templateProvider,
                               ExoContainer container,
                               PluginKey key,
                               boolean pushNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.isPushNotification = pushNotification;
    this.key = key;
  }

  @Override
  protected MessageInfo makeMessage(NotificationContext ctx) {
    NotificationInfo notification = ctx.getNotificationInfo();

    RequestLifeCycle.begin(container);
    try {
      Event event = getEvent(notification);
      String notificationURL = getEventURL(event);
      String pushNotificationURL = isPushNotification ? notificationURL : null;

      TemplateContext templateContext = buildTemplateParameters(templateProvider, notification);
      MessageInfo messageInfo = buildMessageSubjectAndBody(templateContext, notification, pushNotificationURL);
      Throwable exception = templateContext.getException();
      logException(notification, exception);
      ctx.setException(exception);
      return messageInfo;
    } catch (Throwable e) {
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

  @Override
  protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
    return false;
  }

  private final Event getEvent(NotificationInfo notification) {
    String eventIdString = notification.getValueOwnerParameter("eventId");
    if (StringUtils.isBlank(eventIdString)) {
      throw new IllegalStateException("Event id is missing in notification");
    }
    long eventId = Long.parseLong(eventIdString);
    if (eventId == 0) {
      throw new IllegalStateException("Event id is equal to 0 in notification");
    }
    return getEventService().getEventById(eventId);
  }

  public AgendaEventService getEventService() {
    if (agendaEventService == null) {
      agendaEventService = CommonsUtils.getService(AgendaEventService.class);
    }
    return agendaEventService;
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
        return null;
      }
    }
  }

  public TemplateProvider getTemplateProvider() {
    return templateProvider;
  }

}
