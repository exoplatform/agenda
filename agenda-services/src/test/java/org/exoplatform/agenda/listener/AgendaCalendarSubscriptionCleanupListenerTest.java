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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

/**
 * Pins the clean-up of a subscription whose calendar was deleted by another path
 * (EXO-90278): it is registered on the deletion event, and acts on subscribed
 * calendars only.
 */
class AgendaCalendarSubscriptionCleanupListenerTest {

  /**
   * The subscription of a deleted subscribed calendar is removed; nothing is
   * asked for any other calendar.
   */
  @Test
  void theSubscriptionOfADeletedSubscribedCalendarIsRemoved() {
    ListenerService listenerService = mock(ListenerService.class);
    AgendaCalendarSubscriptionService service = mock(AgendaCalendarSubscriptionService.class);
    AgendaCalendarSubscriptionCleanupListener listener = new AgendaCalendarSubscriptionCleanupListener(listenerService, service);
    listener.init();
    verify(listenerService).addListener(AgendaCalendarSubscriptionCleanupListener.CALENDAR_DELETED_EVENT, listener);

    Calendar ordinary = new Calendar();
    ordinary.setId(5);
    listener.onEvent(new Event<>(AgendaCalendarSubscriptionCleanupListener.CALENDAR_DELETED_EVENT, ordinary, null));
    verify(service, never()).deleteCalendarSubscription(anyLong());

    Calendar subscribed = new Calendar();
    subscribed.setId(77);
    subscribed.setSubscription(true);
    listener.onEvent(new Event<>(AgendaCalendarSubscriptionCleanupListener.CALENDAR_DELETED_EVENT, subscribed, null));
    verify(service).deleteCalendarSubscription(77);
  }

}
