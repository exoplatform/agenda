/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;

/**
 * Container-backed (real database, real services) tests of the multiple
 * personal calendars capability: a user owning several named calendars, the
 * system calendar staying the default, and calendar deletion moving events
 * instead of destroying them.
 */
public class AgendaPersonalCalendarsIntegrationTest extends BaseAgendaEventTest {

  /**
   * A user can own a second, named personal calendar next to the default one;
   * the name becomes the displayed title while the default keeps deriving its
   * title from the owner; the default resolution keeps returning the system
   * calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testSecondNamedPersonalCalendar() throws Exception { // NOSONAR
    String username = testuser1Identity.getRemoteId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    Calendar secondCalendar = new Calendar(0, userIdentityId, false, null, "second calendar", null, null, null, null);
    secondCalendar.setName("Side projects");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, username);
    try {
      assertNotNull(secondCalendar);
      assertFalse("A user-created calendar must not be a system calendar", secondCalendar.isSystem());
      assertEquals("The user-defined name must be the displayed title", "Side projects", secondCalendar.getTitle());
      assertEquals("Side projects", secondCalendar.getName());

      // The default calendar stays the system one, not the newly created row
      Calendar defaultCalendar = agendaCalendarService.getOrCreateCalendarByOwnerId(userIdentityId);
      assertEquals("The default calendar must stay the system calendar", calendar.getId(), defaultCalendar.getId());
      assertTrue(defaultCalendar.isSystem());

      // Renaming through the user update path
      secondCalendar.setName("Volunteering");
      agendaCalendarService.updateCalendar(secondCalendar, username);
      Calendar renamedCalendar = agendaCalendarService.getCalendarById(secondCalendar.getId(), username);
      assertEquals("Volunteering", renamedCalendar.getTitle());

      // A second calendar can't take an already used name
      Calendar duplicateCalendar = new Calendar(0, userIdentityId, false, null, null, null, null, null, null);
      duplicateCalendar.setName("volunteering");
      try {
        agendaCalendarService.createCalendar(duplicateCalendar, username);
        fail("Shouldn't allow two personal calendars with the same name");
      } catch (IllegalArgumentException e) {
        assertEquals("agenda.calendarNameAlreadyExists", e.getMessage());
      }
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * Deleting a user calendar must move its events to the owner's default
   * calendar — keeping every event alive — then delete the emptied calendar;
   * the system calendar itself must stay undeletable.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testDeleteUserCalendarMovesEventsToDefault() throws Exception { // NOSONAR
    String username = testuser1Identity.getRemoteId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    Calendar secondCalendar = new Calendar(0, userIdentityId, false, null, "second calendar", null, null, null, null);
    secondCalendar.setName("To delete");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, username);

    // File one event in the second calendar and one in the default calendar
    ZonedDateTime start = getDate();
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(secondCalendar.getId());
    Event movedEvent = createEvent(event.clone(), userIdentityId, testuser1Identity);

    Event untouchedEventInstance = newEventInstance(start, start.plusHours(1), false);
    untouchedEventInstance.setRecurrence(null);
    untouchedEventInstance.setCalendarId(calendar.getId());
    Event untouchedEvent = createEvent(untouchedEventInstance.clone(), userIdentityId, testuser1Identity);

    assertEquals(secondCalendar.getId(), movedEvent.getCalendarId());

    // Delete the second calendar as the user
    agendaCalendarService.deleteCalendarById(secondCalendar.getId(), username);
    restartTransaction();

    // The calendar is gone, its event is alive in the default calendar
    assertNull(agendaCalendarService.getCalendarById(secondCalendar.getId()));
    Event movedEventAfterDelete = agendaEventService.getEventById(movedEvent.getId());
    assertNotNull("The event must survive the deletion of its calendar", movedEventAfterDelete);
    assertEquals("The event must now belong to the owner's default calendar",
                 calendar.getId(),
                 movedEventAfterDelete.getCalendarId());
    Event untouchedEventAfterDelete = agendaEventService.getEventById(untouchedEvent.getId());
    assertEquals(calendar.getId(), untouchedEventAfterDelete.getCalendarId());

    // The default (system) calendar stays undeletable
    try {
      agendaCalendarService.deleteCalendarById(calendar.getId(), username);
      fail("Shouldn't allow to delete the system calendar");
    } catch (IllegalStateException e) {
      // Expected
    }
  }
}
