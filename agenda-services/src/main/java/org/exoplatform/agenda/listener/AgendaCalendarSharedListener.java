/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * Sends the "a calendar was shared with you" notification once a share is
 * recorded (EXO-90357). Glue only, the shape of {@link AgendaEventReplyListener}:
 * the share service broadcasts, this hands the record to the notification
 * framework, which decides the channels from the colleague's settings.
 */
public class AgendaCalendarSharedListener extends Listener<CalendarShare, Long> {

  private final ExoContainer container;

  /**
   * Builds the listener.
   *
   * @param container the portal container the notification runs in
   */
  public AgendaCalendarSharedListener(ExoContainer container) {
    this.container = container;
  }

  /**
   * Dispatches the notification of a recorded share.
   *
   * @param event the share event, whose source is the record
   * @throws Exception when the dispatch fails
   */
  @Override
  public void onEvent(Event<CalendarShare, Long> event) throws Exception {
    CalendarShare share = event == null ? null : event.getSource();
    if (share == null) {
      return;
    }
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      sendCalendarSharedNotification(share);
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Hands a share to the notification framework.
   *
   * @param share the record
   */
  public void sendCalendarSharedNotification(CalendarShare share) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationUtils.CALENDAR_SHARE, share);
    ctx.getNotificationExecutor()
       .with(ctx.makeCommand(PluginKey.key(NotificationUtils.AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN)))
       .execute(ctx);
  }

}
