package org.exoplatform.agenda.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
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
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.notification.LinkProviderUtils;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

public class NotificationUtils {

  private static final Log                                   LOG                                            =
                                                                 ExoLogger.getLogger(NotificationUtils.class);

  public static final ArgumentLiteral<Event>                 EVENT_AGENDA                                   =
                                                                          new ArgumentLiteral<>(Event.class, "event_agenda");

  @SuppressWarnings("rawtypes")
  public static final ArgumentLiteral<List>                  EVENT_ATTENDEE                                 =
                                                                            new ArgumentLiteral<>(List.class, "eventAttendee");

  public static final ArgumentLiteral<Long>                  EVENT_MODIFIER                                 =
                                                                            new ArgumentLiteral<>(Long.class, "eventModifier");

  public static final ArgumentLiteral<String>                EVENT_MODIFICATION_TYPE                        =
                                                                                     new ArgumentLiteral<>(String.class,
                                                                                                           "modificationEventType");

  public static final ArgumentLiteral<EventReminder>         EVENT_AGENDA_REMINDER                          =
                                                                                   new ArgumentLiteral<>(EventReminder.class,
                                                                                                         "event_agenda_reminder");

  public static final ArgumentLiteral<Long>                  EVENT_PARTICIPANT_ID                           =
                                                                                  new ArgumentLiteral<>(Long.class,
                                                                                                        "event_participant_id");

  public static final ArgumentLiteral<EventAttendeeResponse> EVENT_RESPONSE                                 =
                                                                            new ArgumentLiteral<>(EventAttendeeResponse.class,
                                                                                                  "event_response");

  public static final ArgumentLiteral<ZonedDateTime>         EVENT_OCCURRENCE_ID                            =
                                                                                 new ArgumentLiteral<>(ZonedDateTime.class,
                                                                                                       "occurrence_id");

  public static final String                                 AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN         =
                                                                                                    "EventAddedNotificationPlugin";

  public static final String                                 AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN      =
                                                                                                       "EventModifiedNotificationPlugin";

  public static final String                                 AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN     =
                                                                                                        "EventCanceledNotificationPlugin";

  public static final String                                 AGENDA_REMINDER_NOTIFICATION_PLUGIN            =
                                                                                                 "EventReminderNotificationPlugin";

  public static final String                                 AGENDA_REPLY_NOTIFICATION_PLUGIN               =
                                                                                              "EventReplyNotificationPlugin";

  public static final String                                 AGENDA_DATE_POLL_NOTIFICATION_PLUGIN           =
                                                                                                  "DatePollNotificationPlugin";

  public static final String                                 AGENDA_VOTE_NOTIFICATION_PLUGIN                =
                                                                                             "VoteNotificationPlugin";

  private static final String                                TEMPLATE_VARIABLE_EVENT_URL                    = "eventURL";

  private static final String                                TEMPLATE_VARIABLE_IS_CREATOR                   = "isCreator";

