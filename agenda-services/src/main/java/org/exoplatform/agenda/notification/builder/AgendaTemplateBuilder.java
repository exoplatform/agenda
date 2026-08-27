package org.exoplatform.agenda.notification.builder;

import static org.exoplatform.agenda.util.NotificationUtils.*;
import static org.exoplatform.agenda.util.Utils.generateIcsFile;

import java.io.*;
import java.time.*;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.EventIcsBuilder;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.*;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.mail.Attachment;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;


public class AgendaTemplateBuilder extends AbstractTemplateBuilder {

  private static final Log           LOG = ExoLogger.getLogger(AgendaTemplateBuilder.class);

  private AgendaEventService         agendaEventService;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  private AgendaUserSettingsService  agendaUserSettingsService;

  private SpaceService               spaceService;

  private IdentityManager            identityManager;

  private TemplateProvider           templateProvider;

  private ExoContainer               container;

  private boolean                    isPushNotification;

  private boolean                    isWebNotification;

  private PluginKey                  key;

  /**
   * Builds the notification content of one agenda event plugin on one channel.
   *
   * @param templateProvider provider holding the template paths of the channel
   * @param container container the platform services are read from
   * @param key key of the notification plugin this builder serves
   * @param pushNotification whether the channel is the mobile push one
   * @param webNotification whether the channel is the in-platform web one
   */
  public AgendaTemplateBuilder(TemplateProvider templateProvider,
                               ExoContainer container,
                               PluginKey key,
                               boolean pushNotification,
                               boolean webNotification) {
    this.templateProvider = templateProvider;
    this.container = container;
    this.isPushNotification = pushNotification;
    this.isWebNotification = webNotification;
    this.key = key;
  }

