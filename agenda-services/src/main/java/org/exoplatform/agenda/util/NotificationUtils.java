package org.exoplatform.agenda.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;
import java.util.Calendar;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventModificationType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

public class NotificationUtils {

  private static final Log                           LOG                                        =
                                                         ExoLogger.getLogger(NotificationUtils.class);

  public static final ArgumentLiteral<Event>         EVENT_AGENDA                               =
                                                                  new ArgumentLiteral<>(Event.class, "event_agenda");

  @SuppressWarnings("rawtypes")
  public static final ArgumentLiteral<List>          EVENT_ATTENDEE                             =
                                                                    new ArgumentLiteral<>(List.class, "eventAttendee");

  public static final ArgumentLiteral<String>        EVENT_MODIFICATION_TYPE                    =
                                                                             new ArgumentLiteral<>(String.class,
                                                                                                   "modificationEventType");

  public static final ArgumentLiteral<EventReminder> EVENT_AGENDA_REMINDER                      =
                                                                           new ArgumentLiteral<>(EventReminder.class,
                                                                                                 "event_agenda_reminder");

  public static final String                         AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN     = "EventAddedNotificationPlugin";

  public static final String                         AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN  =
                                                                                               "EventModifiedNotificationPlugin";

  public static final String                         AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN =
                                                                                                "EventCanceledNotificationPlugin";

  public static final String                         AGENDA_REMINDER_NOTIFICATION_PLUGIN        =
                                                                                         "EventReminderNotificationPlugin";

  private static final String                        TEMPLATE_VARIABLE_EVENT_URL                = "eventURL";

  public static final PluginKey                      EVENT_ADDED_KEY                            =
                                                                     PluginKey.key(AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);

  public static final PluginKey                      EVENT_MODIFIED_KEY                         =
                                                                        PluginKey.key(AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);

  public static final PluginKey                      EVENT_CANCELLED_KEY                        =
                                                                         PluginKey.key(AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN);

  public static final PluginKey                      EVENT_REMINDER_KEY                         =
                                                                        PluginKey.key(AGENDA_REMINDER_NOTIFICATION_PLUGIN);

  public static final String                         STORED_PARAMETER_EVENT_TITLE               = "eventTitle";

  public static final String                         STORED_PARAMETER_EVENT_DESCRIPTION         = "eventDescription";

  public static final String                         STORED_PARAMETER_EVENT_LOCATION            = "eventLocation";

  public static final String                         STORED_PARAMETER_EVENT_OWNER_ID            = "ownerId";

  private static final String                        STORED_PARAMETER_EVENT_ID                  = "eventId";

  public static final String                         STORED_PARAMETER_EVENT_MODIFIER            = "eventModifier";

  public static final String                         STORED_PARAMETER_EVENT_CREATOR             = "eventCreator";

  public static final String                         STORED_PARAMETER_EVENT_URL                 = "Url";

  public static final String                         STORED_EVENT_MODIFICATION_TYPE             = "EVENT_MODIFICATION_TYPE";

  public static final String                         STORED_PARAMETER_MODIFIER_IDENTITY_ID      = "MODIFIER_IDENTITY_ID";

  public static final String                         STORED_PARAMETER_EVENT_START_DATE          = "startDate";

  public static final String                         STORED_PARAMETER_EVENT_END_DATE            = "endDate";

  public static final String                         STORED_PARAMETER_EVENT_RESPONSE_TENTATIVE  = "urlAcceptedTentative";

  public static final String                         STORED_PARAMETER_EVENT_ATTENDEES           = "attendees";

  public static final String                         STORED_PARAMETER_EVENT_RECURRENT_DETAILS   = "recurrenceDetails";

  private static final String                        TEMPLATE_VARIABLE_EVENT_START_DATE         = "startDate";

  private static final String                        TEMPLATE_VARIABLE_EVENT_END_DATE           = "endDate";

  private static final String                        TEMPLATE_VARIABLE_EVENT_MONTH_YEAR_DATE    = "monthYearDate";

  private static final String                        TEMPLATE_VARIABLE_AGENDA_NAME              = "agendaName";

  private static final String                        TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR   = "calendarOwnerAvatarUrl";

  public static final String                         TEMPLATE_VARIABLE_EVENT_ID                 = "eventId";

  public static final String                         TEMPLATE_VARIABLE_EVENT_TITLE              = "eventTitle";

