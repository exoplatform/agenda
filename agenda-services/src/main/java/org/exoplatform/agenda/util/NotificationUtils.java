package org.exoplatform.agenda.util;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.NotificationMessageUtils;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.*;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.util.*;
import java.util.stream.Collectors;

public class NotificationUtils {

  public static final ArgumentLiteral<Long>   EVENT_ID                                 =
                                                       new ArgumentLiteral<>(Long.class, "event_id");

  public static final ArgumentLiteral<String> EVENT_NAME                               =
                                                         new ArgumentLiteral<>(String.class, "event_name");

  public static final String                  AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN   = "EventAddedNotificationPlugin";

  private static final String                 TEMPLATE_VARIABLE_NOTIFICATION_URL       = "detailsURL";

  public static final PluginKey               EVENT_ADDED_KEY                          =
                                                              PluginKey.key(AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);

  private static final String                 STORED_PARAMETER_EVENT_TITLE             = "eventTitle";

  private static final String                 STORED_PARAMETER_EVENT_ID                = "eventId";

  private static final String                 STORED_PARAMETER_EVENT_START_DATE        = "startDate";

  private static final String                 TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR = "Avatar";

  private static final String                 TEMPLATE_VARIABLE_SUFFIX_IDENTITY_NAME   = "Name";

  private static final String                 TEMPLATE_VARIABLE_SUFFIX_IDENTITY_URL    = "Url";

  private static final String                 TEMPLATE_VARIABLE_EVENT_ID               = "eventId";

  private static final String                 TEMPLATE_VARIABLE_EVENT_TITLE            = "eventTitle";

  private static String                       defaultSite;


  private NotificationUtils() {
  }

  public static final long getEventId(NotificationContext ctx) {
    return ctx.value(EVENT_ID);
  }

  public static final void setEventId(NotificationContext ctx, long eventId) {
    ctx.append(EVENT_ID, eventId);
  }

  public static final void setNotificationRecipients(NotificationInfo notification, List<EventAttendee> eventAttendee) {
    List<String> recipientList = eventAttendee.stream()
                                              .map(attendee -> Utils.getIdentityById(attendee.getIdentityId()).getRemoteId())
                                              .collect(Collectors.toList());
    notification.to(recipientList);
    notification.with("receivers", recipientList.toString());
  }

  public static final void storeEventParameters(NotificationInfo notification, Event event) {
    if (event.getCreatorId() == 0) {
      throw new IllegalStateException("creator is null");
    }
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_START_DATE, String.valueOf(event.getStart()));
  }

  public static final String getNotificationURL(Event event) {
    String currentSite = getDefaultSite();
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    String notificationURL = currentDomain + "portal/" + currentSite + "/agenda?eventId=" + event.getId();
    return notificationURL;
  }

  public static String getDefaultSite() {
    if (defaultSite != null) {
      return defaultSite;
    }
    UserPortalConfigService portalConfig = CommonsUtils.getService(UserPortalConfigService.class);
    defaultSite = portalConfig.getDefaultPortal();
    return defaultSite;
  }

  public static final TemplateContext buildTemplateParameters(TemplateProvider templateProvider,
                                                              NotificationInfo notification,
                                                              String notificationURL) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    setIdentityNameAndAvatar(notification, templateContext, "receiver");
    setEventDetails(templateContext, notification);

    templateContext.put(TEMPLATE_VARIABLE_NOTIFICATION_URL, notificationURL);
    return templateContext;
  }

  public static final MessageInfo buildMessageSubjectAndBody(TemplateContext templateContext,
                                                             NotificationInfo notification,
                                                             String pushNotificationURL) {
    MessageInfo messageInfo = new MessageInfo();
    setMessageSubject(messageInfo, templateContext, getEventTitle(notification), pushNotificationURL);
    setMessageBody(templateContext, messageInfo);
    return messageInfo.end();
  }

  private static final void setEventDetails(TemplateContext templateContext, NotificationInfo notification) {
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
    templateContext.put("USER",notification.getTo());
  }

  private static final void setIdentityNameAndAvatar(NotificationInfo notification,
                                                     TemplateContext templateContext,
                                                     String prefix) {
    String identityId = notification.getValueOwnerParameter(prefix.toUpperCase() + "_ID");
    if (StringUtils.isBlank(identityId)) {
      templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_NAME, "");
      templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR, "");
      templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_URL, "");
      return;
    }
    Identity identity = Utils.getIdentityById(identityId);
    if (identity == null) {
      throw new IllegalStateException("Identity with id " + identityId + " not found, can't send notification");
    }
    String fullName = identity.getProfile().getFullName();
    templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_NAME, fullName);
    templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR, identity.getProfile().getAvatarUrl());
    templateContext.put(prefix + TEMPLATE_VARIABLE_SUFFIX_IDENTITY_URL, identity.getProfile().getUrl());

  }

  private static final void setMessageSubject(MessageInfo messageInfo,
                                              TemplateContext templateContext,
                                              String title,
                                              String pushNotificationURL) {
    if (pushNotificationURL != null) {
      messageInfo.subject(pushNotificationURL);
    } else {
      messageInfo.subject(TemplateUtils.processSubject(templateContext) + ":" + title);
    }
  }

  private static String getEventTitle(NotificationInfo notification) {
    return notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE);
  }

  private static final TemplateContext getTemplateContext(TemplateProvider templateProvider,
                                                          NotificationInfo notification,
                                                          String language) {
    PluginKey pluginKey = notification.getKey();
    String pluginId = pluginKey.getId();
    ChannelKey channelKey = templateProvider.getChannelKey();
    return TemplateContext.newChannelInstance(channelKey, pluginId, language);
  }

  private static final void setMessageBody(TemplateContext templateContext, MessageInfo messageInfo) {

    messageInfo.body(TemplateUtils.processGroovy(templateContext));
  }

  private static final void setFooter(NotificationInfo notification, TemplateContext templateContext) {
    SocialNotificationUtils.addFooterAndFirstName(notification.getTo(), templateContext);
  }

  private static final void setRead(NotificationInfo notification, TemplateContext templateContext) {
    templateContext.put("READ",
                        Boolean.valueOf(notification.getValueOwnerParameter(NotificationMessageUtils.READ_PORPERTY.getKey())) ? "read"
                                                                                                                              : "unread");
  }

  private static final void setNotificationId(NotificationInfo notification, TemplateContext templateContext) {
    templateContext.put("NOTIFICATION_ID", notification.getId());
  }

  private static final void setLasModifiedTime(NotificationInfo notification, TemplateContext templateContext, String language) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(notification.getLastModifiedDate());
    templateContext.put("LAST_UPDATED_TIME",
                        TimeConvertUtils.convertXTimeAgoByTimeServer(cal.getTime(),
                                                                     "EE, dd yyyy",
                                                                     new Locale(language),
                                                                     TimeConvertUtils.YEAR));
  }
}
