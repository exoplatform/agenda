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

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.*;

public class AgendaEventReminderServiceTest extends BaseAgendaEventTest {

  @Test
  public void testSaveEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

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
  public void testSaveRecurrentEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2),
                                                     0,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null);
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    long eventId = event.getId();
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(eventId,
                                                                                    start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    long exceptionalOccurrenceId = exceptionalOccurrence.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    List<EventReminder> exceptionalOccurrenceReminders = agendaEventReminderService.getEventReminders(exceptionalOccurrenceId,
                                                                                                      userIdentityId);
    assertNotNull(exceptionalOccurrenceReminders);
    assertEquals(1, exceptionalOccurrenceReminders.size());

    EventReminder eventReminder = eventReminders.get(0);
    assertNotNull(eventReminder);

    eventReminder = eventReminder.clone();
    eventReminder.setId(0);
    eventReminders.add(eventReminder);

    agendaEventReminderService.saveEventReminders(event, eventReminders, userIdentityId);

    eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(2, eventReminders.size());
    exceptionalOccurrenceReminders = agendaEventReminderService.getEventReminders(exceptionalOccurrenceId, userIdentityId);
    assertNotNull(exceptionalOccurrenceReminders);
    assertEquals(2, exceptionalOccurrenceReminders.size());

    exceptionalOccurrenceReminders.remove(0);
    agendaEventReminderService.saveEventReminders(exceptionalOccurrence, exceptionalOccurrenceReminders, userIdentityId);

    eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(2, eventReminders.size());
    exceptionalOccurrenceReminders = agendaEventReminderService.getEventReminders(exceptionalOccurrenceId, userIdentityId);
    assertNotNull(exceptionalOccurrenceReminders);
    assertEquals(1, exceptionalOccurrenceReminders.size());

    agendaEventReminderService.saveEventReminders(event, Collections.emptyList(), userIdentityId);
    eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(0, eventReminders.size());
    exceptionalOccurrenceReminders = agendaEventReminderService.getEventReminders(exceptionalOccurrenceId, userIdentityId);
    assertNotNull(exceptionalOccurrenceReminders);
    assertEquals(0, exceptionalOccurrenceReminders.size());
  }

  @Test
  public void testGetEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

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

  @Test
  public void testGetDefaultReminders() throws Exception { // NOSONAR
    List<EventReminderParameter> defaultReminders = agendaEventReminderService.getDefaultReminders();
    assertNotNull(defaultReminders);
    assertFalse(defaultReminders.isEmpty());

    try {
      defaultReminders.add(new EventReminderParameter());
      fail("Shouldn't allow list modification");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void testSaveUserReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);

    long eventId = event.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    long user4IdentityId = Long.parseLong(testuser4Identity.getId());

    eventReminders = Collections.singletonList(new EventReminder(user4IdentityId, 2, ReminderPeriodType.DAY));
    agendaEventReminderService.saveEventReminders(event, eventReminders, user4IdentityId);

    eventReminders = agendaEventReminderService.getEventReminders(eventId, user4IdentityId);
    assertEquals(1, eventReminders.size());

    eventReminders = agendaEventReminderService.getEventReminders(eventId);
    assertEquals(2, eventReminders.size());

    agendaEventReminderService.removeUserReminders(eventId, user4IdentityId);
    eventReminders = agendaEventReminderService.getEventReminders(eventId);
    assertEquals(1, eventReminders.size());

    eventReminders = agendaEventReminderService.getEventReminders(eventId, user4IdentityId);
    assertEquals(0, eventReminders.size());
  }

}
