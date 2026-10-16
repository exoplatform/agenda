package org.exoplatform.agenda.util;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.stream.Collectors;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

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

  /** Plugin id of "a calendar was shared with you" (EXO-90357). */
  public static final String                                 AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN     =
                                                                                                        "CalendarSharedNotificationPlugin";

  /** The share record handed to the calendar-shared notification (EXO-90357). */
  public static final ArgumentLiteral<CalendarShare>         CALENDAR_SHARE                                 =
                                                                            new ArgumentLiteral<>(CalendarShare.class, "calendar_share");

  /** Plugin id of "a colleague changed something in your calendar" (EXO-90378). */
  public static final String                                 AGENDA_CALENDAR_EDITED_NOTIFICATION_PLUGIN     =
                                                                                                        "CalendarEditedNotificationPlugin";

  /** The change handed to the calendar-edited notification (EXO-90378). */
  public static final ArgumentLiteral<CalendarEditorChange>  CALENDAR_EDITOR_CHANGE                         =
                                                                            new ArgumentLiteral<>(CalendarEditorChange.class,
                                                                                                  "calendar_editor_change");

  /**
   * Plugin id of "your participation is confirmed", sent to an attendee who
   * got a seat from the waiting list (EXO-90315).
   */
  public static final String                                 AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN =
                                                                                                                "EventParticipationConfirmedNotificationPlugin";

  /** The promotion handed to the participation-confirmed notification (EXO-90315). */
  public static final ArgumentLiteral<EventAttendeePromotion> EVENT_PROMOTION                               =
                                                                              new ArgumentLiteral<>(EventAttendeePromotion.class,
                                                                                                    "event_promotion");

  /**
   * Plugin id of "your event has reached its capacity limit", sent to the
   * creator of an event and the managers of its space when an answer fills
   * its last seat (EXO-90320).
   */
  public static final String                                 AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN   =
                                                                                                           "EventCapacityReachedNotificationPlugin";

  /**
   * The identifier of the event row whose pool of seats reached its capacity,
   * handed to the capacity-reached notification (EXO-90320): a series, the
   * row of one of its dates, or an event that is not recurring.
   */
  public static final ArgumentLiteral<Long>                  CAPACITY_REACHED_EVENT_ID                     =
                                                                                       new ArgumentLiteral<>(Long.class,
                                                                                                             "capacity_reached_event_id");

  /** Stored parameter: full name of the colleague who made the change (EXO-90378). */
  public static final String                                 STORED_PARAMETER_MODIFIER_NAME                 = "modifierName";

  /** Stored parameter: what the colleague did, ADDED, CHANGED or REMOVED (EXO-90378). */
  public static final String                                 STORED_PARAMETER_CHANGE_KIND                   = "changeKind";

  /** Stored parameter: the event's summary as it stood when the change was made (EXO-90378). */
  public static final String                                 STORED_PARAMETER_CHANGED_EVENT_SUMMARY         = "changedEventSummary";

  /** Template variable: full name of the colleague who made the change (EXO-90378). */
  public static final String                                 TEMPLATE_VARIABLE_MODIFIER_NAME                = "modifierName";

  /** Template variable: what the colleague did (EXO-90378). */
  public static final String                                 TEMPLATE_VARIABLE_CHANGE_KIND                  = "changeKind";

  /** Template variable: the event's summary (EXO-90378). */
  public static final String                                 TEMPLATE_VARIABLE_CHANGED_EVENT_SUMMARY        = "changedEventSummary";

  /** Stored parameter: technical identifier of the shared calendar. */
  public static final String                                 STORED_PARAMETER_CALENDAR_ID                   = "calendarId";

  /** Stored parameter: name of the shared calendar. */
  public static final String                                 STORED_PARAMETER_CALENDAR_NAME                 = "calendarName";

  /** Stored parameter: full name of the calendar's owner. */
  public static final String                                 STORED_PARAMETER_OWNER_NAME                    = "ownerName";

  /** Stored parameter: username of the calendar's owner. */
  public static final String                                 STORED_PARAMETER_OWNER_USERNAME                = "ownerUsername";

  /** Template variable: name of the shared calendar. */
  public static final String                                 TEMPLATE_VARIABLE_CALENDAR_NAME                = "calendarName";

  /** Template variable: full name of the calendar's owner. */
  public static final String                                 TEMPLATE_VARIABLE_OWNER_NAME                   = "ownerName";

  /** Template variable: link to the agenda. */
  public static final String                                 TEMPLATE_VARIABLE_AGENDA_URL                   = "agendaURL";

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

  /** Plugin key of "a calendar was shared with you" (EXO-90357). */
  public static final PluginKey                              CALENDAR_SHARED_KEY                            =
                                                                                 PluginKey.key(AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN);

  /** Plugin key of "a colleague changed something in your calendar" (EXO-90378). */
  public static final PluginKey                              CALENDAR_EDITED_KEY                            =
                                                                                 PluginKey.key(AGENDA_CALENDAR_EDITED_NOTIFICATION_PLUGIN);

  /** Plugin key of "your participation is confirmed" (EXO-90315). */
  public static final PluginKey                              PARTICIPATION_CONFIRMED_KEY                    =
                                                                                         PluginKey.key(AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN);

  /** Plugin key of "your event has reached its capacity limit" (EXO-90320). */
  public static final PluginKey                              CAPACITY_REACHED_KEY                           =
                                                                                      PluginKey.key(AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN);

  public static final String                                 STORED_PARAMETER_EVENT_TITLE                   = "eventTitle";

  public static final String                                 STORED_PARAMETER_EVENT_CALENDAR_NAME           = "calendarName";

  /** The label the agenda shows its owner for a personal calendar never named, in locale.portlet.Agenda. */
  public static final String                                 PERSONAL_CALENDAR_LABEL_KEY                    = "agenda.myCalendar";

  public static final String                                 STORED_PARAMETER_EVENT_DESCRIPTION             = "eventDescription";

  public static final String                                 STORED_PARAMETER_EVENT_LOCATION                = "eventLocation";

  public static final String                                 STORED_PARAMETER_EVENT_OWNER_ID                = "ownerId";

  public static final String                                 STORED_PARAMETER_EVENT_ID                      = "eventId";

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

  /** "true" on an all-day date, whose start and end are then stored as days */
  public static final String                                 STORED_PARAMETER_EVENT_ALL_DAY                 = "allDay";

  /** The number of dates promoted after the first one */
  public static final String                                 STORED_PARAMETER_OTHER_DATES                   = "otherDates";

  /** "true" when the dates promoted have no last one */
  public static final String                                 STORED_PARAMETER_OPEN_ENDED                    = "openEnded";

  /** "true" when the recipient is a guest without an account */
  public static final String                                 STORED_PARAMETER_IS_GUEST                      = "isGuest";

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

  private static final String                                TEMPLATE_VARIABLE_PROMOTED_DAY                 = "promotedDay";

  private static final String                                TEMPLATE_VARIABLE_PROMOTED_TIME                = "promotedTime";

  private static final String                                TEMPLATE_VARIABLE_OTHER_DATES                  = "otherDates";

  private static final String                                TEMPLATE_VARIABLE_OPEN_ENDED                   = "openEnded";

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
    storeCalendarName(notification, calendar);

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
    storeCalendarName(notification, calendar);
  }

  /**
   * Stores what the "Participation confirmation" notification shows: the
   * event, and on a recurring event the first date promoted, which its links
   * open, and the number of the other dates promoted.
   *
   * @param notification the notification
   * @param event the event whose row holds the first date promoted: a series,
   *          the row of one of its dates, or an event that is not recurring
   * @param date the first date promoted, as served: an occurrence of the
   *          series, or the event itself
   * @param occurrenceId the id of that date on a recurring event, null
   *          otherwise
   * @param calendar the event's calendar
   * @param promotion the seats the attendee got
   * @param guest whether the recipient is a guest without an account
   */
  public static final void storeParticipationConfirmedParameters(NotificationInfo notification,
                                                                 Event event,
                                                                 Event date,
                                                                 ZonedDateTime occurrenceId,
                                                                 Calendar calendar,
                                                                 EventAttendeePromotion promotion,
                                                                 boolean guest) {
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event, occurrenceId))
                .with(STORED_PARAMETER_WEB_EVENT_URL, getWebEventURL(event, occurrenceId))
                .with(STORED_PARAMETER_EVENT_ALL_DAY, String.valueOf(date.isAllDay()))
                .with(STORED_PARAMETER_EVENT_START_DATE, storedDate(date, date.getStart()))
                .with(STORED_PARAMETER_EVENT_END_DATE, storedDate(date, date.getEnd()))
                .with(STORED_PARAMETER_OTHER_DATES, String.valueOf(promotion.otherDates()))
                .with(STORED_PARAMETER_OPEN_ENDED, String.valueOf(promotion.openEnded()))
                .with(STORED_PARAMETER_IS_GUEST, String.valueOf(guest));
    if (occurrenceId != null) {
      notification.with(STORED_PARAMETER_EVENT_OCCURRENCE_ID, AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC));
    }
    storeCalendarName(notification, calendar);
  }

  /**
   * @return the day of an all-day date, which is the same in every zone, or
   *         the instant of a timed one
   */
  private static String storedDate(Event date, ZonedDateTime value) {
    return date.isAllDay() ? value.toLocalDate().toString() : AgendaDateUtils.toRFC3339Date(value);
  }

  /**
   * Stores what the "Event capacity reached" notification shows: the event
   * and, on a recurring event, the date that filled up, which its links open.
   *
   * @param notification the notification
   * @param event the event whose pool of seats filled up: a series, the row
   *          of one of its dates, or an event that is not recurring
   * @param occurrenceId the id of the date that filled up on a recurring
   *          event, null otherwise
   * @param calendar the event's calendar
   */
  public static final void storeCapacityReachedParameters(NotificationInfo notification,
                                                          Event event,
                                                          ZonedDateTime occurrenceId,
                                                          Calendar calendar) {
    notification.with(STORED_PARAMETER_EVENT_ID, String.valueOf(event.getId()))
                .with(STORED_PARAMETER_EVENT_TITLE, event.getSummary())
                .with(STORED_PARAMETER_EVENT_OWNER_ID, String.valueOf(calendar.getOwnerId()))
                .with(STORED_PARAMETER_EVENT_URL, getEventURL(event, occurrenceId))
                .with(STORED_PARAMETER_WEB_EVENT_URL, getWebEventURL(event, occurrenceId));
    if (occurrenceId != null) {
      notification.with(STORED_PARAMETER_EVENT_OCCURRENCE_ID, AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC));
    }
    storeCalendarName(notification, calendar);
  }

  /**
   * The managers of the space owning a calendar, by username: who hears, with
   * the event's creator, that an event of the space reached its capacity.
   *
   * @param identityManager the identity manager
   * @param spaceService the space service
   * @param ownerId the identity identifier of the calendar's owner
   * @return the managers' usernames, none when the owner is not a space
   */
  public static List<String> getSpaceManagersByOwnerId(IdentityManager identityManager, SpaceService spaceService, long ownerId) {
    Identity identity = Utils.getIdentityById(identityManager, ownerId);
    if (identity == null || !SpaceIdentityProvider.NAME.equals(identity.getProviderId())) {
      return Collections.emptyList();
    }
    Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
    if (space == null || space.getManagers() == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(space.getManagers()).filter(StringUtils::isNotBlank).toList();
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
    storeCalendarName(notification, calendar);


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
    setCalendarName(notification, templateContext);
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

  /**
   * Builds the context of the "Participation confirmation" templates. On a
   * recurring event, the first date promoted is given as a day and, on a
   * timed event, a time, in the recipient's zone and language. A guest
   * without an account is given no link to the event: the portal is closed to
   * them.
   *
   * @param templateProvider the provider of the channel's template
   * @param notification the notification
   * @param timeZone the recipient's zone
   * @return the template context
   */
  public static final TemplateContext buildTemplateParticipationConfirmedParameters(TemplateProvider templateProvider,
                                                                                    NotificationInfo notification,
                                                                                    ZoneId timeZone) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    boolean guest = Boolean.parseBoolean(notification.getValueOwnerParameter(STORED_PARAMETER_IS_GUEST));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_IS_GUEST, guest);
    templateContext.put(TEMPLATE_VARIABLE_EVENT_URL, guest ? "" : notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    templateContext.put(TEMPLATE_VARIABLE_WEB_EVENT_URL,
                        guest ? "" : notification.getValueOwnerParameter(STORED_PARAMETER_WEB_EVENT_URL));

    String promotedDay = "";
    String promotedTime = "";
    String start = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_START_DATE);
    if (StringUtils.isNotBlank(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OCCURRENCE_ID))
        && StringUtils.isNotBlank(start)) {
      Locale locale = StringUtils.isBlank(language) ? Locale.ENGLISH : LocaleUtils.toLocale(language);
      DateTimeFormatter dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale);
      if (Boolean.parseBoolean(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ALL_DAY))) {
        // An all-day date is a calendar day, the same whatever the recipient's
        // zone: never converted, or a westward recipient reads the day before
        promotedDay = dayFormat.format(LocalDate.parse(start));
      } else {
        ZonedDateTime startDate = ZonedDateTime.parse(start).withZoneSameInstant(timeZone);
        promotedDay = dayFormat.format(startDate);
        promotedTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(startDate);
      }
    }
    templateContext.put(TEMPLATE_VARIABLE_PROMOTED_DAY, promotedDay);
    templateContext.put(TEMPLATE_VARIABLE_PROMOTED_TIME, promotedTime);
    templateContext.put(TEMPLATE_VARIABLE_OTHER_DATES,
                        NumberUtils.toInt(notification.getValueOwnerParameter(STORED_PARAMETER_OTHER_DATES), 0));
    templateContext.put(TEMPLATE_VARIABLE_OPEN_ENDED,
                        Boolean.parseBoolean(notification.getValueOwnerParameter(STORED_PARAMETER_OPEN_ENDED)));
    return templateContext;
  }

  /**
   * Builds the context of the "Event capacity reached" templates: the event's
   * title and the link that opens it, on a recurring event at the date that
   * filled up.
   *
   * @param templateProvider the provider of the channel's template
   * @param notification the notification
   * @return the template context
   */
  public static final TemplateContext buildTemplateCapacityReachedParameters(TemplateProvider templateProvider,
                                                                             NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);

    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);

    templateContext.put(TEMPLATE_VARIABLE_EVENT_ID, notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_ID));
    templateContext.put(TEMPLATE_VARIABLE_EVENT_TITLE, getEventTitle(notification));
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
    setCalendarName(notification, templateContext);
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

  /**
   * The template context of "a calendar was shared with you" (EXO-90357): the
   * calendar's name, its owner's, and the link to the agenda, beside the
   * footer, read state, identifier and date every agenda notification
   * carries.
   *
   * @param templateProvider the channel's provider
   * @param notification the notification, with its stored parameters
   * @return the context
   */
  public static final TemplateContext buildTemplateCalendarSharedParameters(TemplateProvider templateProvider,
                                                                            NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);
    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);
    templateContext.put(TEMPLATE_VARIABLE_CALENDAR_NAME,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_CALENDAR_NAME)));
    templateContext.put(TEMPLATE_VARIABLE_OWNER_NAME,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_OWNER_NAME)));
    templateContext.put(TEMPLATE_VARIABLE_AGENDA_URL,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL)));
    return templateContext;
  }

  /**
   * The template context of the calendar-edited notification (EXO-90378): the
   * colleague's name, what they did, the event's summary and the calendar's
   * name, beside the footer, read state, identifier and date every agenda
   * notification carries. Everything was stored when the notification was
   * made, so nothing is read again — a deleted event has nothing left to read.
   *
   * @param templateProvider the channel's provider
   * @param notification the notification, with its stored parameters
   * @return the context
   */
  public static final TemplateContext buildTemplateCalendarEditedParameters(TemplateProvider templateProvider,
                                                                            NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    TemplateContext templateContext = getTemplateContext(templateProvider, notification, language);
    setFooter(notification, templateContext);
    setRead(notification, templateContext);
    setNotificationId(notification, templateContext);
    setLasModifiedTime(notification, templateContext, language);
    templateContext.put(TEMPLATE_VARIABLE_CALENDAR_NAME,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_CALENDAR_NAME)));
    templateContext.put(TEMPLATE_VARIABLE_MODIFIER_NAME,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_MODIFIER_NAME)));
    templateContext.put(TEMPLATE_VARIABLE_CHANGE_KIND,
                        StringUtils.defaultIfBlank(notification.getValueOwnerParameter(STORED_PARAMETER_CHANGE_KIND), "CHANGED"));
    templateContext.put(TEMPLATE_VARIABLE_CHANGED_EVENT_SUMMARY,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_CHANGED_EVENT_SUMMARY)));
    templateContext.put(TEMPLATE_VARIABLE_AGENDA_URL,
                        StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL)));
    return templateContext;
  }

  /**
   * The absolute link that opens the agenda, where a calendar shared with the
   * user sits under "Shared with me" (EXO-90357).
   *
   * @return the link
   */
  public static String getAgendaURL() {
    return eventsBaseURL();
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

  /**
   * The absolute link that opens an event, or one date of a series.
   *
   * @param event the event, a series or a stored date of a series
   * @param occurrenceId the date to open, null for the event itself
   * @return the link, naming the series and the date when a date is given
   */
  public static String getEventURL(Event event, ZonedDateTime occurrenceId) {
    String notificationURL = "";
    if (event != null) {
      if (occurrenceId == null) {
        notificationURL = getEventURL(event.getId());
      } else {
        notificationURL = eventsBaseURL() + "?parentId=" + seriesIdOf(event) + "&occurrenceId="
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

  /**
   * The link, relative to the server, that opens an event, or one date of a
   * series.
   *
   * @param event the event, a series or a stored date of a series
   * @param occurrenceId the date to open, null for the event itself
   * @return the link, naming the series and the date when a date is given
   */
  public static String getWebEventURL(Event event, ZonedDateTime occurrenceId) {
    String currentSite = getDefaultSite();
    String notificationURL = "";
    if (event != null) {
      if (occurrenceId == null) {
        notificationURL = "/portal/" + currentSite + "/agenda?eventId=" + event.getId();
      } else {
        notificationURL = "/portal/" + currentSite + "/agenda?parentId=" + seriesIdOf(event) + "&occurrenceId="
                + AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC);
      }
    } else {
      notificationURL = "/portal/" + currentSite + "/agenda";
    }
    return notificationURL;
  }

  /**
   * The series a date is read on: a date the series stores has a row of its
   * own, which the page cannot read as a series, so a link to it names its
   * series, where the date finds its row again.
   *
   * @param event a series, or a stored date of a series
   * @return the series' identifier
   */
  private static long seriesIdOf(Event event) {
    return event.getParentId() > 0 ? event.getParentId() : event.getId();
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

  /**
   * Keeps the name the owner gave the calendar with the notification, so the
   * channels can name a personal calendar without loading it again. A
   * calendar never named stores nothing: getCalendarDisplayName() then names
   * it for its recipient.
   */
  private static final void storeCalendarName(NotificationInfo notification, Calendar calendar) {
    if (calendar != null && StringUtils.isNotBlank(calendar.getName())) {
      notification.with(STORED_PARAMETER_EVENT_CALENDAR_NAME, calendar.getName());
    }
  }

  private static final void setCalendarName(NotificationInfo notification, TemplateContext templateContext) {
    String language = templateContext.getLanguage();
    Locale locale = StringUtils.isBlank(language) ? Locale.ENGLISH : LocaleUtils.toLocale(language);
    templateContext.put(TEMPLATE_VARIABLE_AGENDA_NAME, getCalendarDisplayName(notification, locale));
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

  /**
   * The name a notification gives the calendar of its event, the same the
   * agenda shows its recipient: a space calendar by its space; a personal
   * calendar by the name its owner gave it, else - for a calendar never
   * named - by the agenda's own label in the recipient's language when the
   * recipient owns it, by the owner's full name otherwise.
   *
   * @param notification the notification carrying the event parameters, and
   *          in getTo() the recipient it is rendered for
   * @param locale the language of the recipient
   * @return the calendar display name, empty when the owner is not found
   */
  public static String getCalendarDisplayName(NotificationInfo notification, Locale locale) {
    String ownerId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity identity = StringUtils.isBlank(ownerId) ? null : identityManager.getIdentity(ownerId);
    if (identity == null) {
      return "";
    } else if (SpaceIdentityProvider.NAME.equals(identity.getProviderId())) {
      Space space = ExoContainerContext.getService(SpaceService.class).getSpaceByPrettyName(identity.getRemoteId());
      return space == null ? "" : space.getDisplayName();
    } else {
      String calendarName = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_CALENDAR_NAME);
      if (StringUtils.isNotBlank(calendarName)) {
        return calendarName;
      } else if (StringUtils.equals(notification.getTo(), identity.getRemoteId())) {
        return Utils.getResourceBundleLabel(locale, PERSONAL_CALENDAR_LABEL_KEY);
      } else {
        return identity.getProfile().getFullName();
      }
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
   * built for this recipient, read from that recipient's own settings.
   *
   * <p>
   * <b>This is the fallback, not the decision.</b> Since EXO-90247 the question
   * is put to the add-on that would write the recipient's synced copy, through
   * {@code AgendaRemoteCopyService} and its
   * {@link org.exoplatform.agenda.plugin.RemoteEventCopyPlugin} — because
   * predicting another repository's decision from settings agenda happens to be
   * able to read is what let the two drift apart, in both directions at once:
   * the add-on's seeding pass wrote a copy where this predicted none (the
   * meeting twice), and later declined one where this predicted one (the
   * meeting nowhere). What is left here is the answer for a deployment where
   * there is no add-on to ask, and for a cancellation whose event is already
   * gone, so no add-on can be asked about it.
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
