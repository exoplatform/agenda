/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventReminderComputingListenerTest extends BaseAgendaEventTest {

  @Test
  public void testComputeEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now();

    boolean allDay = false;
    String creatorUserName = testuser1Identity.getRemoteId();

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_CREATE_AGENDA_EVENT_EVENT, new Listener<Long, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<Long, Object> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderComputingListener().onEvent(event);
        }
      }
    });

    Event event = newEventInstance(start, start, allDay);
    try {
      event = createEvent(event, creatorUserName, testuser4Identity, testuser5Identity);
    } finally {
      executeListener.set(false);
    }

    List<Event> events = agendaEventService.getEventOccurrencesInPeriod(event,
                                                                        start,
                                                                        start.plusDays(2),
                                                                        start.getZone(),
                                                                        0);
    assertNotNull(events);
    assertEquals(0, events.size());
  }

}
