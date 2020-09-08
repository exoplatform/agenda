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
package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;

public class AgendaEventReminderServiceTest extends BaseAgendaEventTest {

  @Test
  public void testSaveEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser5Identity);

    long eventId = event.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    EventReminder eventReminder = eventReminders.get(0);
    assertNotNull(eventReminder);

    eventReminder = eventReminder.clone();
    eventReminder.setId(0);
    eventReminders.add(eventReminder);

    agendaEventReminderService.saveEventReminders(event, eventReminders, userIdentityId);
    eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(2, eventReminders.size());

    agendaEventReminderService.saveEventReminders(event, Collections.emptyList(), userIdentityId);
    eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(0, eventReminders.size());
  }

  @Test
  public void testGetEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser5Identity);

    long eventId = event.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    EventReminder eventReminderToStore = REMINDERS.get(0);

    EventReminder eventReminder = eventReminders.get(0);
    assertNotNull(eventReminder);
    assertTrue(eventReminder.getId() > 0);
    assertEquals(eventReminder.getBefore(), eventReminderToStore.getBefore());
    assertEquals(eventReminder.getBeforePeriodType(), eventReminderToStore.getBeforePeriodType());
    assertEquals(userIdentityId, eventReminder.getReceiverId());
    assertNotNull(eventReminder.getDatetime());
    assertEquals(event.getStart().minusMinutes(eventReminder.getBefore()).withZoneSameInstant(ZoneOffset.UTC),
                 eventReminder.getDatetime().withZoneSameInstant(ZoneOffset.UTC));
  }

}
