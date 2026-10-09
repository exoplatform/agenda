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
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.rest.model.EventSearchResultEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

/**
 * Event capacity limitation (eXIP 7.3.0.21): the capacity of an event and the
 * response date of an attendee travel through the four mappers that stand
 * between the HTTP payload, the model and the stored row
 * ({@code RestEntityBuilder} and {@code EntityMapper}, each way), where a field
 * forgotten in a positional constructor is silently dropped.
 * <p>
 * Two rules ride on them besides the copy. A capacity the client does not
 * state reaches the Service as null, which keeps the stored limit on update:
 * defaulting it to 0 on the way in would let any client that omits the field
 * remove a limit. And the response date is the server's: it goes out to the
 * reader and never comes in from a client, since it orders the waiting list.
 */
class EventCapacityMappingTest {

  private static final ZonedDateTime START   = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime REACHED = ZonedDateTime.of(2026, 9, 20, 18, 0, 0, 0, ZoneOffset.UTC);

  /**
   * A capacity the client states reaches the Service, and an omitted one
   * reaches it as "not specified", not as "no limit".
   */
  @Test
  void theRestPayloadCarriesTheCapacityInAndLeavesAnOmittedOneUnspecified() {
    EventEntity payload = payload();
    payload.setCapacity(12);

    assertEquals(Integer.valueOf(12), RestEntityBuilder.toEvent(payload).getCapacity());

    payload.setCapacity(null);

    assertNull(RestEntityBuilder.toEvent(payload).getCapacity(), "an omitted capacity is not a 0");
  }

  /**
   * The reached marker is never taken from a client, whatever the payload
   * carries: the conversion has no field to read it from, and sets none.
   */
  @Test
  void theRestPayloadNeverCarriesTheReachedMarkerIn() {
    EventEntity payload = payload();
    payload.setCapacity(12);

    assertNull(RestEntityBuilder.toEvent(payload).getCapacityReachedDate());
  }

  /**
   * A reader gets the capacity of the event read, and "no limit" as 0 when
   * the model holds none, never null.
   */
  @Test
  void theRestPayloadCarriesTheCapacityOut() {
    Event event = event();
    event.setCapacity(12);

    assertEquals(Integer.valueOf(12), fromEvent(event).getCapacity());

    event.setCapacity(null);

    assertEquals(Integer.valueOf(0), fromEvent(event).getCapacity(), "never null on read");
  }

  /**
   * A computed occurrence has no row: it shows its series' limit. A stored
   * occurrence is its date's own pool and shows its own.
   */
  @Test
  void anOccurrenceShowsTheCapacityOfItsPool() {
    EventEntity series = new EventEntity();
    series.setCapacity(12);
    Event computed = new Event();
    computed.setParentId(41L);
    computed.setCapacity(30);

    assertEquals(12, RestEntityBuilder.effectiveCapacity(series, computed), "a computed date shows its series' limit");

    Event stored = new Event();
    stored.setId(42L);
    stored.setParentId(41L);
    stored.setCapacity(30);

    assertEquals(30, RestEntityBuilder.effectiveCapacity(series, stored), "a stored date shows its own");
    assertEquals(30, RestEntityBuilder.effectiveCapacity(null, computed), "a standalone event shows its own");
  }

  /**
   * A search hit is built from the index, which carries no capacity: it says
   * "unknown", like the open flag there, never "no limit".
   */
  @Test
  void aSearchResultLeavesTheCapacityUnknown() {
    EventSearchResult hit = new EventSearchResult();
    hit.setId(7);
    hit.setTimeZoneId(ZoneOffset.UTC);
    hit.setStart(START);
    hit.setEnd(START.plusHours(1));
    hit.setCapacity(12);

    EventSearchResultEntity payload = RestEntityBuilder.fromSearchEvent(mock(AgendaCalendarService.class),
                                                                        mock(AgendaEventService.class),
                                                                        mock(IdentityManager.class),
                                                                        hit,
                                                                        ZoneOffset.UTC);

    assertNull(payload.getCapacity());
  }

