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
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
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

  /**
   * A place on the waiting list holds no seat, so no reminder; the promotion
   * that gives the seat gives the reminders with it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aWaitingAnswerHoldsNoReminderUntilItsPromotion() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(1);
    long eventId = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity).getId();
    long user4IdentityId = Long.parseLong(testuser4Identity.getId());
    long user5IdentityId = Long.parseLong(testuser5Identity.getId());

    AtomicBoolean executeListener = computeRemindersOnResponses();
    try {
      agendaEventAttendeeService.sendEventResponse(eventId, user4IdentityId, EventAttendeeResponse.ACCEPTED);
      agendaEventAttendeeService.sendEventResponse(eventId, user5IdentityId, EventAttendeeResponse.ACCEPTED);
      assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, user5IdentityId));
      assertTrue(agendaEventReminderService.getEventReminders(eventId, user5IdentityId).isEmpty());

      agendaEventAttendeeService.sendEventResponse(eventId, user4IdentityId, EventAttendeeResponse.DECLINED);
    } finally {
      executeListener.set(false);
    }

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, user5IdentityId));
    assertEquals(agendaUserSettingsService.getAgendaUserSettings(user5IdentityId).getReminders().size(),
                 agendaEventReminderService.getEventReminders(eventId, user5IdentityId).size());
    assertFalse(agendaEventReminderService.getEventReminders(eventId, user5IdentityId).isEmpty());
  }

  /**
   * An answer to a series is reported at its next date only, while the
   * reminders cover the whole series: an answer that waits at that date and
   * holds a seat at the later ones keeps the user's reminders, and a decline
   * of every date removes them.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesAnswerThatWaitsAtItsNextDateKeepsTheRemindersOfTheSeatsAfterIt() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    long seriesId = seriesFullBefore(1, start, start.plusHours(1));
    long user5IdentityId = Long.parseLong(testuser5Identity.getId());

    AtomicBoolean executeListener = computeRemindersOnResponses();
    try {
      agendaEventAttendeeService.sendEventResponse(seriesId, user5IdentityId, EventAttendeeResponse.ACCEPTED);
      assertWaitsAtTheNextDateAndHoldsTheSecondOne(seriesId, user5IdentityId);
      assertEquals(agendaUserSettingsService.getAgendaUserSettings(user5IdentityId).getReminders().size(),
                   agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).size());
      assertFalse(agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).isEmpty());

      agendaEventAttendeeService.sendEventResponse(seriesId, user5IdentityId, EventAttendeeResponse.DECLINED);
    } finally {
      executeListener.set(false);
    }

    assertTrue(agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).isEmpty());
  }

  /**
   * A row after the one valid at the next date starts where that one ends,
   * whatever the clock says: here the second date has begun while the first,
   * read as the next one, still runs, so the seat the second date starts
   * began before now and keeps the reminders all the same. An all-day id read
   * as an instant falls in the same place every evening west of UTC.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeatOnADateThatBeganAfterTheNextOneKeepsTheReminders() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().minusHours(30).withNano(0).withSecond(0).withMinute(0);
    long seriesId = seriesFullBefore(1, start, start.plusHours(48));
    long user5IdentityId = Long.parseLong(testuser5Identity.getId());

    AtomicBoolean executeListener = computeRemindersOnResponses();
    try {
      agendaEventAttendeeService.sendEventResponse(seriesId, user5IdentityId, EventAttendeeResponse.ACCEPTED);
    } finally {
      executeListener.set(false);
    }

    assertWaitsAtTheNextDateAndHoldsTheSecondOne(seriesId, user5IdentityId);
    assertTrue("the second date began before now", occurrenceIds(seriesId).get(1).isBefore(ZonedDateTime.now()));
    assertFalse(agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).isEmpty());
  }

  /**
   * A user whose rows all start after the next date holds no row valid at
   * it: their answer to the series reads as a copy answering nothing, and a
   * seat on any of their rows keeps the reminders.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aUserWhoseRowsAllStartAfterTheNextDateKeepsTheRemindersOfTheirSeat() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    long seriesId = seriesFullBefore(2, start, start.plusHours(1));
    long user5IdentityId = Long.parseLong(testuser5Identity.getId());
    List<ZonedDateTime> dates = occurrenceIds(seriesId);
    // Waiting at the second date, a seat from the third, and no row before
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, dates.get(1), user5IdentityId, EventAttendeeResponse.ACCEPTED);
    AgendaEventAttendeeStorage attendeeStorage = container.getComponentInstanceOfType(AgendaEventAttendeeStorage.class);
    for (EventAttendee row : agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees(user5IdentityId)) {
      if (row.getFromOccurrenceId() == null) {
        attendeeStorage.removeEventAttendee(row.getId());
      }
    }
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, agendaEventAttendeeService.getSeriesResponse(seriesId, user5IdentityId));
    assertTrue(agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).isEmpty());

    AtomicBoolean executeListener = computeRemindersOnResponses();
    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, dates.get(1), user5IdentityId, EventAttendeeResponse.ACCEPTED);
    } finally {
      executeListener.set(false);
    }

    List<EventAttendee> rows = agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees(user5IdentityId);
    assertTrue("no row before the second date", rows.stream().allMatch(row -> row.getFromOccurrenceId() != null));
    assertEquals(EventAttendeeResponse.WAITING,
                 agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendee(user5IdentityId, dates.get(1)).getResponse());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendee(user5IdentityId, dates.get(2)).getResponse());
    assertFalse(agendaEventReminderService.getEventReminders(seriesId, user5IdentityId).isEmpty());
  }

  /**
   * An open daily series of the space calendar, limited to one participant,
   * whose dates before the one given the fourth test user fills and whose
   * later dates are free.
   *
   * @param freeFrom the index of the first free date
   * @return the series identifier
   */
  private long seriesFullBefore(int freeFrom, ZonedDateTime start, ZonedDateTime end) throws Exception {
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(1);
    long seriesId = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity).getId();
    long user4IdentityId = Long.parseLong(testuser4Identity.getId());
    agendaEventAttendeeService.sendEventResponse(seriesId, user4IdentityId, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                         occurrenceIds(seriesId).get(freeFrom),
                                                         user4IdentityId,
                                                         EventAttendeeResponse.DECLINED);
    return seriesId;
  }

  private void assertWaitsAtTheNextDateAndHoldsTheSecondOne(long seriesId, long identityId) throws Exception {
    assertEquals(EventAttendeeResponse.WAITING, agendaEventAttendeeService.getSeriesResponse(seriesId, identityId));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventAttendees(seriesId)
                                           .getEventAttendee(identityId, occurrenceIds(seriesId).get(1))
                                           .getResponse());
  }

  /**
   * Runs the reminder listener on every answer saved, until the flag returned
   * is cleared.
   */
  private AtomicBoolean computeRemindersOnResponses() {
    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_EVENT_RESPONSE_SAVED, new Listener<EventAttendee, EventAttendee>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<EventAttendee, EventAttendee> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderListener().onEvent(event);
        }
      }
    });
    return executeListener;
  }

  private List<ZonedDateTime> occurrenceIds(long seriesId) {
    Event stored = agendaEventService.getEventById(seriesId);
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(5), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .toList();
  }

  private EventAttendeeResponse responseOf(long eventId, long identityId) {
    return agendaEventAttendeeService.getEventAttendees(eventId)
                                     .getEventAttendees()
                                     .stream()
                                     .filter(attendee -> attendee.getIdentityId() == identityId)
                                     .map(EventAttendee::getResponse)
                                     .findFirst()
                                     .orElse(null);
  }

}
