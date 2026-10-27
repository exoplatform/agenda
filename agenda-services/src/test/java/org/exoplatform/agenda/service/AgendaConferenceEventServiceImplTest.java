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
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventConferenceMatch;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;

/**
 * Pins how a call finds its event: which links refer to it, which events the
 * reader keeps, and which occurrence of a recurring event a moment opens.
 * <p>
 * The occurrence dates are computed by the real recurrence engine
 * ({@code Utils.getOccurrences}), on series in several time zones; only the
 * reads that apply the reader's rights are stubbed. The refusal tests stub
 * the whole read path they refuse, so that dropping a guard makes them fail
 * on their own assertion rather than on a null.
 */
class AgendaConferenceEventServiceImplTest {

  private static final String              JITSI   = "jitsi";

  private static final String              CALL_ID = "g_space-abc";

  private static final String              LINK    = "https://host/jitsi/meet/" + CALL_ID + "?inviteId=1";

  private static final int                 GRACE   = 12;

  private static final long                READER  = 7L;

  private static final long                SINGLE  = 10L;

  private static final long                SERIES  = 20L;

  private static final long                STORED  = 21L;

  /** A Tuesday. */
  private static final LocalDate           DAY     = LocalDate.of(2026, 10, 27);

  private AgendaEventConferenceStorage     conferenceStorage;

  private AgendaEventService               agendaEventService;

  private AgendaConferenceEventServiceImpl service;

  /** The occurrences the reader reads, by their date's instant; any other is computed from the series. */
  private Map<Long, Event>                 storedOccurrences;

  /** The meeting link of an event, by its identifier; any other event holds the call's link. */
  private Map<Long, String>                ownLinks;

  @BeforeEach
  void setUp() {
    conferenceStorage = mock(AgendaEventConferenceStorage.class);
    agendaEventService = mock(AgendaEventService.class);
    service = new AgendaConferenceEventServiceImpl(conferenceStorage, agendaEventService);
    storedOccurrences = new HashMap<>();
    ownLinks = new HashMap<>();
    when(conferenceStorage.getEventConferences(anyLong())).thenAnswer(invocation -> {
      long eventId = invocation.getArgument(0);
      return List.of(new EventConference(1, eventId, JITSI, ownLinks.getOrDefault(eventId, LINK), null, null, null));
    });
  }

