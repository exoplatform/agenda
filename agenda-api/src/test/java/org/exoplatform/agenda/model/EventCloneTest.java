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
package org.exoplatform.agenda.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;

/**
 * {@code Event.clone()} is what makes one occurrence of a recurring event able
 * to differ from its series: {@code AgendaEventServiceImpl
 * .createEventExceptionalOccurrence} clones the parent and edits the copy. A
 * field forgotten in that positional constructor call is therefore a field the
 * exception silently loses — for visibility, an occurrence the organiser had
 * marked private going back to publishing in full the moment they move it.
 * <p>
 * Pinned over every field rather than over the two this delivery adds, because
 * the shape of the defect is the constructor call and not the field:
 * {@code CalendarPermission.clone()} shipped with two of its arguments
 * transposed (EXO-90276) for exactly this reason.
 */
class EventCloneTest {

  /**
   * A clone carries every field of the event it copies.
   */
  @Test
  void aCloneCarriesEveryField() {
    Event event = new Event();
    event.setId(7);
    event.setParentId(3);
    event.setCalendarId(11);
    event.setCreatorId(21);
    event.setModifierId(22);
    event.setCreated(ZonedDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC));
    event.setUpdated(ZonedDateTime.of(2026, 9, 2, 8, 0, 0, 0, ZoneOffset.UTC));
    event.setSummary("Weekly sync");
    event.setDescription("<p>Agenda</p>");
    event.setLocation("Room 4");
    event.setColor("#FF0000");
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setStart(ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC));
    event.setAllDay(true);
    event.setAvailability(EventAvailability.FREE);
    event.setVisibility(EventVisibility.PRIVATE);
    event.setStatus(EventStatus.TENTATIVE);
    event.setAllowAttendeeToUpdate(true);
    event.setAllowAttendeeToInvite(true);

    assertEquals(event, event.clone());
  }

  /**
   * And the two values this delivery adds are carried each on its own, so a
   * failure names the field rather than printing two whole events.
   */
  @Test
  void aCloneCarriesTheAvailabilityAndTheVisibility() {
    Event event = new Event();
    event.setAvailability(EventAvailability.FREE);
    event.setVisibility(EventVisibility.PRIVATE);

    Event copy = event.clone();

    assertEquals(EventAvailability.FREE, copy.getAvailability(), "an occurrence keeps its series' availability");
    assertEquals(EventVisibility.PRIVATE, copy.getVisibility(), "and its visibility");
  }

}
