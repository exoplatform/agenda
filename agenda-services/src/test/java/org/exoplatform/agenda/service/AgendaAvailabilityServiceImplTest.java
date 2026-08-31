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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the availability gate: who may read whose free/busy, and what is
 * computed once they may.
 * <p>
 * The refusal tests deliberately stub the whole read path they refuse, so that
 * dropping the guard makes them fail on their own assertion — "nothing was
 * thrown" — rather than on an incidental null.
 */
class AgendaAvailabilityServiceImplTest {

  private static final long             ASKER        = 5L;

  private static final long             COLLEAGUE    = 400L;

  private static final ZonedDateTime    WINDOW_START = ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime    WINDOW_END   = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneOffset.UTC);

  private AgendaEventService            agendaEventService;

  private IdentityManager               identityManager;

  private SpaceService                  spaceService;

  private AgendaAvailabilityServiceImpl availabilityService;

  @BeforeEach
  void setUp() {
    agendaEventService = Mockito.mock(AgendaEventService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    spaceService = Mockito.mock(SpaceService.class);
    availabilityService = new AgendaAvailabilityServiceImpl(agendaEventService, identityManager, spaceService);
    mockUser(ASKER);
    mockUser(COLLEAGUE);
  }

  // --- the gate ---------------------------------------------------------------

  @Test
  void readingAColleaguesAvailabilityIsRefused() throws Exception {
    // The read path is fully stubbed on purpose: with the guard removed this
    // call returns an availability, so the assertion below is what fails.
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER));
  }

  @Test
  void aRefusedReadNeverTouchesTheColleaguesCalendar() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER));

    verify(agendaEventService, never()).getEvents(any(), any(), anyLong());
  }

  @Test
  void oneUnreadableUserInTheListRefusesTheWholeCall() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(ASKER, COLLEAGUE), WINDOW_START, WINDOW_END, ASKER));
  }

  @Test
  void readingOwnAvailabilityIsAllowed() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(busyEvent(9, 10)));

    List<UserAvailability> result = availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, ASKER);

    assertEquals(1, result.size());
    assertEquals(ASKER, result.get(0).getIdentityId());
    assertEquals(1, result.get(0).getBusy().size());
  }

  @Test
  void theEventServiceIsAskedAsTheAskingUserNotAsTheTarget() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of());

    availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, ASKER);

    // The asker is passed as the acting user, so the event service's own
    // attendee check is a real second gate rather than a comparison of the
    // target with itself.
    //
    // Note this assertion cannot currently tell the two apart: while the rule
    // is "your own availability only", asker and target are always equal, so
    // passing the target here would still satisfy it. It is defence in depth
    // for the day the rule widens, not a pin that a mutation would kill.
    verify(agendaEventService).getEvents(argThat(filter -> filter.getAttendeeId() == ASKER), eq(ZoneOffset.UTC), eq(ASKER));
  }

  @Test
  void anUnknownAskerIsRefused() {
    when(identityManager.getIdentity("999")).thenReturn(null);

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, 999L));
  }

  @Test
  void suggestingATimeWithAnUnreadableAttendeeIsRefused() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.suggestMeetingTime(List.of(ASKER, COLLEAGUE),
                                                              Duration.ofMinutes(30),
                                                              WINDOW_START,
                                                              WINDOW_END,
                                                              false,
                                                              false,
                                                              100,
                                                              ASKER));
  }

  // --- what "busy" means ------------------------------------------------------

  @Test
  void anEventPublishedAsFreeDoesNotMakeTheUserBusy() throws Exception {
    Event free = busyEvent(9, 10);
    free.setAvailability(EventAvailability.FREE);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(free));

    List<UserAvailability> result = availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, ASKER);

    assertTrue(result.get(0).getBusy().isEmpty(), "an event marked free must not make its owner look busy");
    assertEquals(1, result.get(0).getFree().size());
  }

  @Test
  void anEventLeftOnTheDefaultAvailabilityStillMakesTheUserBusy() throws Exception {
    Event standard = busyEvent(9, 10);
    standard.setAvailability(EventAvailability.DEFAULT);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(standard));

    List<UserAvailability> result = availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, ASKER);

    assertEquals(1, result.get(0).getBusy().size());
  }

  @Test
  void overlappingEventsMergeAndTheRestOfTheWindowIsFree() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(busyEvent(9, 10),
                                                                                                busyEvent(9, 11),
                                                                                                busyEvent(14, 15)));

    List<UserAvailability> result = availabilityService.getAvailability(List.of(ASKER), WINDOW_START, WINDOW_END, ASKER);

    List<TimeBlock> busy = result.get(0).getBusy();
    assertEquals(2, busy.size());
    assertEquals(at(9), busy.get(0).getStart());
    assertEquals(at(11), busy.get(0).getEnd());
    assertEquals(3, result.get(0).getFree().size());
  }

  // --- conflicts degrade, they do not refuse ----------------------------------

  @Test
  void anUnreadableAttendeeIsNamedNotCheckedRatherThanAssumedFree() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    AvailabilityConflicts report = availabilityService.getConflicts(List.of(ASKER, COLLEAGUE), at(9), at(10), ASKER);

    assertTrue(report.getConflicts().isEmpty());
    assertEquals(List.of(COLLEAGUE), report.getNotDisclosedIdentityIds(), "an attendee we could not check must be named");
    assertTrue(report.isAllAvailable(), "all_available means 'no clash found', and none was found");
  }

  @Test
  void aReadableAttendeeWhoIsBusyIsReported() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(busyEvent(9, 10)));

    AvailabilityConflicts report = availabilityService.getConflicts(List.of(ASKER), at(9), at(10), ASKER);

    assertEquals(1, report.getConflicts().size());
    assertEquals(ASKER, report.getConflicts().get(0).getIdentityId());
    assertFalse(report.isAllAvailable());
  }

  @Test
  void rankingCountsOnlyTheAttendeesTheAskerMayRead() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of(busyEvent(9, 10)));
    EventDateOption busySlot = dateOption(9, 10);
    EventDateOption freeSlot = dateOption(14, 15);

    List<EventDateOption> ranked = availabilityService.rankDateOptionsByAvailability(List.of(busySlot, freeSlot),
                                                                                     List.of(ASKER, COLLEAGUE),
                                                                                     ASKER);

    assertEquals(freeSlot, ranked.get(0), "the slot the asker is free on must come first");
  }

  // --- helpers ----------------------------------------------------------------

  private Identity mockUser(long identityId) {
    Identity identity = Mockito.mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.isUser()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("user" + identityId);
    when(identityManager.getIdentity(String.valueOf(identityId))).thenReturn(identity);
    return identity;
  }

  private Event busyEvent(int startHour, int endHour) {
    Event event = new Event();
    event.setStart(at(startHour));
    event.setEnd(at(endHour));
    event.setStatus(EventStatus.CONFIRMED);
    event.setAvailability(EventAvailability.DEFAULT);
    return event;
  }

  private EventDateOption dateOption(int startHour, int endHour) {
    EventDateOption dateOption = new EventDateOption();
    dateOption.setStart(at(startHour));
    dateOption.setEnd(at(endHour));
    return dateOption;
  }

  private ZonedDateTime at(int hour) {
    return ZonedDateTime.of(2026, 7, 20, hour, 0, 0, 0, ZoneOffset.UTC);
  }

}
