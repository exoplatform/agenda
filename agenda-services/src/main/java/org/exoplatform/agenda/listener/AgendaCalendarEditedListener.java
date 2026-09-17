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

import org.exoplatform.agenda.model.CalendarEditorChange;
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
 * Sends the "changes in your calendar" notification once a colleague holding
 * an edit share wrote in a calendar (EXO-90378). Glue only, the shape of
 * {@link AgendaCalendarSharedListener}: the <b>event service</b> decides that
 * the writer's one right was the share and broadcasts the change, this hands
 * it to the notification framework, which decides the channels from the
 * owner's settings. No business logic here, and none possible: everything the
 * decision needed was known where the write happened.
 */
public class AgendaCalendarEditedListener extends Listener<CalendarEditorChange, Long> {

  private final ExoContainer container;

  /**
   * Builds the listener.
   *
   * @param container the portal container the notification runs in
   */
  public AgendaCalendarEditedListener(ExoContainer container) {
    this.container = container;
  }

  /**
   * Dispatches the notification of a change a colleague made.
   *
   * @param event the change event, whose source is the change
   * @throws Exception when the dispatch fails
   */
  @Override
  public void onEvent(Event<CalendarEditorChange, Long> event) throws Exception {
    CalendarEditorChange change = event == null ? null : event.getSource();
    if (change == null) {
      return;
    }
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      sendCalendarEditedNotification(change);
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Hands a change to the notification framework.
   *
   * @param change the change a colleague made
   */
  public void sendCalendarEditedNotification(CalendarEditorChange change) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationUtils.CALENDAR_EDITOR_CHANGE, change);
    ctx.getNotificationExecutor()
       .with(ctx.makeCommand(PluginKey.key(NotificationUtils.AGENDA_CALENDAR_EDITED_NOTIFICATION_PLUGIN)))
       .execute(ctx);
  }

}
