/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.ScheduleConflict;
import org.exoplatform.agenda.model.ScheduleConflictEvent;
import org.exoplatform.agenda.model.ScheduleConflicts;

/**
 * Pins the interval arithmetic this service exists to own.
 * <p>
 * The single most valuable assertion in this file is
 * {@link #backToBackEventsDoNotConflict()}: adjacency reported as a clash is
 * the error the tool was built to remove, and loosening either comparison in
 * the overlap rule must fail here loudly rather than quietly turn every normal
 * morning into a double booking.
 * <p>
 * The tests read events straight from a mocked
 * {@link AgendaEventService}, which is what the window query and the calendar
 * ACL are; what is exercised here is everything this service decides
 * <em>after</em> that read.
 */
class AgendaScheduleConflictServiceImplTest {

  private static final long                       USER         = 5L;

  private static final long                       COLLEAGUE    = 400L;

  private static final ZonedDateTime              WINDOW_START = ZonedDateTime.of(2026, 7, 20, 0, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime              WINDOW_END   = ZonedDateTime.of(2026, 7, 21, 0, 0, 0, 0, ZoneOffset.UTC);

  private AgendaEventService                      agendaEventService;

  private AgendaEventAttendeeService              agendaEventAttendeeService;

  private AgendaScheduleConflictServiceImpl       conflictService;

  private long                                    nextEventId  = 1L;

  @BeforeEach
  void setUp() {
    agendaEventService = Mockito.mock(AgendaEventService.class);
    agendaEventAttendeeService = Mockito.mock(AgendaEventAttendeeService.class);
    conflictService = new AgendaScheduleConflictServiceImpl(agendaEventService, agendaEventAttendeeService);
  }

  // --- the overlap rule -------------------------------------------------------

  @Test
  void overlappingEventsAreOneConflict() throws Exception {
    givenEvents(event(at(9), at(11)), event(at(10), at(12)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(2, conflicts.get(0).getEvents().size());
    assertEquals(at(9), conflicts.get(0).getStart());
    assertEquals(at(12), conflicts.get(0).getEnd());
  }

  @Test
  void overlappingEventsAreOneConflictWhateverOrderTheyAreReadIn() throws Exception {
    givenEvents(event(at(10), at(12)), event(at(9), at(11)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(2, conflicts.get(0).getEvents().size());
  }

  /**
   * The assertion this whole tool exists for. 10:00 - 11:00 followed by 11:00
   * - 12:00 is a normal morning: the first ends at the instant the second
   * begins and no instant belongs to both.
   */
  @Test
  void backToBackEventsDoNotConflict() throws Exception {
    givenEvents(event(at(10), at(11)), event(at(11), at(12)));

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void backToBackEventsDoNotConflictWhateverOrderTheyAreReadIn() throws Exception {
    givenEvents(event(at(11), at(12)), event(at(10), at(11)));

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void aWholeDayOfBackToBackMeetingsIsNotAConflict() throws Exception {
    givenEvents(event(at(9), at(10)),
                event(at(10), at(11)),
                event(at(11), at(12)),
                event(at(12), at(13)),
                event(at(13), at(14)));

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void eventsAtTheSameTimeConflict() throws Exception {
    givenEvents(event(at(9), at(10)), event(at(9), at(10)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(2, conflicts.get(0).getEvents().size());
  }

  @Test
  void anEventInsideAnotherConflictsWithIt() throws Exception {
    givenEvents(event(at(9), at(17)), event(at(10), at(11)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(2, conflicts.get(0).getEvents().size());
    assertEquals(at(9), conflicts.get(0).getStart());
    assertEquals(at(17), conflicts.get(0).getEnd());
  }

  @Test
  void anInstantaneousEventIsNotAConflict() throws Exception {
    givenEvents(event(at(8), at(10)), event(at(9), at(9)));

    assertTrue(conflicts().isEmpty());
  }

  // --- grouping ---------------------------------------------------------------

  /**
   * A overlaps B and B overlaps C, but A and C do not touch each other. That
   * is one tangle of three, not two pairs naming B twice.
   */
  @Test
  void overlapsAreGroupedTransitively() throws Exception {
    givenEvents(event(at(9), at(11)), event(at(10), at(12)), event(at(11, 30), at(12, 30)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(3, conflicts.get(0).getEvents().size());
    assertEquals(at(9), conflicts.get(0).getStart());
    assertEquals(at(12, 30), conflicts.get(0).getEnd());
  }

  /**
   * The group's reach is the furthest end seen so far, not the last event's.
   * A short event in the middle of a long one must not close the group and
   * split one tangle into two that both name the long event.
   */
  @Test
  void aShortEventInsideALongOneDoesNotSplitTheGroup() throws Exception {
    givenEvents(event(at(9), at(13)), event(at(10), at(10, 30)), event(at(12), at(13)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(3, conflicts.get(0).getEvents().size());
  }

  @Test
  void twoSeparateTanglesAreTwoConflictsOrderedByWhenTheyStart() throws Exception {
    givenEvents(event(at(14), at(15, 30)),
                event(at(15), at(16)),
                event(at(9), at(10, 30)),
                event(at(10), at(11)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(2, conflicts.size());
    assertEquals(at(9), conflicts.get(0).getStart());
    assertEquals(at(11), conflicts.get(0).getEnd());
    assertEquals(at(14), conflicts.get(1).getStart());
    assertEquals(at(16), conflicts.get(1).getEnd());
  }

  @Test
  void aLoneEventIsNotAConflict() throws Exception {
    givenEvents(event(at(9), at(10)));

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void anEmptyWindowHasNoConflicts() throws Exception {
    givenEvents();

    ScheduleConflicts result = conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER);

    assertTrue(result.getConflicts().isEmpty());
    assertFalse(result.isTruncated());
  }

  // --- what is deliberately not a conflict ------------------------------------

  @Test
  void aDeclinedEventIsNotAConflict() throws Exception {
    Event declined = event(at(9), at(11));
    givenEvents(declined, event(at(10), at(12)));
    givenAnswer(declined, EventAttendeeResponse.DECLINED);

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void anAcceptedEventStillConflicts() throws Exception {
    Event accepted = event(at(9), at(11));
    givenEvents(accepted, event(at(10), at(12)));
    givenAnswer(accepted, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, conflicts().size());
  }

  @Test
  void anEventTheUserWasNeverInvitedToStillConflicts() throws Exception {
    // No attendee row for this user: normal for an event on a calendar of a
    // space they belong to. Never asked is not the same as declined.
    Event notInvited = event(at(9), at(11));
    givenEvents(notInvited, event(at(10), at(12)));
    when(agendaEventAttendeeService.getEventAttendees(notInvited.getId())).thenReturn(EventAttendeeList.EMPTY_ATTENDEE_LIST);

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertNull(conflictOn(conflicts.get(0), notInvited).getResponse());
  }

  @Test
  void aCancelledEventIsNotAConflict() throws Exception {
    Event cancelled = event(at(9), at(11));
    cancelled.setStatus(EventStatus.CANCELLED);
    givenEvents(cancelled, event(at(10), at(12)));

    assertTrue(conflicts().isEmpty());
  }

  @Test
  void anEventPublishedAsFreeTimeIsNotAConflict() throws Exception {
    Event marker = event(at(9), at(11));
    marker.setAvailability(EventAvailability.FREE);
    givenEvents(marker, event(at(10), at(12)));

    assertTrue(conflicts().isEmpty());
  }

  // --- the hard cases ---------------------------------------------------------

  @Test
  void anAllDayEventConflictsWithAMeetingInsideItsDay() throws Exception {
    Event allDay = event(at(0), WINDOW_START.plusHours(23).plusMinutes(59).plusSeconds(59));
    allDay.setAllDay(true);
    givenEvents(allDay, event(at(10), at(11)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(2, conflicts.get(0).getEvents().size());
    assertTrue(conflictOn(conflicts.get(0), allDay).getEvent().isAllDay());
  }

  /**
   * A materialised occurrence of a recurring series has no id of its own — the
   * series is in {@code parentId} — so the user's answer must be looked up
   * under the series, and the occurrence's own date passed along.
   */
  @Test
  void anOccurrenceIsLookedUpUnderItsSeries() throws Exception {
    Event occurrence = new Event();
    occurrence.setId(0);
    occurrence.setParentId(77L);
    occurrence.setOccurrence(new EventOccurrence(at(9)));
    occurrence.setStart(at(9));
    occurrence.setEnd(at(11));
    occurrence.setStatus(EventStatus.CONFIRMED);
    occurrence.setAvailability(EventAvailability.DEFAULT);
    givenEvents(occurrence, event(at(10), at(12)));
    when(agendaEventAttendeeService.getEventAttendees(77L))
                                                           .thenReturn(new EventAttendeeList(List.of(new EventAttendee(1L,
                                                                                                                       77L,
                                                                                                                       USER,
                                                                                                                       EventAttendeeResponse.DECLINED))));

    assertTrue(conflicts().isEmpty());
    verify(agendaEventAttendeeService).getEventAttendees(77L);
    verify(agendaEventAttendeeService, never()).getEventAttendees(0L);
  }

  /**
   * The window bounds the query, not the arithmetic: an event that runs past
   * the end of the asked window is reported with its real times, and the
   * group's envelope is allowed to reach past the window with it. Clipping it
   * would report a clash as shorter than it is.
   */
  @Test
  void anEventRunningPastTheWindowKeepsItsRealTimes() throws Exception {
    givenEvents(event(at(23), WINDOW_END.plusHours(2)), event(at(23, 30), at(23, 45)));

    List<ScheduleConflict> conflicts = conflicts();

    assertEquals(1, conflicts.size());
    assertEquals(at(23), conflicts.get(0).getStart());
    assertEquals(WINDOW_END.plusHours(2), conflicts.get(0).getEnd());
  }

  // --- whose calendar, and how much of it -------------------------------------

  @Test
  void onlyTheAskingUsersOwnCalendarIsRead() throws Exception {
    givenEvents();

    conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER);

    verify(agendaEventService).getEvents(argThat(filter -> filter.getAttendeeId() == USER && filter.getOwnerIds() == null),
                                         eq(ZoneOffset.UTC),
                                         eq(USER));
  }

  @Test
  void theAskedWindowIsTheQueriedWindow() throws Exception {
    givenEvents();

    conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER);

    verify(agendaEventService).getEvents(argThat(filter -> WINDOW_START.equals(filter.getStart())
                                                          && WINDOW_END.equals(filter.getEnd())),
                                         any(),
                                         anyLong());
  }

  @Test
  void theQueryAlsoDropsDeclinedEvents() throws Exception {
    givenEvents();

    conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER);

    verify(agendaEventService).getEvents(argThat(filter -> !filter.getResponseTypes()
                                                                  .contains(EventAttendeeResponse.DECLINED)),
                                         any(),
                                         anyLong());
  }

  @Test
  void aFullReadIsReportedAsTruncated() throws Exception {
    List<Event> many = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      many.add(event(WINDOW_START.plusMinutes(i * 2L), WINDOW_START.plusMinutes(i * 2L + 1)));
    }
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(many);

    ScheduleConflicts result = conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER);

    assertTrue(result.isTruncated());
    assertTrue(result.getConflicts().isEmpty());
  }

  @Test
  void aReadThatFitIsNotReportedAsTruncated() throws Exception {
    givenEvents(event(at(9), at(11)), event(at(10), at(12)));

    assertFalse(conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER).isTruncated());
  }

  @Test
  void aRefusalFromTheCalendarIsNotSwallowed() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenThrow(new IllegalAccessException("nope"));

    assertThrows(IllegalAccessException.class, () -> conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER));
  }

  // --- whose event it is ------------------------------------------------------

  @Test
  void theCallersOwnEventIsFlaggedAsTheirs() throws Exception {
    Event mine = event(at(9), at(11));
    mine.setCreatorId(USER);
    Event theirs = event(at(10), at(12));
    theirs.setCreatorId(COLLEAGUE);
    givenEvents(mine, theirs);

    ScheduleConflict conflict = conflicts().get(0);

    assertTrue(conflictOn(conflict, mine).isCreatedByUser());
    assertFalse(conflictOn(conflict, theirs).isCreatedByUser());
  }

  @Test
  void theEventItselfIsHandedBackUntouched() throws Exception {
    Event first = event(at(9), at(11));
    givenEvents(first, event(at(10), at(12)));

    assertSame(first, conflictOn(conflicts().get(0), first).getEvent());
  }

  // --- the window -------------------------------------------------------------

  @Test
  void aMissingWindowIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> conflictService.getScheduleConflicts(null, WINDOW_END, USER));
    assertThrows(IllegalArgumentException.class, () -> conflictService.getScheduleConflicts(WINDOW_START, null, USER));
  }

  @Test
  void aWindowThatEndsBeforeItStartsIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> conflictService.getScheduleConflicts(WINDOW_END, WINDOW_START, USER));
    assertThrows(IllegalArgumentException.class, () -> conflictService.getScheduleConflicts(WINDOW_START, WINDOW_START, USER));
  }

  @Test
  void aWindowLongerThanAQuarterIsRefused() {
    assertThrows(IllegalArgumentException.class,
                 () -> conflictService.getScheduleConflicts(WINDOW_START, WINDOW_START.plusDays(93), USER));
  }

  @Test
  void aRefusedWindowNeverTouchesTheCalendar() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> conflictService.getScheduleConflicts(WINDOW_END, WINDOW_START, USER));

    verify(agendaEventService, never()).getEvents(any(), any(), anyLong());
  }

  // --- helpers ----------------------------------------------------------------

  /**
   * Runs the service over the standard window and returns just the groups.
   *
   * @return the clashes found
   * @throws IllegalAccessException never, in these tests
   */
  private List<ScheduleConflict> conflicts() throws IllegalAccessException {
    return conflictService.getScheduleConflicts(WINDOW_START, WINDOW_END, USER).getConflicts();
  }

  /**
   * Makes the calendar hold exactly these events over the window.
   *
   * @param events what the event service will return
   * @throws IllegalAccessException never, this only stubs
   */
  private void givenEvents(Event... events) throws IllegalAccessException {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(events));
  }

  /**
   * Records the asking user's answer to an event.
   *
   * @param event the event answered
   * @param response the answer
   */
  private void givenAnswer(Event event, EventAttendeeResponse response) {
    when(agendaEventAttendeeService.getEventAttendees(event.getId()))
                                                                     .thenReturn(new EventAttendeeList(List.of(new EventAttendee(1L,
                                                                                                                                 event.getId(),
                                                                                                                                 USER,
                                                                                                                                 response))));
  }

  /**
   * Finds an event inside a clash by identity.
   *
   * @param conflict the clash to look in
   * @param event the event looked for
   * @return its entry in the clash
   */
  private ScheduleConflictEvent conflictOn(ScheduleConflict conflict, Event event) {
    return conflict.getEvents()
                   .stream()
                   .filter(conflictEvent -> conflictEvent.getEvent() == event)
                   .findFirst()
                   .orElseThrow(() -> new AssertionError("event %s is not in the conflict".formatted(event.getId())));
  }

  /**
   * Builds a plain confirmed event, distinct from every other one built here.
   *
   * @param start when it starts
   * @param end when it ends
   * @return the event
   */
  private Event event(ZonedDateTime start, ZonedDateTime end) {
    Event event = new Event();
    event.setId(nextEventId++);
    event.setStart(start);
    event.setEnd(end);
    event.setStatus(EventStatus.CONFIRMED);
    event.setAvailability(EventAvailability.DEFAULT);
    event.setCreatorId(COLLEAGUE);
    return event;
  }

  private ZonedDateTime at(int hour) {
    return at(hour, 0);
  }

  private ZonedDateTime at(int hour, int minute) {
    return WINDOW_START.plusHours(hour).plusMinutes(minute);
  }

}