  /**
   * Compiles the Groovy template of this plugin, falling back to an empty
   * template rather than failing the notification when it cannot be read.
   *
   * @return the compiled template, or null when even an empty one fails
   */
  @Override
  public Template getTemplateEngine() {
    String templatePath = null;
    try {
      templatePath = templateProvider.getTemplateFilePathConfigs().get(key);
      String template = TemplateUtils.loadGroovyTemplate(templatePath);
      if (StringUtils.isBlank(template)) {
        LOG.warn("Template not found {}", templatePath);
        return new GStringTemplateEngine().createTemplate("");
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
   * Builds the message one recipient receives on this channel: its subject,
   * its rendered body, and — unless the recipient will hold a synced copy of
   * the same meeting — the {@code event.ics} file.
   *
   * @param ctx notification context carrying the notification to render
   * @return the message, or null when it must not be sent or could not be
   *         built
   */
  @Override
  protected MessageInfo makeMessage(NotificationContext ctx) {
    NotificationInfo notification = ctx.getNotificationInfo();

    RequestLifeCycle.begin(container);
    try {
      Event event = getEvent(notification);
      String eventModificationType = notification.getValueOwnerParameter(STORED_EVENT_MODIFICATION_TYPE);
      if (event == null && !StringUtils.equals(eventModificationType, AgendaEventModificationType.DELETED.name())) {
        return null;
      }
      String notificationURL = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL);
      if (StringUtils.isBlank(notificationURL)) {
        notificationURL = getEventURL(event);
      }

      String pushNotificationURL = isPushNotification ? notificationURL : null;
      String username = notification.getTo();
      long identityId = Utils.getIdentityIdByUsername(getIdentityManager(), username);

      String modifierIdentityId = notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_IDENTITY_ID);

      if((isPushNotification || isWebNotification) && StringUtils.isNotBlank(modifierIdentityId) && modifierIdentityId.equals(String.valueOf(identityId))) {
        return null;
      }
      AgendaUserSettings agendaUserSettings = getAgendaUserSettingsService().getAgendaUserSettings(identityId);
      ZoneId timeZone;
      if (agendaUserSettings != null && agendaUserSettings.getTimeZoneId() != null) {
        timeZone = ZoneId.of(agendaUserSettings.getTimeZoneId());
      } else if (event != null) {
        timeZone = event.getTimeZoneId();
      } else {
        timeZone = ZoneOffset.UTC;
      }

      TemplateContext templateContext = buildTemplateParameters(username,
                                                                getSpaceService(),
                                                                getAgendaEventAttendeeService(),
                                                                templateProvider,
                                                                notification,
                                                                timeZone);
      MessageInfo messageInfo = new MessageInfo();
      if (pushNotificationURL != null) {
        messageInfo.subject(pushNotificationURL);
      } else {
        messageInfo.subject(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));
      }
      messageInfo.body(TemplateUtils.processGroovy(templateContext));

      String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
      String eventSummary = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE);
      String eventDescription = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_DESCRIPTION);
      String startDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_START_DATE);
      String endDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_END_DATE);
      String eventConference = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CONFERENCE);
      String eventModifierId = notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_IDENTITY_ID);
      String eventCreator = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CREATOR);
      String location = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_LOCATION);
      Locale userLocale = Locale.of(Utils.getUserLanguage(notification.getTo()));

      // The file is left out for a recipient who will hold a synced copy of
      // the same meeting: attaching it there is what makes the meeting appear
      // twice in their calendar. The condition is a prediction and the
      // reasoning behind it lives on shouldAttachIcsFile. Applied on every
      // channel, not on mail alone: it is a fact about the recipient, not
      // about the channel, and the mobile-push and web notifications never
      // hand a file to a calendar application anyway — MessageInfo carries
      // its attachments only into the mail Message.
      if (shouldAttachIcsFile(identityId, agendaUserSettings)) {
        Attachment attachment = new Attachment();
        byte[] icsFileBytes = generateIcsFile(notification.getValueOwnerParameter("eventId"),
                                         ownerId,
                                         eventSummary,
                                         eventDescription,
                                         startDateRFC3339,
                                         endDateRFC3339,
                                         eventConference,
                                         eventModifierId,
                                         eventCreator,
                                         location,
                                         mailedEventUrl(notification, identityId),
                                         userLocale,
                                         timeZone);
        attachment.setInputStream(new ByteArrayInputStream(icsFileBytes));
        attachment.setMimeType("text/calendar;charset=utf-8;method=PUBLISH");
        attachment.setName("event.ics");
        messageInfo.addAttachment(attachment);
      }


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

  /**
   * The link the attached document may carry back to the event in eXo — and
   * nothing at all when the recipient has no account there.
   *
   * <p>
   * <b>Not for guests.</b> A guest is invited by mail address and has no eXo
   * account, so this link puts them on a login screen they cannot get past. A
   * door you cannot open is worse than no door, and this is the one channel
   * that can tell the difference: the mail knows exactly who it is going to,
   * where the CalDAV copy only ever lands in the calendar of somebody who has
   * an account by construction. It is a product decision, not a technicality
   * (EXO-89751).
   *
   * <p>
   * The test is the recipient's organization identity id, which is the same
   * signal {@code shouldAttachIcsFile} already reads for the same reason: a
   * guest is an
   * {@link org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider}
   * identity, never an organization one, so it arrives here as 0.
   *
   * @param notification the notification being rendered, carrying the event id
   * @param recipientIdentityId organization identity id of the recipient, 0
   *          when the recipient is a guest
   * @return the absolute link back to the event, or null for a guest and for a
   *         notification that names no event
   */
  private String mailedEventUrl(NotificationInfo notification, long recipientIdentityId) {
    if (recipientIdentityId <= 0) {
      return null;
    }
    return EventIcsBuilder.eventUrl(NumberUtils.toLong(notification.getValueOwnerParameter("eventId"), 0));
  }

  /**
   * Agenda notifications have no digest form.
   *
   * @param notificationContext notification context, unused
   * @param writer writer the digest would be rendered into, unused
   * @return false, always
   */
  @Override
  protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
    return false;
  }

  /**
   * The event a notification is about.
   *
   * @param notification notification carrying the event id
   * @return the event, or null when it no longer exists
   * @throws IllegalStateException when the notification carries no usable
   *           event id
   */
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

  /**
   * Logs a failure met while rendering, with the whole notification only when
   * debug is on: the notification is verbose and this is a warning, not an
   * incident.
   *
   * @param notification notification being rendered
   * @param e the failure, ignored when null
   */
  private void logException(NotificationInfo notification, Throwable e) {
    if (e != null) {
      if (LOG.isDebugEnabled()) {
        LOG.warn("Error building notification content: {}", notification, e);
      } else {
        LOG.warn("Error building notification content: {}, error: {}", notification, e.getMessage());
      }
    }
  }

  /**
   * The event service, resolved from the container on first use.
   *
   * @return the event service
   */
  private AgendaEventService getEventService() {
    if (agendaEventService == null) {
      agendaEventService = this.container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }

  /**
   * The user settings service, resolved from the container on first use.
   *
   * @return the user settings service
   */
  private AgendaUserSettingsService getAgendaUserSettingsService() {
    if (agendaUserSettingsService == null) {
      agendaUserSettingsService = this.container.getComponentInstanceOfType(AgendaUserSettingsService.class);
    }
    return agendaUserSettingsService;
  }

  /**
   * The attendee service, resolved from the container on first use.
   *
   * @return the attendee service
   */
  private AgendaEventAttendeeService getAgendaEventAttendeeService() {
    if (agendaEventAttendeeService == null) {
      agendaEventAttendeeService = this.container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return agendaEventAttendeeService;
  }

  /**
   * The identity manager, resolved from the container on first use.
   *
   * @return the identity manager
   */
  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = this.container.getComponentInstanceOfType(IdentityManager.class);
    }
    return identityManager;
  }

  /**
   * The space service, resolved from the container on first use.
   *
   * @return the space service
   */
  public SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = this.container.getComponentInstanceOfType(SpaceService.class);
    }
    return spaceService;
  }
}
