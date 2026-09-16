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
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.rest.model.EventSearchResultEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * EXO-90327 / EXO-90322: the two values a user sets on the form row travel the
 * whole way — in through the REST payload, down to the stored row, and back out
 * to whoever reads the event.
 * <p>
 * <strong>Why the mappers and not the endpoint.</strong> The only thing between
 * the HTTP payload and the model is {@code RestEntityBuilder.toEvent}
 * ({@code RestUtils.createEventEntity} and {@code AgendaEventRest.updateEvent}
 * both call it and nothing else on the way), and the only thing between the
 * model and the stored row is {@code EntityMapper}. A field is dropped by
 * forgetting it in one of those four positional constructors, and that is what
 * these pin. What they cannot say is that Jackson binds the JSON name, which a
 * live request shows.
 */
class EventAvailabilityVisibilityMappingTest {

  private static final ZonedDateTime START = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC);

  /**
   * What a client posts is what the service is handed: both values survive the
   * REST payload to model conversion.
   */
  @Test
  void theRestPayloadCarriesBothValuesIn() {
    org.exoplatform.agenda.rest.model.EventEntity payload = new org.exoplatform.agenda.rest.model.EventEntity();
    payload.setId(7);
    payload.setTimeZoneId(ZoneOffset.UTC.getId());
    payload.setStart(AgendaDateUtils.toRFC3339Date(START));
    payload.setEnd(AgendaDateUtils.toRFC3339Date(START.plusHours(1)));
    payload.setAvailability(EventAvailability.FREE);
    payload.setVisibility(EventVisibility.PRIVATE);

    Event event = RestEntityBuilder.toEvent(payload);

    assertEquals(EventAvailability.FREE, event.getAvailability());
    assertEquals(EventVisibility.PRIVATE, event.getVisibility());
  }

  /**
   * And what the service holds is what a reader gets back: both values survive
   * the model to REST payload conversion.
   */
  @Test
  void theRestPayloadCarriesBothValuesOut() {
    Event event = new Event();
    event.setId(7);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setStart(START);
    event.setEnd(START.plusHours(1));
    event.setAvailability(EventAvailability.FREE);
    event.setVisibility(EventVisibility.PRIVATE);

    org.exoplatform.agenda.rest.model.EventEntity payload =
                                                          RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class),
                                                                                      mock(AgendaEventService.class),
                                                                                      mock(IdentityManager.class),
                                                                                      event,
                                                                                      ZoneOffset.UTC);

    assertEquals(EventAvailability.FREE, payload.getAvailability());
    assertEquals(EventVisibility.PRIVATE, payload.getVisibility());
  }

  /**
   * A search hit is built by a second, separate positional constructor call,
   * which this pins on its own.
   * <p>
   * <strong>A guard, not a live path.</strong> Unified search answers null for
   * both values whatever this constructor does, because nothing upstream fills
   * them: {@code AgendaSearchConnector} builds every {@code EventSearchResult}
   * from the Elasticsearch {@code _source}, which the indexing connector never
   * writes availability or visibility into, and
   * {@code AgendaEventServiceImpl.search} then copies only the summary,
   * description, location and dates from the resolved occurrence. Whether the
   * index should carry them is a decision about the mapping, not about this
   * call. What the pin protects is the coupling: the day it does, a field
   * forgotten here would make search answer null for an event that carries the
   * value when read by id, and nothing else would notice.
   */
  @Test
  void aSearchResultCarriesBothValuesOut() {
    EventSearchResult hit = new EventSearchResult();
    hit.setId(7);
    hit.setTimeZoneId(ZoneOffset.UTC);
    hit.setStart(START);
    hit.setEnd(START.plusHours(1));
    hit.setAvailability(EventAvailability.FREE);
    hit.setVisibility(EventVisibility.PRIVATE);

    EventSearchResultEntity payload = RestEntityBuilder.fromSearchEvent(mock(AgendaCalendarService.class),
                                                                        mock(AgendaEventService.class),
                                                                        mock(IdentityManager.class),
                                                                        hit,
                                                                        ZoneOffset.UTC);

    assertEquals(EventAvailability.FREE, payload.getAvailability());
    assertEquals(EventVisibility.PRIVATE, payload.getVisibility());
  }

  /**
   * Both values survive the storage round trip, so what the feed and the
   * connectors read tomorrow is what the user chose today.
   */
  @Test
  void theStoredRowCarriesBothValues() {
    Event event = new Event();
    event.setId(7);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setStart(START);
    event.setEnd(START.plusHours(1));
    event.setStatus(EventStatus.CONFIRMED);
    event.setAvailability(EventAvailability.FREE);
    event.setVisibility(EventVisibility.PRIVATE);

    org.exoplatform.agenda.entity.EventEntity stored = EntityMapper.toEntity(event);

    assertEquals(EventAvailability.FREE, stored.getAvailability(), "the row keeps the availability");
    assertEquals(EventVisibility.PRIVATE, stored.getVisibility(), "and the visibility");

    stored.setCalendar(calendarEntity());
    stored.setStartDate(Date.from(START.toInstant()));
    stored.setEndDate(Date.from(START.plusHours(1).toInstant()));
    stored.setCreatedDate(Date.from(START.toInstant()));
    Event read = EntityMapper.fromEntity(stored);

    assertEquals(EventAvailability.FREE, read.getAvailability(), "and gives them back");
    assertEquals(EventVisibility.PRIVATE, read.getVisibility());
  }

  /**
   * An event a client sends with neither value set arrives with neither set —
   * the mappers invent nothing. Defaulting is the service's job
   * ({@code AgendaEventServiceImpl} writes BUSY-meaning DEFAULT and
   * {@link EventVisibility#DEFAULT}), and a mapper quietly doing it too would
   * hide a service that stopped.
   */
  @Test
  void theMappersDefaultNothing() {
    org.exoplatform.agenda.rest.model.EventEntity payload = new org.exoplatform.agenda.rest.model.EventEntity();
    payload.setTimeZoneId(ZoneOffset.UTC.getId());
    payload.setStart(AgendaDateUtils.toRFC3339Date(START));
    payload.setEnd(AgendaDateUtils.toRFC3339Date(START.plusHours(1)));

    Event event = RestEntityBuilder.toEvent(payload);

    assertNull(event.getAvailability());
    assertNull(event.getVisibility());
  }

  /**
   * A calendar row the entity mapper can read an identifier and a time zone
   * from.
   *
   * @return the row
   */
  private CalendarEntity calendarEntity() {
    CalendarEntity calendar = new CalendarEntity();
    calendar.setId(10L);
    return calendar;
  }

}
