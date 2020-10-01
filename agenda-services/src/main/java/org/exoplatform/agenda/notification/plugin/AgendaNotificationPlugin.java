package org.exoplatform.agenda.notification.plugin;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import java.util.List;

import static org.exoplatform.agenda.util.NotificationUtils.*;

public class AgendaNotificationPlugin extends BaseNotificationPlugin {
  private static final Log           LOG = ExoLogger.getLogger(AgendaNotificationPlugin.class);

  private AgendaEventService         eventService;

  private AgendaEventAttendeeService eventAttendeeService;

  private AgendaCalendarService      calendarService;

  public AgendaNotificationPlugin(InitParams initParams,
                                  AgendaEventService eventService,
                                  AgendaEventAttendeeService eventAttendeeService,
                                  AgendaCalendarService calendarService) {
    super(initParams);
    this.eventService = eventService;
    this.eventAttendeeService = eventAttendeeService;
    this.calendarService = calendarService;
    ValueParam notificationIdParam = initParams.getValueParam("notification.id");
    if (notificationIdParam == null || StringUtils.isBlank(notificationIdParam.getValue())) {
      throw new IllegalStateException("'notification.id' parameter is mandatory");
    }
  }

  @Override
  public String getId() {
    return AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN;
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
  protected NotificationInfo makeNotification(NotificationContext ctx) {
    long eventId = ctx.value(EVENT_ID);
    Event event = eventService.getEventById(eventId);
    List<EventAttendee> eventAttendee = eventAttendeeService.getEventAttendees(eventId);
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      setNotificationRecipients(notification, eventAttendee);
    }
    if (notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    } else {
      storeEventParameters(notification, event, calendar);
      return notification.end();
    }
  }
}
