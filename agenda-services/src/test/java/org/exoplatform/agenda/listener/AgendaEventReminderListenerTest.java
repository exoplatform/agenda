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

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventReminderListenerTest extends BaseAgendaEventTest {

  @Test
  public void testAddUserReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);

    long eventId = event.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_EVENT_RESPONSE_SAVED, new Listener<EventAttendee, EventAttendee>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<EventAttendee, EventAttendee> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderListener().onEvent(event);
        }
      }
    });

    long user5IdentityId = Long.parseLong(testuser5Identity.getId());
    try {
      agendaEventAttendeeService.sendEventResponse(eventId,
                                                   user5IdentityId,
                                                   EventAttendeeResponse.ACCEPTED);
    } finally {
      executeListener.set(false);
    }

    eventReminders = agendaEventReminderService.getEventReminders(eventId, user5IdentityId);
    assertNotNull(eventReminders);
    assertEquals(agendaUserSettingsService.getDefaultReminders().size(), eventReminders.size());

    executeListener.set(true);
    try {
      agendaEventAttendeeService.sendEventResponse(eventId,
                                                   user5IdentityId,
                                                   EventAttendeeResponse.DECLINED);
    } finally {
      executeListener.set(false);
    }
    eventReminders = agendaEventReminderService.getEventReminders(eventId, user5IdentityId);
    assertTrue(eventReminders.isEmpty());

    List<EventReminderParameter> userDefaultRemindersSettings =
                                                              Collections.singletonList(new EventReminderParameter(2,
                                                                                                                   ReminderPeriodType.DAY));

    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(user5IdentityId);
    agendaUserSettings.setReminders(userDefaultRemindersSettings);
    agendaUserSettingsService.saveAgendaUserSettings(user5IdentityId, agendaUserSettings);

    executeListener.set(true);
    try {
      agendaEventAttendeeService.sendEventResponse(eventId,
                                                   user5IdentityId,
                                                   EventAttendeeResponse.ACCEPTED);
    } finally {
      executeListener.set(false);
    }
    eventReminders = agendaEventReminderService.getEventReminders(eventId, user5IdentityId);
    assertEquals(userDefaultRemindersSettings.size(), eventReminders.size());
  }

}