  public static final String                         TEMPLATE_VARIABLE_EVENT_LOCATION           = "eventLocation";

  public static final String                         TEMPLATE_VARIABLE_EVENT_DESCRIPTION        = "eventDescription";

  public static final String                         TEMPLATE_VARIABLE_EVENT_RECURRENT_DETAILS  = "recurrenceDetails";

  private static final String                        TEMPLATE_VARIABLE_EVENT_MODIFICATION_TYPE  = "modificationType";

  private static final String                        TEMPLATE_VARIABLE_EVENT_CREATOR            = "creatorName";

  private static final String                        TEMPLATE_VARIABLE_EVENT_ATTENDEES          = "attendees";

  private static final String                        TEMPLATE_VARIABLE_RESPONSE_ACCEPTED        = "acceptedResponseURL";

  private static final String                        TEMPLATE_VARIABLE_RESPONSE_DECLINED        = "declinedResponseURL";

  private static final String                        TEMPLATE_VARIABLE_RESPONSE_TENTATIVE       = "tentativeResponseURL";

  private static final String                        TEMPLATE_VARIABLE_EVENT_MODIFIER           = "modifierName";

  private static final String                        TEMPLATE_VARIABLE_MODIFIER_IDENTITY_URL    = "modifierProfileUrl";

  private static String                              defaultSite;

  private NotificationUtils() {
  }

  public static final long getEventId(NotificationContext ctx) {
    return ctx.value(EVENT_AGENDA).getId();
  }

  public static final long getEventReminderId(NotificationContext ctx) {
    return ctx.value(EVENT_AGENDA_REMINDER).getId();
  }

  public static final void setNotificationRecipients(IdentityManager identityManager,
                                                     NotificationInfo notification,
                                                     SpaceService spaceService,
                                                     List<EventAttendee> eventAttendee,
                                                     Event event,
                                                     String typeModification) {
    Set<String> recipients = new HashSet<>();
    for (EventAttendee attendee : eventAttendee) {
      Identity identity = Utils.getIdentityById(identityManager, attendee.getIdentityId());
      if (identity.getProviderId().equals(SpaceIdentityProvider.NAME)) {
        String spaceName = identity.getRemoteId();
        List<String> memberSpace = Utils.getSpaceMembersBySpaceName(spaceName, spaceService);
        if (memberSpace != null) {
          recipients.addAll(memberSpace);
        }
      } else if (identity.getProviderId().equals(OrganizationIdentityProvider.NAME)) {
        recipients.add(identity.getRemoteId());
      }
    }
    notification.with(STORED_PARAMETER_EVENT_ATTENDEES, String.join(",", recipients));

    // After computing all usernames, to whom, notifications will be sent,
    // this deletes the username of modifier/creator user
    long userIdentityToExclude = StringUtils.equals(typeModification, "ADDED") ? event.getCreatorId() : event.getModifierId();
    if (userIdentityToExclude > 0) {
      Identity identityToExclude = identityManager.getIdentity(String.valueOf(userIdentityToExclude));
      if (identityToExclude != null) {
        recipients.remove(identityToExclude.getRemoteId());
      }
    }
    notification.to(new ArrayList<>(recipients));
  }

  public static final void setEventReminderNotificationRecipients(IdentityManager identityManager,
                                                                  NotificationInfo notification,
                                                                  Long receiverId) {
    Identity identity = Utils.getIdentityById(identityManager, receiverId);
    notification.to(String.valueOf(identity.getRemoteId()));
  }

