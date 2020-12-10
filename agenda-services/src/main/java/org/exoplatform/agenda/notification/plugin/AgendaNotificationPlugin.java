package org.exoplatform.agenda.notification.plugin;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.EventModificationType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.AgendaCalendarService;
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
  private static final String   AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private static final Log      LOG                             = ExoLogger.getLogger(AgendaNotificationPlugin.class);

  private String                notificationId;

  private IdentityManager       identityManager;

  private AgendaCalendarService calendarService;

  private SpaceService          spaceService;

  public AgendaNotificationPlugin(InitParams initParams,
                                  IdentityManager identityManager,
                                  AgendaCalendarService calendarService,
                                  SpaceService spaceService) {
    super(initParams);
    this.identityManager = identityManager;
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
    @SuppressWarnings("unchecked")
    List<EventAttendee> eventAttendee = ctx.value(EVENT_ATTENDEE);
    Event event = ctx.value(EVENT_AGENDA);
    long removerIdentityId = ctx.value(EVENT_REMOVER_IDENTITY_ID);
    String typeModification = ctx.value(EVENT_MODIFICATION_TYPE);
    // To avoid NPE for previously stored notifications, if
    // EVENT_MODIFICATION_TYPE parameter
    // doesn't exists, we assume that it's a new one
    typeModification = StringUtils.isNotBlank(typeModification) ? typeModification : EventModificationType.ADDED.name();

    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (event.getId() > 0) {
      setNotificationRecipients(identityManager, notification, spaceService, eventAttendee, event, typeModification, removerIdentityId);
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
