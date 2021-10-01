// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.notification.plugin;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

public class EventReminderNotificationPlugin extends BaseNotificationPlugin {
  private static final Log      LOG                             = ExoLogger.getLogger(EventReminderNotificationPlugin.class);

  private static final String   AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private String                notificationId;

  private AgendaEventService    eventService;

  private IdentityManager       identityManager;

  private AgendaCalendarService calendarService;

  public EventReminderNotificationPlugin(InitParams initParams,
                                         IdentityManager identityManager,
                                         AgendaEventService eventService,
                                         AgendaCalendarService calendarService) {
    super(initParams);
    this.identityManager = identityManager;
    this.eventService = eventService;
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
    if (getEventReminderId(ctx) == 0) {
      LOG.warn("Notification type '{}' isn't valid because the event reminder wasn't found", getId());
      return false;
    }
    return true;
  }

  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    EventReminder eventReminder = ctx.value(EVENT_AGENDA_REMINDER);
    Event event = eventService.getEventById(eventReminder.getEventId());
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    if (eventReminder.getId() > 0) {
      setEventReminderNotificationRecipients(identityManager, notification, eventReminder.getReceiverId());
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
