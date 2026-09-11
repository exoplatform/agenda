package org.exoplatform.agenda.util;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.NotificationMessageUtils;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.*;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLEntityEncoder;
import org.exoplatform.commons.utils.TimeConvertUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import static org.exoplatform.agenda.util.Utils.getICalTimeZone;
import static org.exoplatform.agenda.util.Utils.getResourceBundleLabel;

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

  private static final String                                TEMPLATE_VARIABLE_WEB_EVENT_URL                    = "webEventURL";

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

  public static final String                                 STORED_PARAMETER_WEB_EVENT_URL                     = "webUrl";

  public static final String                                 STORED_PARAMETER_EVENT_OCCURRENCE_ID           = "eventOccurrenceId";

  public static final String                                 STORED_EVENT_MODIFICATION_TYPE                 =
                                                                                            "EVENT_MODIFICATION_TYPE";

  public static final String                                 STORED_PARAMETER_MODIFIER_IDENTITY_ID          =
                                                                                                   "MODIFIER_IDENTITY_ID";

  public static final String                                 STORED_PARAMETER_EVENT_START_DATE              = "startDate";

  public static final String                                 STORED_PARAMETER_EVENT_END_DATE                = "endDate";

  public static final String                                 STORED_PARAMETER_EVENT_TIMEZONE_NAME           = "eventTimeZoneName";

  public static final String                                 STORED_PARAMETER_EVENT_ATTENDEES               = "attendees";

  public static final String                                 STORED_PARAMETER_EVENT_CONFERENCE              = "conference";

  public static final String                                 STORED_PARAMETER_EVENT_RECURRENT_DETAILS       = "recurrenceDetails";

  public static final String                                 STORED_PARAMETER_EVENT_PARTICIPANT_NAME        = "participantName";

  public static final String                                 STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL  =
                                                                                                           "participantAvatarUrl";

  public static final String                                 STORED_PARAMETER_EVENT_RESPONSE                = "eventResponse";

  public static final String                                 STORED_PARAMETER_EVENT_STATUS                  = "eventStatus";

  public static final String                                STORED_PARAMETER_EVENT_IS_CREATOR = "isCreator";

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

  private static final String                                TEMPLATE_VARIABLE_EVENT_IS_GUEST               = "isGuest";

  /**
   * Name the CalDAV connector registers itself under in a user's connected
   * accounts. Duplicated from the add-on's own constant on purpose: CalDAV
   * depends on agenda, so the dependency cannot be taken the other way round.
   */
  public static final String                                 CALDAV_PROVIDER_NAME                           =
                                                                                  "agenda.caldavCalendar";

  private static final String                                TEMPLATE_VARIABLE_EVENT_CONFERENCE             = "conference";

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
      } else if (identity.getProviderId().equals(OrganizationIdentityProvider.NAME)
          || identity.getProviderId().equals(AgendaGuestUserIdentityProvider.NAME)) {
        recipients.add(identity.getRemoteId());
        participants.add(identity.getId());
      }
    }
    String showParticipants = getFullUserName(participants, identityManager);
    notification.with(STORED_PARAMETER_EVENT_ATTENDEES, showParticipants);

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
                .with(STORED_PARAMETER_WEB_EVENT_URL, getWebEventURL(event,null))
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

    String webConferenceLink = getWebConferenceLink(event);
    if (webConferenceLink != null) {
      notification.with(STORED_PARAMETER_EVENT_CONFERENCE, webConferenceLink);
    }
  }

  public static final void storeEventParameters(NotificationInfo notification,
                                                Event event,
                                                org.exoplatform.agenda.model.Calendar calendar) {

    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event))
                .with(STORED_PARAMETER_WEB_EVENT_URL, getWebEventURL(event,null))
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
                .with(STORED_PARAMETER_WEB_EVENT_URL, getWebEventURL(event, occurrenceId))
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_RESPONSE, String.valueOf(response))
                .with(STORED_PARAMETER_EVENT_PARTICIPANT_NAME, getEventNotificationCreatorOrModifierUserName(identity))
                .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(event.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date((event.getEnd())))
                .with(STORED_PARAMETER_EVENT_RECURRENT_DETAILS, getRecurrenceDetails(event))
                .with(STORED_PARAMETER_EVENT_TIMEZONE_NAME, timeZoneName)
                .with(STORED_PARAMETER_EVENT_ATTENDEES, showParticipants);


    String username = notification.getTo();
    long identityId = Utils.getIdentityIdByUsername(identityManager, username);
    boolean isCreator = event.getCreatorId() == identityId;
    notification.with(STORED_PARAMETER_EVENT_IS_CREATOR,String.valueOf(isCreator));


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
    defaultSite = portalConfig.getMetaPortal();
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

    setIdentityName(spaceService, notification);
    setSpaceName(notification, templateContext);
    setEventDetails(templateContext, notification, timeZone);
    setIsGuest(username, templateContext);
    String modificationStoredType = notification.getValueOwnerParameter(STORED_EVENT_MODIFICATION_TYPE);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFICATION_TYPE, modificationStoredType);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_WEB_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_WEB_EVENT_URL));
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

    if (StringUtils.equals(modificationStoredType, AgendaEventModificationType.ADDED.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.UPDATED.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.DATES_UPDATED.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL.name())
        || StringUtils.equals(modificationStoredType, AgendaEventModificationType.DELETED.name())) {
      String identityId = notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_IDENTITY_ID);
      templateContext.put(TEMPLATE_VARIABLE_EVENT_MODIFIER, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_MODIFIER));
      String userAbsoluteURI = StringUtils.isBlank(identityId)
          || StringUtils.equals("0", identityId) ? "" : getUserAbsoluteURI(identityId);
      templateContext.put(TEMPLATE_VARIABLE_MODIFIER_IDENTITY_URL, userAbsoluteURI);
      IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
      Identity identity = identityManager.getIdentity(identityId);
      templateContext.put(TEMPLATE_VARIABLE_IS_CREATOR, notificationReceiverUserName.equals(identity.getRemoteId()));
    }
    if (notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CONFERENCE) != null) {
      templateContext.put(TEMPLATE_VARIABLE_EVENT_CONFERENCE,
                          notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CONFERENCE));
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

    setIdentityName(spaceService, notification);
    setEventDetails(templateContext, notification, timeZone);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_WEB_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_WEB_EVENT_URL));
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
    templateContext.put(TEMPLATE_VARIABLE_WEB_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_WEB_EVENT_URL));
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
    setIdentityName(spaceService, notification);
    setEventDatePollDetails(templateContext, notification);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_WEB_EVENT_URL, notification.getValueOwnerParameter(STORED_PARAMETER_WEB_EVENT_URL));
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

  /**
   * The absolute link that opens one event in eXo.
   *
   * <p>
   * This is the single definition of the shape of that link, and it is the one
   * every notification mail's deep link has always used. EXO-89751 made it an
   * overload of its own so that the iCalendar documents eXo writes — the file
   * attached to the invitation mail, and the copy pushed into a user's own
   * calendar over CalDAV — can render the very same string as the mail body
   * they arrive with, from the event alone, without a caller having to pass one
   * in. That is the property the whole ticket turns on: a value derived from
   * the event is the same on a browser push, on a sweep and on a repair, so the
   * link is written once and never stripped by the next repair.
   *
   * @param eventId technical identifier of the event
   * @return the absolute link, ending in <code>agenda?eventId=&lt;id&gt;</code>
   */
  public static String getEventURL(long eventId) {
    return eventsBaseURL() + "?eventId=" + eventId;
  }

  public static String getEventURL(Event event, ZonedDateTime occurrenceId) {
    String notificationURL = "";
    if (event != null) {
      if (occurrenceId == null) {
        notificationURL = getEventURL(event.getId());
      } else {
        notificationURL = eventsBaseURL() + "?parentId=" + event.getId() + "&occurrenceId="
            + AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC);
      }
    } else {
      notificationURL = eventsBaseURL();
    }
    return notificationURL;
  }

  /**
   * The agenda application's own absolute address, with no event named yet.
   *
   * <p>
   * The domain comes from the deployment's configured one
   * ({@link CommonsUtils#getCurrentDomain()}, the
   * <code>gatein.email.domain.url</code> property) rather than from a request,
   * because most of the callers have no request: a notification is rendered by
   * a job, and so is a CalDAV sweep.
   *
   * @return the address, with no trailing slash and no query string
   */
  private static String eventsBaseURL() {
    String currentDomain = CommonsUtils.getCurrentDomain();
    if (!currentDomain.endsWith("/")) {
      currentDomain += "/";
    }
    return currentDomain + "portal/" + getDefaultSite() + "/agenda";
  }

  public static String getWebEventURL(Event event, ZonedDateTime occurrenceId) {
    String currentSite = getDefaultSite();
    String notificationURL = "";
    if (event != null) {
      if (occurrenceId == null) {
        notificationURL = "/portal/" + currentSite + "/agenda?eventId=" + event.getId();
      } else {
        notificationURL = "/portal/" + currentSite + "/agenda?parentId=" + event.getId() + "&occurrenceId="
                + AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC);
      }
    } else {
      notificationURL = "/portal/" + currentSite + "/agenda";
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
          if (eventRecurrence.getInterval() == 1) {
            List<String> dayNamesAbbreviations = eventRecurrence.getByDay();
            return "Weekly on " + AgendaDateUtils.getDayNameFromDayAbbreviation(dayNamesAbbreviations);
          } else {
            List<String> dayNamesAbbreviations = eventRecurrence.getByDay();
            return "Each Week " + eventRecurrence.getInterval() + " on " + AgendaDateUtils.getDayNameFromDayAbbreviation(dayNamesAbbreviations);
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

  private static final void setIdentityName(SpaceService spaceService,
                                            NotificationInfo notification) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getIdentity(ownerId);
    if (identity != null && SpaceIdentityProvider.NAME.equals(identity.getProviderId())) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      notification.setSpaceId(Long.parseLong(space.getId()));
    }
  }

  private static final void setIsGuest(String username, TemplateContext templateContext) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      identity = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, username);
    }
    templateContext.put(TEMPLATE_VARIABLE_EVENT_IS_GUEST,
                        identity != null && identity.getProviderId().equals(AgendaGuestUserIdentityProvider.NAME));
  }

  private static final String setParticipantAvatarUrl(Identity identity) {
    return identity.getProfile().getAvatarUrl();
  }

  private static final void setSpaceName(NotificationInfo notification, TemplateContext templateContext) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    SpaceService spaceService = ExoContainerContext.getService(SpaceService.class);
    Identity identity = identityManager.getIdentity(ownerId);
    if (identity == null) {
      templateContext.put(TEMPLATE_VARIABLE_AGENDA_NAME, "");
    } else {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      String spaceName = space == null ? null : space.getDisplayName();
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
    String fullName = Arrays.stream(identity.getProfile().getFullName().split(" +"))
            .map(t -> t.substring(0, 1).toUpperCase() + t.substring(1))
            .collect(Collectors.joining(" "));
    if(Utils.isExternal(identity.getRemoteId())) {
      fullName += " " + "(" + getResourceBundleLabel(new Locale(Utils.getUserLanguage(identity.getRemoteId())), "external.label.tag") + ")";
    }
    return fullName;
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
    String showParticipants = participants.stream()
                                          .limit(3)
                                          .map(participant -> Utils.getIdentityById(identityManager, participant)
                                                                   .getProfile()
                                                                   .getFullName())
                                          .collect(Collectors.joining(", "));
    if (participants.size() > 3) {
      showParticipants = showParticipants.concat("...");
    }
    return showParticipants;
  }

  private static String getSpaceDisplayName(Set<String> participants, SpaceService spaceService) {
    List<String> showParticipants = new ArrayList<>();
    for (String participant : participants) {
      String displaySpaceName = spaceService.getSpaceByPrettyName(participant).getDisplayName();
      showParticipants.add(displaySpaceName);
    }
    return String.join(", ", showParticipants);
  }

  public static String getWebConferenceLink(Event event) {
    AgendaEventConferenceService agendaEventConferenceService =
                                                              ExoContainerContext.getService(AgendaEventConferenceService.class);
    List<EventConference> webConferences = agendaEventConferenceService.getEventConferences(event.getId());
    if (webConferences != null && !webConferences.isEmpty()) {
      return webConferences.get(0).getUrl();
    } else {
      return null;
    }

  }

  /**
   * Whether the {@code event.ics} file must be attached to the notification
   * built for this recipient.
   *
   * <p>
   * A recipient who keeps a CalDAV account connected with meeting copies on
   * ends up holding the same meeting twice: once as the copy eXo pushes into
   * their "eXo Meetings" calendar, once if they open the file attached to
   * their mail. The two documents carry different UIDs, so no client can tell
   * they are the same meeting and neither replaces the other. The file is the
   * redundant one — it is the poorer document (no ATTENDEE, no PARTSTAT, no
   * recurrence, no alarms) and the copy arrives whether it is opened or not —
   * so it is the one that goes.
   *
   * <p>
   * <b>This is a prediction, not an observation, and deliberately so.</b> The
   * invitation is dispatched synchronously inside event creation
   * ({@code AgendaEventServiceImpl.createEvent} to
   * {@code AgendaEventAttendeeServiceImpl.sendInvitations}) while an
   * attendee's copy is written only by the five-minute CalDAV sweep. At the
   * moment this runs the copy does not exist yet — measured on the rig,
   * notification at 21:36:19Z and copy at 21:40:16Z — so "does a copy exist"
   * is unanswerable here and asking it would be a race. The question answered
   * instead is "will this user hold a copy", read from the user's own
   * settings. It is wrong for a user whose CalDAV server has been unreachable
   * for a long time; that user loses the attachment, but they are already
   * receiving none of their meetings, and they can still see the event in eXo
   * and answer from the links in the mail, which this does not touch.
   *
   * <p>
   * <b>A guest is never suppressed</b>, under any condition: a guest has no
   * copy by definition, and the attachment is their only way to get the
   * meeting at all. Two independent things guarantee it here — a guest is a
   * {@link org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider}
   * identity, so it never resolves to an organization identity and reaches
   * this method with an id of 0; and having no connected account, its default
   * settings fail the connector test as well.
   *
   * @param recipientIdentityId organization identity id of the recipient, 0
   *          when the recipient is not an internal user (a guest)
   * @param recipientSettings agenda settings of that recipient, possibly null
   * @return true when the file must be attached, which is every case but the
   *         connected-with-copies one
   */
  public static boolean shouldAttachIcsFile(long recipientIdentityId, AgendaUserSettings recipientSettings) {
    return !willHoldCaldavCopy(recipientIdentityId, recipientSettings);
  }

  /**
   * Whether the recipient's own settings say they will hold a CalDAV copy of
   * the meetings they are invited to.
   *
   * <p>
   * Reads the same two switches the front end reads before it asks the server
   * for a copy — {@code AgendaConnector.vue}'s {@code shouldReachAccount}, the
   * single gate every push trigger funnels through, requires
   * {@code settings.automaticPushEvents} <i>and</i> the account's own
   * {@code pushEnabled} for a space meeting. Reading anything else here would
   * predict a copy the platform never writes.
   *
   * @param recipientIdentityId organization identity id of the recipient, 0
   *          for a guest
   * @param recipientSettings agenda settings of that recipient, possibly null
   * @return true when a CalDAV account of that user is set to receive copies
   */
  private static boolean willHoldCaldavCopy(long recipientIdentityId, AgendaUserSettings recipientSettings) {
    if (recipientIdentityId <= 0 || recipientSettings == null || !recipientSettings.isAutomaticPushEvents()) {
      return false;
    }
    List<AgendaConnectorAccount> connectedAccounts = recipientSettings.getConnectedConnectors();
    return connectedAccounts != null
        && connectedAccounts.stream().anyMatch(account -> account != null && account.isPushEnabled()
            && isCaldavProvider(account.getProviderName()));
  }

  /**
   * Whether a connected account's provider is a CalDAV one.
   *
   * <p>
   * The name is matched rather than imported: the CalDAV add-on depends on
   * agenda, so agenda cannot see its constant. Two spellings exist — the seed
   * registration is {@code agenda.caldavCalendar} and every additional
   * declared server is {@code agenda.caldavCalendar.<id>} — and both push
   * copies into the same mirror calendar, so both count.
   *
   * @param providerName name of the remote provider an account is held on
   * @return true when that provider is CalDAV
   */
  private static boolean isCaldavProvider(String providerName) {
    return StringUtils.equals(providerName, CALDAV_PROVIDER_NAME)
        || StringUtils.startsWith(providerName, CALDAV_PROVIDER_NAME + ".");
  }

}
