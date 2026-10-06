package org.exoplatform.agenda.notification.plugin;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.CalendarShareAccess;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class EventReplyNotificationPlugin extends BaseNotificationPlugin {
  private static final Log           LOG                             = ExoLogger.getLogger(EventReplyNotificationPlugin.class);

  private static final String        AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private String                     notificationId;

  private IdentityManager            identityManager;

  private AgendaCalendarService      calendarService;

  private AgendaEventAttendeeService eventAttendeeService;

  private AgendaEventService         eventService;

  private SpaceService               spaceService;

  /** The level a calendar is shared at, read as the event service reads it. */
  private CalendarShareAccess        calendarShareAccess             = new CalendarShareAccess();

  public EventReplyNotificationPlugin(InitParams initParams,
                                      IdentityManager identityManager,
                                      AgendaCalendarService calendarService,
                                      AgendaEventAttendeeService eventAttendeeService,
                                      AgendaEventService eventService,
                                      SpaceService spaceService) {
    super(initParams);
    this.identityManager = identityManager;
    this.calendarService = calendarService;
    this.eventAttendeeService = eventAttendeeService;
    this.eventService = eventService;
    this.spaceService = spaceService;
    ValueParam notificationIdParam = initParams.getValueParam(AGENDA_NOTIFICATION_PLUGIN_NAME);
    if (notificationIdParam == null || StringUtils.isBlank(notificationIdParam.getValue())) {
      throw new IllegalStateException("'agenda.notification.plugin.key' parameter is mandatory");
    }
    this.notificationId = notificationIdParam.getValue();
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  /**
   * Whether an identity is a user of this deployment — a space owns a
   * calendar too, and a space is nobody to notify.
   *
   * @param identityId identity identifier
   * @return true for a user
   */
  private boolean isUser(long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity != null && !identity.isDeleted() && identity.isUser();
  }

  /**
   * Who hears an attendee's reply: the event's creator, while they may, and the
   * owner of the personal calendar the event is in; when the creator declines
   * their own event, the attendees who can still edit it.
   *
   * @param event the event replied to
   * @param calendar its calendar, null when it is gone
   * @param eventParticipantId the identity that replied
   * @param eventResponse the reply
   * @param occurrenceId the occurrence replied to, null for the whole event
   * @return the identities to notify
   */
  Set<Long> replyRecipients(Event event,
                            Calendar calendar,
                            long eventParticipantId,
                            EventAttendeeResponse eventResponse,
                            ZonedDateTime occurrenceId) {
    Set<Long> receivers = new HashSet<>();
    if (eventParticipantId != event.getCreatorId()) {
      if (creatorHearsReplies(event, calendar)) {
        receivers.add(event.getCreatorId());
      }
    } else if (EventAttendeeResponse.DECLINED.equals(eventResponse) && eventParticipantId == event.getCreatorId()) {
      List<EventAttendee> eventAttendees = eventAttendeeService.getEventAttendees(event.getId(),
                                                                                  occurrenceId,
                                                                                  EventAttendeeResponse.ACCEPTED,
                                                                                  EventAttendeeResponse.TENTATIVE);
      Set<Long> eventAttendeeIds = eventAttendees.stream()
                                                 .map(EventAttendee::getIdentityId)
                                                 .filter(identityId -> eventService.canUpdateEvent(event, identityId))
                                                 .collect(Collectors.toSet());
      eventAttendeeIds.add(event.getCreatorId());
      eventAttendeeIds.remove(eventParticipantId);
      receivers = new HashSet<>(eventAttendeeIds);
    }
    // The owner of a personal calendar hears the replies to a meeting held
    // in it, even when a colleague they shared it with for editing created
    // it (EXO-90378): without this the owner would never learn who accepted a
    // meeting in their own calendar, and once that colleague's share no longer
    // lets them read the event in full, the owner is the one who hears them.
    // Never the replier themselves, and never a space — a space calendar's
    // owner is the space, which is nobody to notify.
    if (calendar != null && !calendar.isDeleted() && calendar.getOwnerId() != event.getCreatorId()
        && calendar.getOwnerId() != eventParticipantId && isUser(calendar.getOwnerId())) {
      receivers.add(calendar.getOwnerId());
    }
    return receivers;
  }

  /**
   * Whether the creator of an event still hears its attendees' replies
   * (EXO-90378). In a personal calendar a creator other than the owner made the
   * event through an edit share, and hears the replies while that share lets
   * them read the event in full: an edit share, or a view share on an event
   * that is not private. Once the share is removed, or downgraded to view on a
   * private event, the replies go to the owner alone, who hears them anyway.
   * A creator who attends the event still reads it in full, as
   * {@link AgendaEventService#getEventAccess(Event, long)} answers for any
   * attendee, so they keep hearing its replies whatever their share: the
   * attendance is asked on the event, or on its series when the event has no id
   * of its own. A space calendar keeps its creator as the one who hears them:
   * its owner, the space, is nobody to notify.
   *
   * @param event the event replied to
   * @param calendar its calendar, null when it is gone
   * @return true when the creator is notified
   */
  private boolean creatorHearsReplies(Event event, Calendar calendar) {
    long creatorId = event.getCreatorId();
    if (calendar == null || calendar.isDeleted() || calendar.getOwnerId() == creatorId || !isUser(calendar.getOwnerId())) {
      return true;
    }
    long attendedEventId = event.getId() > 0 ? event.getId() : event.getParentId();
    if (attendedEventId > 0 && eventAttendeeService.isEventAttendee(attendedEventId, creatorId)) {
      return true;
    }
    CalendarShareLevel level = calendarShareAccess.levelOf(calendar.getId(), creatorId);
    return level == CalendarShareLevel.EDIT
        || level == CalendarShareLevel.VIEW && event.getVisibility() != EventVisibility.PRIVATE;
  }

  void setCalendarShareAccess(CalendarShareAccess calendarShareAccess) {
    this.calendarShareAccess = calendarShareAccess;
  }

  @Override
  public boolean isValid(NotificationContext ctx) {
    if (getEventId(ctx) == 0) {
      LOG.warn("Notification type '{}' isn't valid because the event wasn't found", getId());
      return false;
    }
    return true;
  }

  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    Event event = ctx.value(EVENT_AGENDA);
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    long eventParticipantId = ctx.value(EVENT_PARTICIPANT_ID);
    EventAttendeeResponse eventResponse = ctx.value(EVENT_RESPONSE);
    ZonedDateTime occurrenceId = ctx.value(EVENT_OCCURRENCE_ID);
    if (occurrenceId == null && event.getOccurrence() != null) {
      occurrenceId = event.getOccurrence().getId();
    }
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      Set<Long> receivers = replyRecipients(event, calendar, eventParticipantId, eventResponse, occurrenceId);
      setEventReminderNotificationRecipients(identityManager, notification, receivers.toArray(new Long[receivers.size()]));
    }
    if (notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    } else {
      storeEventParameters(identityManager,
                           notification,
                           event,
                           occurrenceId,
                           eventParticipantId,
                           eventResponse,
                           calendar,
                           eventAttendeeService,
                           spaceService);
      return notification.end();
    }
  }
}
