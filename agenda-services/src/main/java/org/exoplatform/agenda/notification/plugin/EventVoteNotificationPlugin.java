package org.exoplatform.agenda.notification.plugin;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import static org.exoplatform.agenda.util.NotificationUtils.*;

public class EventVoteNotificationPlugin extends BaseNotificationPlugin {
  private static final Log           LOG                             = ExoLogger.getLogger(EventVoteNotificationPlugin.class);

  private static final String        AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private String                     notificationId;

  private IdentityManager            identityManager;

  private AgendaCalendarService      calendarService;

  private AgendaEventAttendeeService eventAttendeeService;

  private SpaceService               spaceService;

  public EventVoteNotificationPlugin(InitParams initParams,
                                     IdentityManager identityManager,
                                     AgendaCalendarService calendarService,
                                     AgendaEventAttendeeService eventAttendeeService,
                                     SpaceService spaceService) {
    super(initParams);
    this.identityManager = identityManager;
    this.calendarService = calendarService;
    this.eventAttendeeService = eventAttendeeService;
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
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      setEventReminderNotificationRecipients(identityManager, notification, event.getCreatorId());
    }
    if (notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    } else {
      storeEventParameters(identityManager,
                           notification,
                           event,
                           eventParticipantId,
                           eventResponse,
                           calendar,
                           eventAttendeeService,
                           spaceService);
      return notification.end();
    }
  }
}