  /**
   * The call identifier must be a whole path segment of the link: followed
   * by its end, a query string, a fragment or another segment, and never the
   * beginning of a longer identifier, even when the longer one comes first.
   */
  @Test
  void aLinkRefersToTheCallOnlyThroughAWholeSegment() {
    assertTrue(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/abc", "abc"));
    assertTrue(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/abc?inviteId=1", "abc"));
    assertTrue(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/abc#x", "abc"));
    assertTrue(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/abc/join", "abc"));
    assertTrue(AgendaConferenceEventServiceImpl.holdsCallId("https://h/abcd/meet/abc", "abc"),
               "a later whole segment counts when an earlier one only starts with the identifier");
    assertFalse(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/abcd", "abc"));
    assertFalse(AgendaConferenceEventServiceImpl.holdsCallId("https://h/jitsi/meet/xabc", "abc"));
    assertFalse(AgendaConferenceEventServiceImpl.holdsCallId(null, "abc"));
  }

  /**
   * The storage is asked for the call's type and for the identifier as a
   * segment, and a link only starting with the identifier answers nothing.
   *
   * @throws Exception never
   */
  @Test
  void aLongerCallIdentifierFindsNothing() throws Exception {
    when(conferenceStorage.findConferencesContaining(JITSI, "/" + CALL_ID, AgendaConferenceEventServiceImpl.MATCH_LIMIT))
                                                                                                                    .thenReturn(List.of(new EventConferenceMatch("https://host/jitsi/meet/"
                                                                                                                        + CALL_ID + "d", SINGLE)));

    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 10, 30), GRACE, READER));
    verify(agendaEventService, never()).getEventById(anyLong());
  }

  /**
   * A single event the reader can read is answered, at its own moment and
   * outside it alike: the call belongs to it whenever it was held.
   *
   * @throws Exception never
   */
  @Test
  void aReadableSingleEventIsAnsweredWheneverTheCallWasHeld() throws Exception {
    Event single = single(SINGLE, at(ZoneOffset.UTC, 10, 0));
    matches(SINGLE);
    stored(single);
    when(agendaEventService.getEventById(SINGLE, ZoneOffset.UTC, READER)).thenReturn(single);

    assertSame(single, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 10, 30), GRACE, READER));
    assertSame(single, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 10, 30).plusDays(3), GRACE, READER));
  }

  /**
   * An event the reader cannot read answers nothing.
   *
   * @throws Exception never
   */
  @Test
  void anEventTheReaderCannotReadAnswersNothing() throws Exception {
    matches(SINGLE);
    stored(single(SINGLE, at(ZoneOffset.UTC, 10, 0)));
    when(agendaEventService.getEventById(SINGLE, ZoneOffset.UTC, READER)).thenThrow(new IllegalAccessException());

    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 10, 30), GRACE, READER));
  }

  /**
   * An event the reader reads only as busy time answers nothing: its title
   * and its meeting are hidden from them.
   *
   * @throws Exception never
   */
  @Test
  void anEventReadAsBusyTimeAnswersNothing() throws Exception {
    Event masked = single(SINGLE, at(ZoneOffset.UTC, 10, 0));
    masked.setMasked(true);
    matches(SINGLE);
    stored(single(SINGLE, at(ZoneOffset.UTC, 10, 0)));
    when(agendaEventService.getEventById(SINGLE, ZoneOffset.UTC, READER)).thenReturn(masked);

    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 10, 30), GRACE, READER));
  }

  /**
   * For a daily meeting, the occurrence of the moment's day is answered,
   * not the previous one nor the series, in every time zone: the days are
   * those of the series' own zone, so a morning meeting east of UTC, whose
   * day starts before the UTC one, is found too.
   *
   * @throws Exception never
   */
  @Test
  void theOccurrenceOfTheMomentIsAnsweredInEveryTimeZone() throws Exception {
    for (String zone : List.of("UTC", "Europe/Paris", "America/New_York", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland")) {
      setUp();
      ZoneId zoneId = ZoneId.of(zone);
      Event series = daily(zoneId, 9);
      readableSeries(series);

      Event answer = service.getConferenceEvent(JITSI, CALL_ID, at(zoneId, 9, 30), GRACE, READER);

      assertNotNull(answer, zone);
      assertNotNull(answer.getOccurrence(), zone + ": an occurrence, not the series");
      assertEquals(at(zoneId, 9, 0).toInstant(), answer.getStart().toInstant(), zone + ": the occurrence of the moment's day");
    }
  }

  /**
   * An early meeting, starting right after midnight in the series' zone, is
   * found from a moment later that morning.
   *
   * @throws Exception never
   */
  @Test
  void anEarlyMorningOccurrenceIsFound() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = daily(tokyo, 0);
    readableSeries(series);

    Event answer = service.getConferenceEvent(JITSI, CALL_ID, at(tokyo, 0, 30), GRACE, READER);

    assertNotNull(answer.getOccurrence());
    assertEquals(at(tokyo, 0, 0).toInstant(), answer.getStart().toInstant());
  }

  /**
   * The occurrence still spans the moment until the grace period after its
   * end, and no longer: then the series is answered. The grace is the
   * caller's.
   *
   * @throws Exception never
   */
  @Test
  void theGracePeriodIsTheCallers() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    ZonedDateTime end = at(ZoneOffset.UTC, 10, 0);

    assertNotNull(service.getConferenceEvent(JITSI, CALL_ID, end.plusHours(12), GRACE, READER).getOccurrence());
    assertNotNull(service.getConferenceEvent(JITSI, CALL_ID, end.plusHours(3), 3, READER).getOccurrence());
    assertNull(service.getConferenceEvent(JITSI, CALL_ID, end.plusHours(3).plusMinutes(1), 3, READER).getOccurrence(),
               "past the caller's grace, and before the next day's occurrence, the series");
    assertSame(series, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 8, 59), 0, READER),
               "with no grace, a moment before today's occurrence spans nothing");
  }

  /**
   * The occurrences looked at go back as far as the grace asks: a weekly
   * meeting is found two and a half days after its end with a three-day
   * grace, and the series is answered with a two-day one.
   *
   * @throws Exception never
   */
  @Test
  void theWindowGrowsWithTheGrace() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    series.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    readableSeries(series);
    ZonedDateTime moment = at(ZoneOffset.UTC, 10, 0).plusHours(60);

    Event answer = service.getConferenceEvent(JITSI, CALL_ID, moment, 72, READER);
    assertNotNull(answer.getOccurrence(), "the occurrence of the week, within a three-day grace");
    assertEquals(at(ZoneOffset.UTC, 9, 0).toInstant(), answer.getStart().toInstant());
    assertSame(series, service.getConferenceEvent(JITSI, CALL_ID, moment, 48, READER));
  }

  /**
   * A stored occurrence moved to another time keeps the date it was computed
   * for, and is answered at its new time.
   *
   * @throws Exception never
   */
  @Test
  void aMovedStoredOccurrenceIsAnsweredAtItsNewTime() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    Event moved = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 14, 0));
    storedOccurrences.put(at(ZoneOffset.UTC, 9, 0).toInstant().toEpochMilli(), moved);

    assertSame(moved, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 14, 30), 0, READER));
  }

  /**
   * A stored occurrence with a meeting link of its own, to another call, is
   * not the meeting of a recording of the series' call: the series is
   * answered.
   *
   * @throws Exception never
   */
  @Test
  void anOccurrenceHoldingAnotherCallIsNotAnswered() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    Event other = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 9, 0));
    storedOccurrences.put(at(ZoneOffset.UTC, 9, 0).toInstant().toEpochMilli(), other);
    ownLinks.put(STORED, "https://host/jitsi/meet/g_space-other");

    assertSame(series, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), 0, READER));
  }

  /**
   * When only a stored occurrence holds the call, the series having another
   * one, that occurrence is answered at its moment, and nothing at any other
   * moment: neither the series nor its computed occurrences are the call's.
   *
   * @throws Exception never
   */
  @Test
  void aSeriesHoldingAnotherCallIsNeitherAnOccurrenceNorAFallback() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    ownLinks.put(SERIES, "https://host/jitsi/meet/g_space-other");
    Event own = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 9, 0));
    storedOccurrences.put(at(ZoneOffset.UTC, 9, 0).toInstant().toEpochMilli(), own);

    assertSame(own, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), 0, READER));
    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30).minusDays(1), 0, READER),
               "the day before, the computed occurrence holds the series' call, not this one");
  }

  /**
   * A series whose own link to the call is of another conference type, a
   * manual one pasted with the same address, does not hold the call.
   *
   * @throws Exception never
   */
  @Test
  void aLinkOfAnotherTypeDoesNotHoldTheCall() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    when(conferenceStorage.getEventConferences(SERIES)).thenReturn(List.of(new EventConference(1, SERIES, "manual", LINK, null, null, null)));

    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), 0, READER));
  }

  /**
   * A cancelled occurrence does not span anything: the series is answered.
   *
   * @throws Exception never
   */
  @Test
  void aCancelledOccurrenceIsSkipped() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    Event cancelled = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 9, 0));
    cancelled.setStatus(EventStatus.CANCELLED);
    storedOccurrences.put(at(ZoneOffset.UTC, 9, 0).toInstant().toEpochMilli(), cancelled);

    assertSame(series, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), 0, READER));
  }

  /**
   * An occurrence the reader reads only as busy time is left out.
   *
   * @throws Exception never
   */
  @Test
  void anOccurrenceReadAsBusyTimeIsSkipped() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    readableSeries(series);
    Event masked = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 9, 0));
    masked.setMasked(true);
    storedOccurrences.put(at(ZoneOffset.UTC, 9, 0).toInstant().toEpochMilli(), masked);

    assertSame(series, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), 0, READER));
  }

  /**
   * A reader invited to one occurrence only, who cannot read the series,
   * gets that occurrence when it spans the moment, and nothing otherwise:
   * never the series.
   *
   * @throws Exception never
   */
  @Test
  void aReaderOfOneOccurrenceOnlyGetsThatOccurrence() throws Exception {
    Event series = daily(ZoneOffset.UTC, 9);
    matches(SERIES);
    stored(series);
    when(agendaEventService.getEventById(SERIES, ZoneOffset.UTC, READER)).thenThrow(new IllegalAccessException());
    Event invited = occurrence(STORED, at(ZoneOffset.UTC, 9, 0), at(ZoneOffset.UTC, 9, 0));
    when(agendaEventService.getEventOccurrence(eq(SERIES), any(), eq(ZoneOffset.UTC), eq(READER))).thenAnswer(invocation -> {
      ZonedDateTime occurrenceId = invocation.getArgument(1);
      if (occurrenceId.toInstant().equals(at(ZoneOffset.UTC, 9, 0).toInstant())) {
        return invited;
      }
      throw new IllegalAccessException();
    });

    assertSame(invited, service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30), GRACE, READER));
    assertNull(service.getConferenceEvent(JITSI, CALL_ID, at(ZoneOffset.UTC, 9, 30).plusDays(2), GRACE, READER));
  }

  /**
   * Among several events of one call, the one spanning the moment wins; with
   * none spanning it, the one started last before it.
   *
   * @throws Exception never
   */
  @Test
  void amongSeveralEventsTheOneOfTheMomentWins() throws Exception {
    ZonedDateTime monday = at(ZoneOffset.UTC, 10, 0).minusDays(1);
    ZonedDateTime tuesday = at(ZoneOffset.UTC, 10, 0);
    Event early = single(SINGLE, monday);
    Event late = single(SINGLE + 1, tuesday);
    matches(SINGLE, SINGLE + 1);
    stored(early);
    stored(late);
    when(agendaEventService.getEventById(SINGLE, ZoneOffset.UTC, READER)).thenReturn(early);
    when(agendaEventService.getEventById(SINGLE + 1, ZoneOffset.UTC, READER)).thenReturn(late);

    assertSame(early, service.getConferenceEvent(JITSI, CALL_ID, monday.plusMinutes(30), GRACE, READER));
    assertSame(late, service.getConferenceEvent(JITSI, CALL_ID, tuesday.plusDays(2), GRACE, READER));
    assertSame(early, service.getConferenceEvent(JITSI, CALL_ID, tuesday.minusHours(2), 0, READER),
               "between the two, the one started last before the moment");
    assertSame(early, service.getConferenceEvent(JITSI, CALL_ID, monday.minusDays(3), GRACE, READER),
               "before both, the first one found");
  }

  /**
   * A missing type, call identifier or moment, and a grace out of 0 to 168
   * hours, are refused with their code.
   */
  @Test
  void badInputsAreRefused() {
    ZonedDateTime moment = at(ZoneOffset.UTC, 10, 0);
    assertEquals(AgendaConferenceEventServiceImpl.MISSING_PROVIDER_TYPE,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.getConferenceEvent(" ", CALL_ID, moment, GRACE, READER)).getMessage());
    assertEquals(AgendaConferenceEventServiceImpl.MISSING_CALL_ID,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.getConferenceEvent(JITSI, "", moment, GRACE, READER)).getMessage());
    assertEquals(AgendaConferenceEventServiceImpl.MISSING_MOMENT,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.getConferenceEvent(JITSI, CALL_ID, null, GRACE, READER)).getMessage());
    assertEquals(AgendaConferenceEventServiceImpl.NEGATIVE_GRACE,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.getConferenceEvent(JITSI, CALL_ID, moment, -1, READER)).getMessage());
    assertEquals(AgendaConferenceEventServiceImpl.GRACE_TOO_LONG,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.getConferenceEvent(JITSI, CALL_ID, moment, 169, READER)).getMessage());
    verify(conferenceStorage, never()).findConferencesContaining(anyString(), anyString(), anyInt());
  }

  /**
   * A time of {@link #DAY} in a zone.
   *
   * @param zone the zone
   * @param hour the hour
   * @param minute the minute
   * @return the moment
   */
  private static ZonedDateTime at(ZoneId zone, int hour, int minute) {
    return DAY.atTime(LocalTime.of(hour, minute)).atZone(zone);
  }

  /**
   * Stubs the links the storage finds for the call, one per series.
   *
   * @param seriesIds the series of the links
   */
  private void matches(long... seriesIds) {
    when(conferenceStorage.findConferencesContaining(JITSI, "/" + CALL_ID, AgendaConferenceEventServiceImpl.MATCH_LIMIT))
                                                                                                                    .thenReturn(java.util.Arrays.stream(seriesIds)
                                                                                                                                                .mapToObj(id -> new EventConferenceMatch(LINK,
                                                                                                                                                                                         id))
                                                                                                                                                .toList());
  }

  /**
   * Stubs the stored event, as read with no reader.
   *
   * @param event the event
   */
  private void stored(Event event) {
    when(agendaEventService.getEventById(event.getId())).thenReturn(event);
  }

  /**
   * Stubs a series the reader reads, its link found for the call, and its
   * occurrences as the reader reads them: a stored one when registered in
   * {@link #storedOccurrences}, else the occurrence computed for the date.
   *
   * @param series the series
   * @throws Exception never
   */
  private void readableSeries(Event series) throws Exception {
    matches(SERIES);
    stored(series);
    when(agendaEventService.getEventById(SERIES, ZoneOffset.UTC, READER)).thenReturn(series);
    when(agendaEventService.getEventOccurrence(eq(SERIES), any(), eq(ZoneOffset.UTC), eq(READER))).thenAnswer(invocation -> {
      ZonedDateTime occurrenceId = invocation.getArgument(1);
      Event stored = storedOccurrences.get(occurrenceId.toInstant().toEpochMilli());
      return stored != null ? stored : occurrence(0, occurrenceId, occurrenceId);
    });
  }

  /**
   * Builds a daily one-hour series in a zone, started a week before
   * {@link #DAY}.
   *
   * @param zone the series' zone
   * @param hour the hour it starts at
   * @return the series
   */
  private static Event daily(ZoneId zone, int hour) {
    Event series = single(SERIES, at(zone, hour, 0).minusDays(7));
    series.setTimeZoneId(zone);
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);
    series.setRecurrence(recurrence);
    return series;
  }

  /**
   * Builds a confirmed one-hour single event.
   *
   * @param id the event identifier
   * @param start the start
   * @return the event
   */
  private static Event single(long id, ZonedDateTime start) {
    Event event = new Event();
    event.setId(id);
    event.setStart(start);
    event.setEnd(start.plusHours(1));
    event.setTimeZoneId(start.getZone());
    event.setStatus(EventStatus.CONFIRMED);
    return event;
  }

  /**
   * Builds a confirmed one-hour occurrence of {@link #SERIES}.
   *
   * @param id the stored occurrence's identifier, 0 for a computed one
   * @param occurrenceId the date it was computed for
   * @param start its start
   * @return the occurrence
   */
  private static Event occurrence(long id, ZonedDateTime occurrenceId, ZonedDateTime start) {
    Event occurrence = single(id, start);
    occurrence.setParentId(SERIES);
    occurrence.setOccurrence(new EventOccurrence(occurrenceId, id > 0, false));
    return occurrence;
  }

}
