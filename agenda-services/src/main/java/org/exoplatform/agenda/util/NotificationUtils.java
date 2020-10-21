package org.exoplatform.agenda.util;

import java.util.*;

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
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

public class NotificationUtils {

  public static final ArgumentLiteral<Long>    EVENT_ID                                  =
                                                        new ArgumentLiteral<>(Long.class, "event_id");

  public static final ArgumentLiteral<Boolean> IS_NEW                                    =
                                                      new ArgumentLiteral<>(Boolean.class, "isNew");

  public static final String                   AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN    = "EventAddedNotificationPlugin";

  public static final String                   AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN = "EventModifiedNotificationPlugin";

  private static final String                  TEMPLATE_VARIABLE_EVENT_URL               = "eventURL";

  public static final PluginKey                EVENT_ADDED_KEY                           =
                                                               PluginKey.key(AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);

  public static final PluginKey                EVENT_MODIFIED_KEY                        =
                                                                  PluginKey.key(AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);

  public static final String                   STORED_PARAMETER_EVENT_TITLE              = "eventTitle";

  public static final String                   STORED_PARAMETER_EVENT_OWNER_ID           = "ownerId";

  private static final String                  STORED_PARAMETER_EVENT_ID                 = "eventId";

  public static final String                   STORED_PARAMETER_EVENT_START_DATE         = "startDate";

  public static final String                   STORED_PARAMETER_EVENT_END_DATE           = "endDate";

  public static final String                   STORED_PARAMETER_EVENT_URL                = "Url";

  public static final String                   STORED_PARAMETER_EVENT_IS_NEW             = "EVENT_IS_NEW";

  private static final String                  TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR  = "calendarOwnerAvatarUrl";

  public static final String                   TEMPLATE_VARIABLE_EVENT_ID                = "eventId";

  public static final String                   TEMPLATE_VARIABLE_EVENT_TITLE             = "eventTitle";

  private static final String                  TEMPLATE_VARIABLE_EVENT_IS_NEW            = "isNewEvent";

  private static String                        defaultSite;

  private NotificationUtils() {
  }

  public static final long getEventId(NotificationContext ctx) {
    return ctx.value(EVENT_ID);
  }

  public static final void setEventId(NotificationContext ctx, long eventId) {
    ctx.append(EVENT_ID, eventId);
  }

  public static final void setNotificationRecipients(IdentityManager identityManager,
                                                     NotificationInfo notification,
                                                     List<EventAttendee> eventAttendee,
                                                     Event event,
                                                     boolean isNew) {
    Set<String> recipients = new HashSet<>();
    for (EventAttendee attendee : eventAttendee) {
      Identity identity = Utils.getIdentityById(identityManager, attendee.getIdentityId());
      if (identity.getProviderId().equals(SpaceIdentityProvider.NAME)) {
        String spaceName = identity.getRemoteId();
        List<String> memberSpace = Utils.getSpaceMembersBySpaceName(spaceName);
        if (memberSpace != null) {
          recipients.addAll(memberSpace);
        }
      } else if (identity.getProviderId().equals(OrganizationIdentityProvider.NAME)) {
        recipients.add(identity.getRemoteId());
      }
    }

    // After computing all usernames, to whom, notifications will be sent,
    // this deletes the username of modifier/creator user
    long userIdentityToExclude = isNew ? event.getCreatorId() : event.getModifierId();
    if (userIdentityToExclude > 0) {
      Identity identityToExclude = identityManager.getIdentity(String.valueOf(userIdentityToExclude));
      if (identityToExclude != null) {
        recipients.remove(identityToExclude.getRemoteId());
      }
    }
    notification.to(new ArrayList<>(recipients));
  }

  public static final void storeEventParameters(NotificationInfo notification,
                                                Event event,
                                                org.exoplatform.agenda.model.Calendar calendar,
                                                boolean isNew) {
    if (event == null) {
      throw new IllegalArgumentException("event is null");
    }
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event))
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date(event.getEnd()))
                .with(STORED_PARAMETER_EVENT_IS_NEW, String.valueOf(isNew));
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
                                                              NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    setIdentityNameAndAvatar(notification, templateContext);
    setEventDetails(templateContext, notification);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_IS_NEW, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_IS_NEW));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
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
    templateContext.put("USER", notification.getTo());
  }

  public static String getEventURL(Event event) {
    String currentSite = getDefaultSite();
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    String notificationURL = "";
    if (event != null) {
      notificationURL = currentDomain + "portal/" + currentSite + "/agenda?eventId=" + event.getId();
    } else {
      notificationURL = currentDomain + "portal/" + currentSite + "/agenda";
    }
    return notificationURL;
  }

  private static final void setIdentityNameAndAvatar(NotificationInfo notification, TemplateContext templateContext) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(ownerId);
    if (identity != null) {
      String avatarUrl = identity.getProfile().getAvatarUrl();
      templateContext.put(TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR, avatarUrl);
    }
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
    Boolean isRead = Boolean.valueOf(notification.getValueOwnerParameter(NotificationMessageUtils.READ_PORPERTY.getKey()));
    templateContext.put("READ", isRead != null && isRead.booleanValue() ? "read" : "unread");
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
