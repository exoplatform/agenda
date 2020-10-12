package org.exoplatform.agenda.notification.plugin;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

public class AgendaNotificationPlugin extends BaseNotificationPlugin {
  private static final String        AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private static final Log           LOG                             = ExoLogger.getLogger(AgendaNotificationPlugin.class);

  private String                     notificationId;

  private IdentityManager            identityManager;

  private AgendaEventService         eventService;

  private AgendaEventAttendeeService eventAttendeeService;

  private AgendaCalendarService      calendarService;

  public AgendaNotificationPlugin(InitParams initParams,
                                  IdentityManager identityManager,
                                  AgendaEventService eventService,
                                  AgendaEventAttendeeService eventAttendeeService,
                                  AgendaCalendarService calendarService) {
    super(initParams);
    this.identityManager = identityManager;
    this.eventService = eventService;
    this.eventAttendeeService = eventAttendeeService;
    this.calendarService = calendarService;
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
    long eventId = ctx.value(EVENT_ID);
    Boolean isNew = ctx.value(IS_NEW);
    // To avoid NPE for previously stored notifications, if IS_NEW parameter
    // doesn't exists, we assume that it's a new one
    isNew = isNew == null || isNew.booleanValue();

    Event event = eventService.getEventById(eventId);
    List<EventAttendee> eventAttendee = eventAttendeeService.getEventAttendees(eventId);
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      setNotificationRecipients(identityManager, notification, eventAttendee, event, isNew);
    }
    if (notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    } else {
      storeEventParameters(notification, event, calendar, isNew);
      return notification.end();
    }
  }

}