  public static final PluginKey                              EVENT_ADDED_KEY                                =
                                                                             PluginKey.key(AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_MODIFIED_KEY                             =
                                                                                PluginKey.key(AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_CANCELLED_KEY                            =
                                                                                 PluginKey.key(AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_REMINDER_KEY                             =
                                                                                PluginKey.key(AGENDA_REMINDER_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_REPLY_KEY                                =
                                                                             PluginKey.key(AGENDA_REPLY_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_DATE_POLL_KEY                            =
                                                                                 PluginKey.key(AGENDA_DATE_POLL_NOTIFICATION_PLUGIN);

  public static final PluginKey                              EVENT_DATE_VOTE_KEY                            =
                                                                                 PluginKey.key(AGENDA_VOTE_NOTIFICATION_PLUGIN);

  public static final String                                 STORED_PARAMETER_EVENT_TITLE                   = "eventTitle";

  public static final String                                 STORED_PARAMETER_EVENT_DESCRIPTION             = "eventDescription";

  public static final String                                 STORED_PARAMETER_EVENT_LOCATION                = "eventLocation";

  public static final String                                 STORED_PARAMETER_EVENT_OWNER_ID                = "ownerId";

  private static final String                                STORED_PARAMETER_EVENT_ID                      = "eventId";

  public static final String                                 STORED_PARAMETER_EVENT_MODIFIER                = "eventModifier";

  public static final String                                 STORED_PARAMETER_EVENT_CREATOR                 = "eventCreator";

  public static final String                                 STORED_PARAMETER_EVENT_URL                     = "Url";

  public static final String                                 STORED_PARAMETER_EVENT_OCCURRENCE_ID           = "eventOccurrenceId";

  public static final String                                 STORED_EVENT_MODIFICATION_TYPE                 =
                                                                                            "EVENT_MODIFICATION_TYPE";

  public static final String                                 STORED_PARAMETER_MODIFIER_IDENTITY_ID          =
                                                                                                   "MODIFIER_IDENTITY_ID";

  public static final String                                 STORED_PARAMETER_EVENT_START_DATE              = "startDate";

  public static final String                                 STORED_PARAMETER_EVENT_END_DATE                = "endDate";

  public static final String                                 STORED_PARAMETER_EVENT_TIMEZONE_NAME           = "eventTimeZoneName";

  public static final String                                 STORED_PARAMETER_EVENT_ATTENDEES               = "attendees";

  public static final String                                 STORED_PARAMETER_EVENT_RECURRENT_DETAILS       = "recurrenceDetails";

  public static final String                                 STORED_PARAMETER_EVENT_PARTICIPANT_NAME        = "participantName";

  public static final String                                 STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL  =
                                                                                                           "participantAvatarUrl";

  public static final String                                 STORED_PARAMETER_EVENT_RESPONSE                = "eventResponse";

  public static final String                                 STORED_PARAMETER_EVENT_STATUS                  = "eventStatus";

  private static final String                                TEMPLATE_VARIABLE_EVENT_START_DATE             = "startDate";

  private static final String                                TEMPLATE_VARIABLE_EVENT_END_DATE               = "endDate";

  private static final String                                TEMPLATE_VARIABLE_EVENT_MONTH_YEAR_DATE        = "monthYearDate";

  private static final String                                TEMPLATE_VARIABLE_AGENDA_NAME                  = "agendaName";

  private static final String                                TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR       =
                                                                                                      "calendarOwnerAvatarUrl";

  public static final String                                 TEMPLATE_VARIABLE_EVENT_ID                     = "eventId";

  public static final String                                 TEMPLATE_VARIABLE_EVENT_TITLE                  = "eventTitle";

  public static final String                                 TEMPLATE_VARIABLE_EVENT_LOCATION               = "eventLocation";

  public static final String                                 TEMPLATE_VARIABLE_EVENT_DESCRIPTION            = "eventDescription";

  public static final String                                 TEMPLATE_VARIABLE_EVENT_RECURRENT_DETAILS      = "recurrenceDetails";

  private static final String                                TEMPLATE_VARIABLE_EVENT_MODIFICATION_TYPE      = "modificationType";

  private static final String                                TEMPLATE_VARIABLE_EVENT_CREATOR                = "creatorName";

  private static final String                                TEMPLATE_VARIABLE_EVENT_ATTENDEES              = "attendees";

  private static final String                                TEMPLATE_VARIABLE_EVENT_TIMEZONE_NAME          = "timeZoneName";

  private static final String                                TEMPLATE_VARIABLE_RESPONSE_ACCEPTED            =
                                                                                                 "acceptedResponseURL";

  private static final String                                TEMPLATE_VARIABLE_RESPONSE_DECLINED            =
                                                                                                 "declinedResponseURL";

  private static final String                                TEMPLATE_VARIABLE_RESPONSE_TENTATIVE           =
                                                                                                  "tentativeResponseURL";

  private static final String                                TEMPLATE_VARIABLE_EVENT_MODIFIER               = "modifierName";

  private static final String                                TEMPLATE_VARIABLE_EVENT_PARTICIPANT_NAME       = "participantName";

  private static final String                                TEMPLATE_VARIABLE_EVENT_RESPONSE               = "responseType";

  private static final String                                TEMPLATE_VARIABLE_EVENT_PARTICIPANT_AVATAR_URL =
                                                                                                            "participantAvatarUrl";

  private static final String                                TEMPLATE_VARIABLE_MODIFIER_IDENTITY_URL        =
                                                                                                     "modifierProfileUrl";

  private static final String                                TEMPLATE_VARIABLE_EVENT_STATUS                 = "eventStatus";

  private static volatile String                             defaultSite;

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
                                                     List<EventAttendee> eventAttendees,
                                                     Event event,
                                                     String typeModification,
                                                     long modifierId) {
    if (event == null) {
      throw new IllegalArgumentException("event is null");
    }
    if (typeModification == null) {
      throw new IllegalArgumentException("Modification type is null");
    }

    Set<String> recipients = new HashSet<>();
    Set<String> participants = new HashSet<>();
    for (EventAttendee attendee : eventAttendees) {
      Identity identity = Utils.getIdentityById(identityManager, attendee.getIdentityId());
      if (identity == null) {
        continue;
      }
      if (identity.getProviderId().equals(SpaceIdentityProvider.NAME)) {
        String spaceName = identity.getRemoteId();
        List<String> memberSpace = Utils.getSpaceMembersBySpaceName(spaceName, spaceService);
        if (memberSpace != null) {
          recipients.addAll(memberSpace);
        }
      } else if (identity.getProviderId().equals(OrganizationIdentityProvider.NAME)) {
        recipients.add(identity.getRemoteId());
        participants.add(identity.getId());
      }
    }
    String showParticipants = getFullUserName(participants, identityManager);
    notification.with(STORED_PARAMETER_EVENT_ATTENDEES, showParticipants);

    // After computing all usernames, to whom, notifications will be sent,
    // this deletes the username of modifier/creator user
    long userIdentityToExclude = StringUtils.equals(typeModification, "ADDED") ? event.getCreatorId() : modifierId;
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
                                                                  Long... receiverIds) {
    List<String> receivers = new ArrayList<>();
    for (Long receiverId : receiverIds) {
      Identity identity = Utils.getIdentityById(identityManager, receiverId);
      if (identity != null && StringUtils.equals(OrganizationIdentityProvider.NAME, identity.getProviderId())) {
        receivers.add(identity.getRemoteId());
      }
    }
    notification.to(receivers);
  }

  public static final void storeEventParameters(IdentityManager identityManager,
                                                NotificationInfo notification,
                                                Event event,
                                                Calendar calendar,
                                                String typeModification) {
    if (event == null) {
      throw new IllegalArgumentException("event is null");
    }
    if (typeModification == null) {
      throw new IllegalArgumentException("Modification type is null");
    }

    Identity identity = Utils.getIdentityById(identityManager, event.getCreatorId());
    String timeZoneName = TimeZone.getTimeZone(event.getTimeZoneId()).getDisplayName() + ": " + event.getTimeZoneId();
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event))
                .with(STORED_PARAMETER_EVENT_CREATOR, getEventNotificationCreatorOrModifierUserName(identity))
                .with(STORED_EVENT_MODIFICATION_TYPE, typeModification)
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date((event.getEnd())))
                .with(STORED_PARAMETER_EVENT_RECURRENT_DETAILS, getRecurrenceDetails(event))
                .with(STORED_PARAMETER_EVENT_TIMEZONE_NAME, timeZoneName)
                .with(STORED_PARAMETER_EVENT_STATUS, event.getStatus().name());

    if (StringUtils.isNotBlank(event.getDescription())) {
      notification.with(STORED_PARAMETER_EVENT_DESCRIPTION, event.getDescription());
    }
    if (StringUtils.isNotBlank(event.getLocation())) {
      notification.with(STORED_PARAMETER_EVENT_LOCATION, event.getLocation());
    }
    long modifierId = event.getModifierId() > 0 ? event.getModifierId() : event.getCreatorId();
    if (modifierId > 0) {
      identity = Utils.getIdentityById(identityManager, modifierId);
      notification.with(STORED_PARAMETER_EVENT_MODIFIER, getEventNotificationCreatorOrModifierUserName(identity))
                  .with(STORED_PARAMETER_MODIFIER_IDENTITY_ID, String.valueOf(modifierId));
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

  public static final void storeEventParameters(IdentityManager identityManager,
                                                NotificationInfo notification,
                                                Event event,
                                                ZonedDateTime occurrenceId,
                                                long participantId,
                                                EventAttendeeResponse response,
                                                Calendar calendar,
                                                AgendaEventAttendeeService eventAttendeeService,
                                                SpaceService spaceService) {
    Identity identity = Utils.getIdentityById(identityManager, participantId);
    String timeZoneName = TimeZone.getTimeZone(event.getTimeZoneId()).getDisplayName() + ": " + event.getTimeZoneId();
    Set<String> participants = new HashSet<>();
    List<EventAttendee> eventAttendee = eventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    Set<String> spaceParticipants = new HashSet<>();
    String showSpaceParticipant = null;
    for (EventAttendee attendee : eventAttendee) {
      Identity identityAttendee = Utils.getIdentityById(identityManager, attendee.getIdentityId());
      if (identityAttendee.getProviderId().equals(SpaceIdentityProvider.NAME)) {
        String spaceName = identityAttendee.getRemoteId();
        if (StringUtils.isNotBlank(spaceName)) {
          spaceParticipants.add(spaceName);
        }
      } else if (identityAttendee.getProviderId().equals(OrganizationIdentityProvider.NAME)) {
        participants.add(identityAttendee.getId());
      }
    }
    String showParticipants = getFullUserName(participants, identityManager);
    if (!spaceParticipants.isEmpty()) {
      showSpaceParticipant = getSpaceDisplayName(spaceParticipants, spaceService);
      showParticipants = showParticipants.concat(",").concat(showSpaceParticipant);
    }
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL, setParticipantAvatarUrl(identity))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event, occurrenceId))
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_RESPONSE, String.valueOf(response))
                .with(STORED_PARAMETER_EVENT_PARTICIPANT_NAME, getEventNotificationCreatorOrModifierUserName(identity))
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date((event.getEnd())))
                .with(STORED_PARAMETER_EVENT_RECURRENT_DETAILS, getRecurrenceDetails(event))
                .with(STORED_PARAMETER_EVENT_TIMEZONE_NAME, timeZoneName)
                .with(STORED_PARAMETER_EVENT_ATTENDEES, showParticipants);
    if (occurrenceId == null && event.getOccurrence() != null) {
      occurrenceId = event.getOccurrence().getId();
    }
    if (occurrenceId != null) {
      notification.with(STORED_PARAMETER_EVENT_OCCURRENCE_ID, AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC));
    }
    if (StringUtils.isNotBlank(event.getDescription())) {
      notification.with(STORED_PARAMETER_EVENT_DESCRIPTION, event.getDescription());
    }
    if (StringUtils.isNotBlank(event.getLocation())) {
      notification.with(STORED_PARAMETER_EVENT_LOCATION, event.getLocation());
    }
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
                                                              SpaceService spaceService,
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

    setIdentityNameAndAvatar(spaceService, notification, templateContext);
    setSpaceName(notification, templateContext);
    setEventDetails(templateContext, notification, timeZone);
    String modificationStoredType = notification.getValueOwnerParameter(STORED_EVENT_MODIFICATION_TYPE);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFICATION_TYPE, modificationStoredType);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_CREATOR, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CREATOR));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ATTENDEES, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ATTENDEES));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TIMEZONE_NAME,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TIMEZONE_NAME));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_STATUS, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_STATUS));

    String eventIdString = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID);
    long eventId = Long.parseLong(eventIdString);

    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_ACCEPTED,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.ACCEPTED));
    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_DECLINED,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.DECLINED));
    templateContext.put(TEMPLATE_VARIABLE_RESPONSE_TENTATIVE,
                        getResponseURL(agendaEventAttendeeService, eventId, username, EventAttendeeResponse.TENTATIVE));

    if (StringUtils.equals(modificationStoredType, AgendaEventModificationType.UPDATED.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.DATES_UPDATED.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.DELETED.name())) {
      String identityId = notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_IDENTITY_ID);
      templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFIER, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_MODIFIER));
      String userAbsoluteURI = StringUtils.isBlank(identityId)
          || StringUtils.equals("0", identityId) ? "" : getUserAbsoluteURI(identityId);
      templateContext.put(TEMPLATE_VARIABLE_MODIFIER_IDENTITY_URL, userAbsoluteURI);
    }
    return templateContext;
  }

  public static final TemplateContext buildTemplateReminderParameters(SpaceService spaceService,
                                                                      TemplateProvider templateProvider,
                                                                      NotificationInfo notification,
                                                                      ZoneId timeZone) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    setIdentityNameAndAvatar(spaceService, notification, templateContext);
    setEventDetails(templateContext, notification, timeZone);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    return templateContext;
  }

  public static final TemplateContext buildTemplateReplyParameters(TemplateProvider templateProvider,
                                                                   NotificationInfo notification,
                                                                   ZoneId timeZone,
                                                                   boolean isCreator) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);
    templateContext.put(TEMPLATE_VARIABLE_IS_CREATOR, String.valueOf(isCreator));

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);
    setSpaceName(notification, templateContext);
    setEventReplyDetails(templateContext, notification, timeZone);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    return templateContext;
  }

  public static final TemplateContext buildTemplateDatePollParameters(SpaceService spaceService,
                                                                      TemplateProvider templateProvider,
                                                                      NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);
    setSpaceName(notification, templateContext);
    setIdentityNameAndAvatar(spaceService, notification, templateContext);
    setEventDatePollDetails(templateContext, notification);

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

  private static final void setEventReplyDetails(TemplateContext templateContext,
                                                 NotificationInfo notification,
                                                 ZoneId timeZone) {
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_PARTICIPANT_NAME,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_PARTICIPANT_NAME));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_RESPONSE, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_RESPONSE));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_PARTICIPANT_AVATAR_URL,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL));

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
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TIMEZONE_NAME,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TIMEZONE_NAME));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ATTENDEES, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ATTENDEES));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_START_DATE, startDateFormatted);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_END_DATE, endDateFormatted);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_MONTH_YEAR_DATE, dateFormatted);
  }

  private static final void setEventDatePollDetails(TemplateContext templateContext, NotificationInfo notification) {
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_CREATOR, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CREATOR));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_PARTICIPANT_NAME,
                        notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_PARTICIPANT_NAME));
    templateContext.put("USER", notification.getTo());
  }

  public static String getEventURL(Event event, ZonedDateTime occurrenceId) {
    String currentSite = getDefaultSite();
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    String notificationURL = "";
    if (event != null) {
      if (occurrenceId == null) {
        notificationURL = currentDomain + "portal/" + currentSite + "/agenda?eventId=" + event.getId();
      } else {
        notificationURL = currentDomain + "portal/" + currentSite + "/agenda?parentId=" + event.getId() + "&occurrenceId="
            + AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC);
      }
    } else {
      notificationURL = currentDomain + "portal/" + currentSite + "/agenda";
    }
    return notificationURL;
  }

  public static String getEventURL(Event event) {
    return getEventURL(event, null);
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

  private static final void setIdentityNameAndAvatar(SpaceService spaceService,
                                                     NotificationInfo notification,
                                                     TemplateContext templateContext) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(ownerId);
    String avatarUrl = null;
    if (identity == null) {
      avatarUrl = LinkProvider.SPACE_DEFAULT_AVATAR_URL;
    } else {
      if (SpaceIdentityProvider.NAME.equals(identity.getProviderId())) {
        Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
        avatarUrl = LinkProviderUtils.getSpaceAvatarUrl(space);
      } else {
        avatarUrl = LinkProviderUtils.getUserAvatarUrl(identity.getProfile());
      }
    }
    templateContext.put(TEMPLATE_VARIABLE_SUFFIX_IDENTITY_AVATAR, avatarUrl);
  }

  private static final String setParticipantAvatarUrl(Identity identity) {
    return identity.getProfile().getAvatarUrl();
  }

  private static final void setSpaceName(NotificationInfo notification, TemplateContext templateContext) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(ownerId);
    if (identity == null) {
      templateContext.put(TEMPLATE_VARIABLE_AGENDA_NAME, "");
    } else {
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
    java.util.Calendar cal = java.util.Calendar.getInstance();
    cal.setTimeInMillis(notification.getLastModifiedDate());
    templateContext.put("LAST_UPDATED_TIME",
                        TimeConvertUtils.convertXTimeAgoByTimeServer(cal.getTime(),
                                                                     "EE, dd yyyy",
                                                                     new Locale(language),
                                                                     TimeConvertUtils.YEAR));
  }

  private static final String getEventNotificationCreatorOrModifierUserName(Identity identity) {
    String[] splited = identity.getProfile().getFullName().split(" ");
    return StringUtils.capitalize(splited[0]).concat(" ").concat(StringUtils.capitalize(splited[1]));
  }

  private static String getUserAbsoluteURI(String identityId) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(identityId);
    String currentSite = CommonsUtils.getCurrentSite().getName();
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    return currentDomain + "portal/" + currentSite + "/profile/" + identity.getRemoteId();
  }

  private static String getFullUserName(Set<String> participants, IdentityManager identityManager) {
    if (participants.size() > 3) {
      List<String> showParticipants = participants.stream().limit(3).collect(Collectors.toList());
      return String.join(", ", showParticipants).concat("...");
    } else {
      List<String> showParticipants = participants.stream().map(participant -> {
        return Utils.getIdentityById(identityManager, participant).getProfile().getFullName();
      }).collect(Collectors.toList());
      return String.join(", ", showParticipants);
    }
  }

  private static String getSpaceDisplayName(Set<String> participants, SpaceService spaceService) {
    List<String> showParticipants = new ArrayList<>();
    for (String participant : participants) {
      String displaySpaceName = spaceService.getSpaceByPrettyName(participant).getDisplayName();
      showParticipants.add(displaySpaceName);
    }
    return String.join(", ", showParticipants);
  }

}
