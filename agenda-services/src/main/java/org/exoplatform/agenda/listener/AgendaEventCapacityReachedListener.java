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

import java.time.ZonedDateTime;

import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * Tells the creator of an event and the managers of its space that an answer
 * filled its last seat, with the "Event capacity reached" notification
 * (EXO-90320). Glue only: the source is the identifier of the event row whose
 * pool of seats filled up, the data the date that filled up on a recurring
 * event. Asynchronous, as no decision on a seat waits for a notification.
 */
@Asynchronous
public class AgendaEventCapacityReachedListener extends Listener<Long, ZonedDateTime> {

  private final ExoContainer container;

  /**
   * Builds the listener.
   *
   * @param container the portal container the notification runs in
   */
  public AgendaEventCapacityReachedListener(ExoContainer container) {
    this.container = container;
  }

  /**
   * Hands a pool of seats that filled up to the notification.
   *
   * @param event the pool filled, whose source is the identifier of its event
   *          row and whose data is the date filled on a recurring event
   * @throws Exception when the dispatch fails
   */
  @Override
  public void onEvent(Event<Long, ZonedDateTime> event) throws Exception {
    Long eventId = event == null ? null : event.getSource();
    if (eventId == null || eventId <= 0) {
      return;
    }
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try {
      sendCapacityReachedNotification(eventId, event.getData());
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Hands a pool of seats that filled up to the "Event capacity reached"
   * notification.
   *
   * @param eventId the identifier of the event row whose pool filled up
   * @param occurrenceId the date that filled up on a recurring event, null
   *          otherwise
   */
  static void sendCapacityReachedNotification(long eventId, ZonedDateTime occurrenceId) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationUtils.CAPACITY_REACHED_EVENT_ID, eventId);
    if (occurrenceId != null) {
      ctx.append(NotificationUtils.EVENT_OCCURRENCE_ID, occurrenceId);
    }
    ctx.getNotificationExecutor()
       .with(ctx.makeCommand(NotificationUtils.CAPACITY_REACHED_KEY))
       .execute(ctx);
  }
}