  /**
   * The capacity and its reached marker survive the storage round trip, and a
   * capacity not specified is stored as no limit: the Service resolves it from
   * the stored row before an update gets there.
   */
  @Test
  void theStoredRowCarriesTheCapacityAndItsReachedMarker() {
    Event event = event();
    event.setStatus(EventStatus.CONFIRMED);
    event.setCapacity(12);
    event.setCapacityReachedDate(REACHED);

    org.exoplatform.agenda.entity.EventEntity stored = EntityMapper.toEntity(event);

    assertEquals(12, stored.getCapacity());
    assertEquals(Date.from(REACHED.toInstant()), stored.getCapacityReachedDate());

    stored.setCalendar(calendarEntity());
    stored.setStartDate(Date.from(START.toInstant()));
    stored.setEndDate(Date.from(START.plusHours(1).toInstant()));
    stored.setCreatedDate(Date.from(START.toInstant()));
    Event read = EntityMapper.fromEntity(stored);

    assertEquals(Integer.valueOf(12), read.getCapacity());
    assertEquals(REACHED.toInstant(), read.getCapacityReachedDate().toInstant());

    event.setCapacity(null);
    event.setCapacityReachedDate(null);
    stored = EntityMapper.toEntity(event);

    assertEquals(0, stored.getCapacity(), "a capacity not specified is no limit");
    assertNull(stored.getCapacityReachedDate());
  }

  /**
   * The response date survives the storage round trip of an attendee row.
   */
  @Test
  void theStoredAttendeeRowCarriesTheResponseDate() {
    EventAttendee attendee = new EventAttendee(5, 7, 9, START, null, EventAttendeeResponse.WAITING, REACHED);

    EventAttendeeEntity stored = EntityMapper.toEntity(attendee);

    assertEquals(Date.from(REACHED.toInstant()), stored.getResponseDate());

    EventAttendee read = EntityMapper.fromEntity(stored, 7);

    assertEquals(REACHED.toInstant(), read.getResponseDate().toInstant());

    stored.setResponseDate(null);

    assertNull(EntityMapper.fromEntity(stored, 7).getResponseDate(), "a row never answered since stays without");
  }

  /**
   * The response date goes out to the reader, and a date a client sends back
   * is ignored.
   */
  @Test
  void theResponseDateGoesOutAndNeverComesIn() throws Exception {
    EventAttendee attendee = new EventAttendee(5, 7, 9, START, null, EventAttendeeResponse.WAITING, REACHED);

    org.exoplatform.agenda.rest.model.EventAttendeeEntity out = RestEntityBuilder.fromEventAttendee(mock(IdentityManager.class),
                                                                                                     attendee);

    assertEquals(AgendaDateUtils.toRFC3339Date(REACHED), out.getResponseDate());

    IdentityEntity identity = new IdentityEntity();
    identity.setId("9");
    out.setIdentity(identity);
    EventAttendee in = RestEntityBuilder.toEventAttendee(mock(IdentityManager.class), 7, out);

    assertNull(in.getResponseDate(), "the order of a waiting list is never taken from a client");
  }

  private EventEntity fromEvent(Event event) {
    return RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class),
                                       mock(AgendaEventService.class),
                                       mock(IdentityManager.class),
                                       event,
                                       ZoneOffset.UTC);
  }

  private Event event() {
    Event event = new Event();
    event.setId(7);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setStart(START);
    event.setEnd(START.plusHours(1));
    return event;
  }

  private EventEntity payload() {
    EventEntity payload = new EventEntity();
    payload.setId(7);
    payload.setTimeZoneId(ZoneOffset.UTC.getId());
    payload.setStart(AgendaDateUtils.toRFC3339Date(START));
    payload.setEnd(AgendaDateUtils.toRFC3339Date(START.plusHours(1)));
    return payload;
  }

  private CalendarEntity calendarEntity() {
    CalendarEntity calendar = new CalendarEntity();
    calendar.setId(10L);
    return calendar;
  }

}