  public static final void storeEventParameters(IdentityManager identityManager,
                                                NotificationInfo notification,
                                                Event event,
                                                org.exoplatform.agenda.model.Calendar calendar,
                                                String typeModification) {
    if (event == null) {
      throw new IllegalArgumentException("event is null");
    }
    Identity identity = Utils.getIdentityById(identityManager, event.getCreatorId());
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event))
                .with(STORED_PARAMETER_EVENT_CREATOR, getEventNotificationCreatorOrModifierUserName(identity))
                .with(STORED_EVENT_MODIFICATION_TYPE, typeModification)
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date((event.getEnd())))
                .with(STORED_PARAMETER_EVENT_RECURRENT_DETAILS, getRecurrenceDetails(event));

    if (StringUtils.isNotBlank(event.getDescription())) {
      notification.with(STORED_PARAMETER_EVENT_DESCRIPTION, event.getDescription());
    }
    if (StringUtils.isNotBlank(event.getLocation())) {
      notification.with(STORED_PARAMETER_EVENT_LOCATION, event.getLocation());
    }
    if (event.getModifierId() > 0) {
      identity = Utils.getIdentityById(identityManager, event.getModifierId());
      notification.with(STORED_PARAMETER_EVENT_MODIFIER, getEventNotificationCreatorOrModifierUserName(identity))
                  .with(STORED_PARAMETER_MODIFIER_IDENTITY_ID, String.valueOf(event.getModifierId()));
    }
  }

  public static final void storeEventParameters(NotificationInfo notification,
                                                Event event,
                                                org.exoplatform.agenda.model.Calendar calendar) {

    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event))
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date((event.getEnd())));
  }

  public static String getDefaultSite() {
    if (defaultSite != null) {
      return defaultSite;
    }
    UserPortalConfigService portalConfig = CommonsUtils.getService(UserPortalConfigService.class);
    defaultSite = portalConfig.getDefaultPortal();
    return defaultSite;
  }

  public static final TemplateContext buildTemplateParameters(String username,
                                                              AgendaEventAttendeeService agendaEventAttendeeService,
                                                              TemplateProvider templateProvider,
                                                              NotificationInfo notification,
                                                              ZoneId timeZone) {
    String notificationReceiverUserName = notification.getTo();
    String language = NotificationPluginUtils.getLanguage(notificationReceiverUserName);
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    setIdentityNameAndAvatar(notification, templateContext);
    setSpaceName(notification, templateContext);
    setEventDetails(templateContext, notification, timeZone);
    String modificationStoredType = notification.getValueOwnerParameter(STORED_EVENT_MODIFICATION_TYPE);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFICATION_TYPE, modificationStoredType);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_CREATOR, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CREATOR));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ATTENDEES, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ATTENDEES));

    String eventIdString = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID);
    long eventId = Long.parseLong(eventIdString);

    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_ACCEPTED,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.ACCEPTED));
    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_DECLINED,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.DECLINED));
    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_TENTATIVE,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.TENTATIVE));

    if (StringUtils.equals(modificationStoredType, EventModificationType.UPDATED.name())) {
      String identityId = notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_IDENTITY_ID);
      templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFIER, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_MODIFIER));
      templateContext.put(TEMPLATE_VARIABLE_MODIFIER_IDENTITY_URL, getUserAbsoluteURI(identityId));
    }
    return templateContext;
  }

  public static final TemplateContext buildTemplateReminderParameters(TemplateProvider templateProvider,
                                                                      NotificationInfo notification,
                                                                      ZoneId timeZone) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    setIdentityNameAndAvatar(notification, templateContext);
    setEventDetails(templateContext, notification, timeZone);

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

  private static final void setEventDetails(TemplateContext templateContext, NotificationInfo notification, ZoneId timeZone) {
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_LOCATION, getEventLocation(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_DESCRIPTION, getEventDescription(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_RECURRENT_DETAILS,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_RECURRENT_DETAILS));

    String startDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_START_DATE);
    String endDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_END_DATE);

    ZonedDateTime startDate = ZonedDateTime.parse(startDateRFC3339).withZoneSameInstant(timeZone);
    ZonedDateTime endDate = ZonedDateTime.parse(endDateRFC3339).withZoneSameInstant(timeZone);

    String dateFormatted = AgendaDateUtils.formatWithYearAndMonth(startDate);
    String startDateFormatted = AgendaDateUtils.formatWithHoursAndMinutes(startDate);
    String endDateFormatted = AgendaDateUtils.formatWithHoursAndMinutes(endDate);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_START_DATE, startDateFormatted);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_END_DATE, endDateFormatted);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_MONTH_YEAR_DATE, dateFormatted);
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

  public static String getResponseURL(AgendaEventAttendeeService agendaEventAttendeeService,
                                      long eventId,
                                      String username,
                                      EventAttendeeResponse response) {
    String notificationURL = "";
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    if (eventId > 0) {
      String token = agendaEventAttendeeService.generateEncryptedToken(eventId, username, response);
      if (token == null) {
        token = "";
      } else {
        try {
          token = URLEncoder.encode(token, String.valueOf(Charset.defaultCharset()));
        } catch (UnsupportedEncodingException e) {
          LOG.error("Error while encoding the token of events", e);
        }
      }
      notificationURL = currentDomain + "portal/rest/v1/agenda/events/" + eventId
          + "/response/send?response=" + response.name() + "&token=" + token + "&redirect=true";
    }
    return notificationURL;
  }

  public static String getRecurrenceDetails(Event event) {
    EventRecurrence eventRecurrence = event.getRecurrence();
    if (eventRecurrence != null && eventRecurrence.getFrequency() != null) {
      switch (eventRecurrence.getFrequency().name()) {
        case "DAILY":
          if (eventRecurrence.getInterval() == 1) {
            return "daily";
          } else {
            return "Each " + eventRecurrence.getInterval() + "days";
          }
        case "WEEKLY":
          if (eventRecurrence.getInterval() == 1 && event.getRecurrence().getByDay().size() == 1) {
            String dayNumber = eventRecurrence.getByDay().get(0);
            DayOfWeek dayName = DayOfWeek.of(Integer.parseInt(dayNumber));
            return "Weekly on " + StringUtils.lowerCase(String.valueOf(dayName)) + "";
          } else if (eventRecurrence.getInterval() == 1 && eventRecurrence.getByDay().size() > 1) {
            List<String> dayNamesAbbreviations = eventRecurrence.getByDay();
            return "Weekly on " + AgendaDateUtils.getDayNameFromDayAbbreviation(dayNamesAbbreviations);
          } else if (eventRecurrence.getByDay().size() == 1) {
            String dayNumber = eventRecurrence.getByDay().get(0);
            DayOfWeek dayName = DayOfWeek.of(Integer.parseInt(dayNumber));
            return "Each Week " + eventRecurrence.getInterval() + " on " + StringUtils.lowerCase(String.valueOf(dayName));
          } else {
            List<String> dayNamesAbbreviations = eventRecurrence.getByDay();
            return "Each Week " + eventRecurrence.getInterval() + " on "
                + AgendaDateUtils.getDayNameFromDayAbbreviation(dayNamesAbbreviations);
          }
        case "MONTHLY":
          if (eventRecurrence.getInterval() == 1) {
            String dayNumberMonth = eventRecurrence.getByMonthDay().get(0);
            return " Monthly on " + dayNumberMonth;
          } else {
            String dayNumberMonth = eventRecurrence.getByMonthDay().get(0);
            return " Each " + eventRecurrence.getInterval() + " month on " + dayNumberMonth;
          }
        case "YEARLY":
          if (eventRecurrence.getInterval() == 1) {
            String dayNumberInMonth = eventRecurrence.getByMonthDay().get(0);
            String monthNumber = eventRecurrence.getByMonth().get(0);
            Month monthName = Month.of(Integer.parseInt(monthNumber));
            return " Yearly on " + StringUtils.lowerCase(String.valueOf(monthName)) + dayNumberInMonth;
          } else {
            String dayNumberInMonth = eventRecurrence.getByMonthDay().get(0);
            String monthNumber = eventRecurrence.getByMonth().get(0);
            Month monthName = Month.of(Integer.parseInt(monthNumber));
            return " Each " + eventRecurrence.getInterval() + " years on " + StringUtils.lowerCase(String.valueOf(monthName))
                + " "
                + dayNumberInMonth;
          }
        default:
          return "";
      }
    } else {
      return "";
    }
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

  private static final void setSpaceName(NotificationInfo notification, TemplateContext templateContext) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(ownerId);
    if (identity != null) {
      String spaceName = identity.getRemoteId();
      templateContext.put(TEMPLATE_VARIABLE_AGENDA_NAME, spaceName);
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

  private static String getEventLocation(NotificationInfo notification) {
    String eventLocation = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_LOCATION);
    if (StringUtils.isNotBlank(eventLocation)) {
      return eventLocation;
    } else {
      return "";
    }
  }

  private static String getEventDescription(NotificationInfo notification) {
    String eventDescription = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_DESCRIPTION);
    if (StringUtils.isNotBlank(eventDescription)) {
      return eventDescription;
    } else {
      return "";
    }
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

  private static final String getEventNotificationCreatorOrModifierUserName(Identity identity) {
    return identity.getProfile().getFullName();
  }

  private static String getUserAbsoluteURI(String identityId) {
    String currentSite = CommonsUtils.getCurrentSite().getName();
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    return currentDomain + "portal/" + currentSite + "/profile/" + identityId;
  }

}
