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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

/**
 * Pins the glue keeping a space's subscribed calendars in the space's colour
 * (EXO-90373): registered on the creation and the update of a calendar, it
 * hands every saved calendar to the service but a subscribed one, whose own
 * recolouring broadcasts an update too.
 */
class AgendaSpaceSubscriptionColorListenerTest {

  /**
   * A saved ordinary calendar is handed to the service; a subscribed one and an
   * empty event are not.
   */
  @Test
  void aSavedCalendarIsHandedToTheServiceButASubscribedOne() {
    ListenerService listenerService = mock(ListenerService.class);
    AgendaCalendarSubscriptionService service = mock(AgendaCalendarSubscriptionService.class);
    AgendaSpaceSubscriptionColorListener listener = new AgendaSpaceSubscriptionColorListener(listenerService, service);
    listener.init();
    verify(listenerService).addListener(AgendaSpaceSubscriptionColorListener.CALENDAR_CREATED_EVENT, listener);
    verify(listenerService).addListener(AgendaSpaceSubscriptionColorListener.CALENDAR_UPDATED_EVENT, listener);

    Calendar subscribed = new Calendar();
    subscribed.setId(77);
    subscribed.setSubscription(true);
    listener.onEvent(new Event<>(AgendaSpaceSubscriptionColorListener.CALENDAR_UPDATED_EVENT, subscribed, null));
    listener.onEvent(null);
    verify(service, never()).followSpaceColor(any());

    Calendar spaceCalendar = new Calendar();
    spaceCalendar.setId(5);
    listener.onEvent(new Event<>(AgendaSpaceSubscriptionColorListener.CALENDAR_UPDATED_EVENT, spaceCalendar, null));
    verify(service).followSpaceColor(spaceCalendar);
  }

}
