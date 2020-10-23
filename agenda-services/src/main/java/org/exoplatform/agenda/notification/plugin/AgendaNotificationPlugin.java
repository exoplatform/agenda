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
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaNotificationPlugin extends BaseNotificationPlugin {
  private static final String        AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private static final Log           LOG                             = ExoLogger.getLogger(AgendaNotificationPlugin.class);

  private String                     notificationId;

  private IdentityManager            identityManager;

  private AgendaEventService         eventService;

  private AgendaEventAttendeeService eventAttendeeService;

  private AgendaCalendarService      calendarService;

  private SpaceService               spaceService;

  public AgendaNotificationPlugin(InitParams initParams,
                                  IdentityManager identityManager,
                                  AgendaEventService eventService,
                                  AgendaEventAttendeeService eventAttendeeService,
                                  AgendaCalendarService calendarService,
                                  SpaceService spaceService) {
    super(initParams);
    this.identityManager = identityManager;
    this.eventService = eventService;
    this.eventAttendeeService = eventAttendeeService;
    this.calendarService = calendarService;
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
    List<EventAttendee> eventAttendee = ctx.value(EVENT_ATTENDEE);
    Event event = ctx.value(EVENT_AGENDA);
    String typeModification = ctx.value(EVENT_MODIFICATION_TYPE);

    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      setNotificationRecipients(identityManager, notification, spaceService, eventAttendee, event, typeModification);
    }
    if (notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    } else {
      storeEventParameters(identityManager, notification, event, calendar, typeModification);
      return notification.end();
    }
  }

}
