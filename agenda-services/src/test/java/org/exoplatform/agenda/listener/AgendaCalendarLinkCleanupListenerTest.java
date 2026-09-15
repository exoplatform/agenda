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
import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

/**
 * Pins that a deleted calendar's link is deleted, on the event the calendar
 * storage broadcasts.
 */
class AgendaCalendarLinkCleanupListenerTest {

  /**
   * The listener registers on the calendar deletion event and deletes the link
   * of the calendar it names.
   */
  @Test
  void aDeletedCalendarLosesItsLink() {
    ListenerService listenerService = mock(ListenerService.class);
    AgendaCalendarLinkService service = mock(AgendaCalendarLinkService.class);
    AgendaCalendarLinkCleanupListener listener = new AgendaCalendarLinkCleanupListener(listenerService, service);

    listener.init();
    Calendar calendar = new Calendar();
    calendar.setId(10);
    listener.onEvent(new Event<>("exo.agenda.calendar.deleted", calendar, null));

    verify(listenerService).addListener("exo.agenda.calendar.deleted", listener);
    verify(service).deleteCalendarLinks(10);
  }

  /**
   * An event with no calendar deletes nothing.
   */
  @Test
  void anEventWithoutACalendarDeletesNothing() {
    AgendaCalendarLinkService service = mock(AgendaCalendarLinkService.class);
    AgendaCalendarLinkCleanupListener listener = new AgendaCalendarLinkCleanupListener(mock(ListenerService.class), service);

    listener.onEvent(new Event<>("exo.agenda.calendar.deleted", null, null));

    verify(service, never()).deleteCalendarLinks(anyLong());
  }

}
