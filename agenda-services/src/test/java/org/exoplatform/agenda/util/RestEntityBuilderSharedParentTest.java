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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * The series of an occurrence is rendered for the same reader as the
 * occurrence (EXO-90357). An occurrence carries its series as
 * {@code parent}, built from the unchecked read by identifier; a colleague
 * admitted through a share alone must read a private series as busy time
 * there too, and the occurrence's own masking cannot decide it — an
 * exceptional occurrence and its series may differ in visibility — so the
 * reader's access, stamped on the occurrence, does.
 */
class RestEntityBuilderSharedParentTest {

  private static final long          SERIES = 5;

  private static final ZonedDateTime START  = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC);

  /**
   * A colleague reading a private series through a share alone gets its
   * occurrence's parent as busy time: no summary, description or location.
   */
  @Test
  void aPrivateSeriesIsBusyTimeOnTheOccurrenceOfASharee() {
    AgendaEventService eventService = eventServiceHolding(privateSeries());
    Event occurrence = Utils.maskForAccess(occurrenceOf(EventVisibility.PRIVATE), EventAccess.SHARED);

    EventEntity entity = RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class), eventService, mock(IdentityManager.class), occurrence, ZoneOffset.UTC);

    assertNotNull(entity.getParent(), "the occurrence still names its series");
    assertTrue(entity.getParent().isMasked(), "the series is masked for the sharee");
    assertFalse("Secret standup".equals(entity.getParent().getSummary()), "the series' title does not leave");
    // The builder sanitises a description on the way out: null becomes blank
    assertTrue(entity.getParent().getDescription() == null || entity.getParent().getDescription().isEmpty(), "no description");
    assertNull(entity.getParent().getLocation());
  }

  /**
   * The occurrence's own masking is not what decides: an exceptional
   * occurrence a colleague may read in full still renders its private series
   * as busy time, because the reader's access says so.
   */
  @Test
  void aReadableOccurrenceOfAPrivateSeriesStillMasksTheSeriesForASharee() {
    AgendaEventService eventService = eventServiceHolding(privateSeries());
    Event occurrence = Utils.maskForAccess(occurrenceOf(EventVisibility.DEFAULT), EventAccess.SHARED);
    assertFalse(occurrence.isMasked(), "the occurrence itself is readable");

    EventEntity entity = RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class), eventService, mock(IdentityManager.class), occurrence, ZoneOffset.UTC);

    assertTrue(entity.getParent().isMasked());
    assertFalse("Secret standup".equals(entity.getParent().getSummary()));
  }

  /**
   * A reader who could read before sharing existed — owner, member, attendee
   * — gets the series in full, as does a read on nobody's behalf.
   */
  @Test
  void aFullReaderAndAnAnonymousReadGetTheSeriesAsStored() {
    AgendaEventService eventService = eventServiceHolding(privateSeries());

    EventEntity forOwner = RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class),
                                                       eventService,
                                                       mock(IdentityManager.class),
                                                       Utils.maskForAccess(occurrenceOf(EventVisibility.PRIVATE), EventAccess.FULL),
                                                       ZoneOffset.UTC);
    EventEntity forNobody = RestEntityBuilder.fromEvent(mock(AgendaCalendarService.class),
                                                        eventService,
                                                        mock(IdentityManager.class),
                                                        occurrenceOf(EventVisibility.PRIVATE),
                                                        ZoneOffset.UTC);

    assertEquals("Secret standup", forOwner.getParent().getSummary());
    assertFalse(forOwner.getParent().isMasked());
    assertEquals("Secret standup", forNobody.getParent().getSummary());
  }

  /**
   * An event service answering the private series by identifier, as the
   * unchecked read does, a fresh copy on every read as the cache hands one.
   *
   * @param series the series
   * @return the service
   */
  private static AgendaEventService eventServiceHolding(Event series) {
    AgendaEventService eventService = mock(AgendaEventService.class);
    when(eventService.getEventById(SERIES)).thenAnswer(invocation -> series.clone());
    return eventService;
  }

  /**
   * A private recurring series with the content a sharee must not read.
   *
   * @return the series
   */
  private static Event privateSeries() {
    Event series = new Event();
    series.setId(SERIES);
    series.setCalendarId(0); // no calendar drawn: the builder reads none for id 0, as the sibling mapping test relies on
    series.setSummary("Secret standup");
    series.setDescription("Where the bodies are");
    series.setLocation("Room 42");
    series.setVisibility(EventVisibility.PRIVATE);
    series.setTimeZoneId(ZoneOffset.UTC);
    series.setStart(START);
    series.setEnd(START.plusHours(1));
    return series;
  }

  /**
   * A computed occurrence of the series, as {@code Utils.getOccurrences}
   * shapes one: no identifier of its own, the series as parent.
   *
   * @param visibility the occurrence's own visibility
   * @return the occurrence
   */
  private static Event occurrenceOf(EventVisibility visibility) {
    Event occurrence = new Event();
    occurrence.setId(0);
    occurrence.setParentId(SERIES);
    occurrence.setCalendarId(0);
    occurrence.setSummary("Secret standup");
    occurrence.setVisibility(visibility);
    occurrence.setTimeZoneId(ZoneOffset.UTC);
    occurrence.setStart(START.plusDays(7));
    occurrence.setEnd(START.plusDays(7).plusHours(1));
    occurrence.setOccurrence(new EventOccurrence(START.plusDays(7)));
    return occurrence;
  }

}
