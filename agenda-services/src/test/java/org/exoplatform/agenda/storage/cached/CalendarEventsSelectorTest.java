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
package org.exoplatform.agenda.storage.cached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.services.cache.ObjectCacheInfo;

/**
 * The selection of the cached events of a calendar, which travels to the other nodes of a cluster.
 */
class CalendarEventsSelectorTest {

  @Test
  void selectsTheEventsOfItsCalendarOnly() {
    CalendarEventsSelector selector = new CalendarEventsSelector(3L);

    assertTrue(selector.select(1L, info(event(3L))));
    assertFalse(selector.select(2L, info(event(4L))));
    assertFalse(selector.select(5L, info(null)));
  }

  @Test
  void travelsAsTheSameSelection() throws Exception {
    CalendarEventsSelector selector = new CalendarEventsSelector(3L);

    Object received = overTheWire(selector);

    assertEquals(selector, received);
    assertEquals(selector.hashCode(), received.hashCode());
    assertNotEquals(selector, new CalendarEventsSelector(4L));
    assertTrue(((CalendarEventsSelector) received).select(1L, info(event(3L))));
  }

  private static Event event(long calendarId) {
    Event event = new Event();
    event.setCalendarId(calendarId);
    return event;
  }

  private static ObjectCacheInfo<Event> info(Event event) {
    return new ObjectCacheInfo<>() {
      @Override
      public long getExpireTime() {
        return -1;
      }

      @Override
      public Event get() {
        return event;
      }
    };
  }

  private static Object overTheWire(Object value) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return in.readObject();
    }
  }
}
