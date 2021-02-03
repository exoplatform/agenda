package org.exoplatform.agenda.notification.builder;

import static org.exoplatform.agenda.util.NotificationUtils.*;
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
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.spi.SpaceService;
import java.io.Writer;

public class VoteTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log   LOG = ExoLogger.getLogger(VoteTemplateBuilder.class);

  private AgendaEventService agendaEventService;

  private SpaceService       spaceService;

  private TemplateProvider   templateProvider;

  private ExoContainer       container;

  private boolean            isPushNotification;

  private PluginKey          key;

  public VoteTemplateBuilder(TemplateProvider templateProvider,
                             ExoContainer container,
                             PluginKey key,
                             boolean isPushNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.key = key;
    this.isPushNotification = isPushNotification;
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
      Event event = getEvent(notification);
      String notificationURL = getEventURL(event);
      String pushNotificationURL = isPushNotification ? notificationURL : null;

      TemplateContext templateContext = buildTemplateDatePollParameters(getSpaceService(), templateProvider, notification);
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

  @Override
  protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
    return false;
  }

  public TemplateProvider getTemplateProvider() {
    return templateProvider;
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

  private SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = this.container.getComponentInstanceOfType(SpaceService.class);
    }
    return spaceService;
  }

}
