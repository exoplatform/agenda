package org.exoplatform.agenda.notification.plugin;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import static org.exoplatform.agenda.util.NotificationUtils.*;

public class AgendaNotificationPlugin extends BaseNotificationPlugin {
  private static final Log LOG = ExoLogger.getLogger(AgendaNotificationPlugin.class);

  private String           notificationId;

  public AgendaNotificationPlugin(InitParams initParams) {
    super(initParams);
    ValueParam notificationIdParam = initParams.getValueParam("notification.id");
    if (notificationIdParam == null || StringUtils.isBlank(notificationIdParam.getValue())) {
      throw new IllegalStateException("'notification.id' parameter is mandatory");
    }
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  @Override
  public boolean isValid(NotificationContext ctx) {
    if (getEventParameter(ctx) == null) {
      LOG.warn("Notification type '{}' isn't valid because the event wasn't found", getId());
      return false;
    }
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext ctx) {
    Event event = getEventParameter(ctx);
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    setNotificationRecipients(notification, event);
    if ((notification.getSendToUserIds() == null || notification.getSendToUserIds().isEmpty())) {
      if (LOG.isDebugEnabled()) {
        LOG.warn("Notification type '{}' doesn't have a recipient", getId());
      }
      return null;
    } else {
      storeEventParameters(notification, event);
      return notification.end();
    }
  }
}
