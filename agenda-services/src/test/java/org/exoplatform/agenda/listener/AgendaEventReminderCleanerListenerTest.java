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

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventReminderCleanerListenerTest extends BaseAgendaEventTest {

  @Test
  public void testCleanEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), userIdentityId, testuser4Identity, testuser5Identity);

    long eventId = event.getId();
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_UPDATE_AGENDA_EVENT_EVENT, new Listener<Long, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<Long, Object> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderCleanerListener().onEvent(event);
        }
      }
    });

    try {
      event.setStatus(EventStatus.CANCELLED);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     eventReminders,
                                     Collections.emptyList(),
                                     null,
                                     false,
                                     userIdentityId);
    } finally {
      executeListener.set(false);
    }
    eventReminders = agendaEventReminderService.getEventReminders(eventId);
    assertNotNull(eventReminders);
    assertTrue(eventReminders.isEmpty());
  }

}
