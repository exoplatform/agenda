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

import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
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

  private static final long             STRANGER     = 700L;

  private static final long             SPACE        = 900L;

  private static final ZonedDateTime    WINDOW_START = ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime    WINDOW_END   = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneOffset.UTC);

  private AgendaEventService            agendaEventService;

  private IdentityManager               identityManager;

  private SpaceService                  spaceService;

  private AgendaUserSettingsService     agendaUserSettingsService;

  private AgendaAvailabilityServiceImpl availabilityService;

  @BeforeEach
  void setUp() {
    agendaEventService = Mockito.mock(AgendaEventService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    spaceService = Mockito.mock(SpaceService.class);
    agendaUserSettingsService = Mockito.mock(AgendaUserSettingsService.class);
    availabilityService = new AgendaAvailabilityServiceImpl(agendaEventService,
                                                            identityManager,
                                                            spaceService,
                                                            agendaUserSettingsService);
    mockUser(ASKER);
    mockUser(COLLEAGUE);
    mockUser(STRANGER);
    // Nobody shares anything and nobody shares a space, unless a test says so.
    // A test that widens says exactly what it widens.
    when(agendaUserSettingsService.getAvailabilitySharing(anyLong())).thenReturn(AvailabilitySharing.NOBODY);
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
  void theEventServiceIsAskedAsTheTargetOnceTheGateHasAllowedTheRead() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(COLLEAGUE))).thenReturn(List.of(busyEvent(9, 10)));

    availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER);

    // The target, not the asker, is the acting user of the underlying event
    // read, because AgendaEventServiceImpl.getEvents refuses an attendee
    // filter naming anyone but its acting user - which would refuse every
    // disclosure this feature exists to allow. EXO-89841 passed the asker
    // here as a second gate; that second gate is spent, and what replaced it
    // is that this read is unreachable except through the gate above, which
    // the refusal tests pin.
    verify(agendaEventService).getEvents(argThat(filter -> filter.getAttendeeId() == COLLEAGUE),
                                         eq(ZoneOffset.UTC),
                                         eq(COLLEAGUE));
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

  // --- the sharing setting widens the gate, and only widens it ----------------

  @Test
  void aColleagueSharingWithNobodyIsRefusedEvenInsideASharedSpace() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.NOBODY);
    membersOfASameSpace("user400", "user5");
    // Fully stubbed on purpose: with the refusal dropped this returns an
    // availability, so it is the assertion below that fails, not an NPE.
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER));
  }

  @Test
  void aColleagueSharingWithinSpacesIsReadableBySomeoneInOneOfThem() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(COLLEAGUE))).thenReturn(List.of(busyEvent(9, 10)));

    List<UserAvailability> result = availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER);

    assertEquals(1, result.size());
    assertEquals(COLLEAGUE, result.get(0).getIdentityId());
    assertEquals(1, result.get(0).getBusy().size(), "a colleague sharing inside their spaces must be readable there");
  }

  @Test
  void aColleagueSharingWithinSpacesIsRefusedToSomeoneOutsideThemAll() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    when(spaceService.getCommonSpaces("user400", "user700")).thenReturn(listAccessOf());
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, STRANGER));
  }

  @Test
  void merelyBeingInvitedToASpaceIsNotSharingOne() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    // The platform's common-spaces query joins memberships of ANY status, so
    // a space the stranger has only been invited to - or has asked to join -
    // comes back as common. Trusting it alone would hand the busy time of
    // everyone in a space to anyone who knocked on its door.
    Space space = commonSpaceOf("user400", "user700");
    when(spaceService.isMember(space, "user400")).thenReturn(true);
    when(spaceService.isMember(space, "user700")).thenReturn(false);
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, STRANGER));
  }

  @Test
  void sharingIsOnAndStillAdmitsNobodyFromOutsideTheTargetsSpaces() throws Exception {
    // The setting has two states, and neither of them opens a user's busy time
    // to someone outside all of their spaces. This is the boundary that
    // replaced the retired third value, and it is the whole of what "on" can
    // ever widen to.
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    when(spaceService.getCommonSpaces("user400", "user700")).thenReturn(listAccessOf());
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, STRANGER));
  }

  @Test
  void theSettingConsultedIsTheTargetsOwnNotTheAskersOwn() throws Exception {
    // The asker is the permissive one, the target the private one. Reading the
    // asker's setting here would let anyone open everyone else's calendar
    // simply by opening their own.
    sharing(ASKER, AvailabilitySharing.SHARED_SPACES);
    sharing(COLLEAGUE, AvailabilitySharing.NOBODY);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(COLLEAGUE), WINDOW_START, WINDOW_END, ASKER));
  }

  @Test
  void noPersonalSettingEverOpensASpaceCalendar() throws Exception {
    // A space owner has no sharing setting of its own; if the code asked for
    // one anyway it would get the permissive default of whatever it looked up.
    mockSpace(SPACE);
    sharing(SPACE, AvailabilitySharing.SHARED_SPACES);
    // If the space identity were run through the personal rule it would find a
    // shared space and be let through; only the isUser guard stops it.
    membersOfASameSpace("space900", "user5");
    when(spaceService.getSpaceByPrettyName("space900")).thenReturn(null);
    when(spaceService.canViewSpace(null, "user5")).thenReturn(false);
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.getAvailability(List.of(SPACE), WINDOW_START, WINDOW_END, ASKER),
                 "the calendar ACL alone decides for a space, whatever any personal setting says");
  }

  @Test
  void suggestingATimeWithOneAttendeeWhoSharesNothingIsRefused() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.NOBODY);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    assertThrows(IllegalAccessException.class,
                 () -> availabilityService.suggestMeetingTime(List.of(ASKER, COLLEAGUE),
                                                              Duration.ofMinutes(30),
                                                              WINDOW_START,
                                                              WINDOW_END,
                                                              false,
                                                              false,
                                                              100,
                                                              ASKER),
                 "an attendee who shares nothing is unknown, and a slot must not be proposed over an unknown");
  }

  @Test
  void suggestingATimeWithASharingAttendeeAvoidsTheirBusyBlock() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of());
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(COLLEAGUE))).thenReturn(List.of(busyEvent(8, 19)));

    List<TimeBlock> slots = availabilityService.suggestMeetingTime(List.of(ASKER, COLLEAGUE),
                                                                   Duration.ofMinutes(30),
                                                                   WINDOW_START,
                                                                   WINDOW_END,
                                                                   false,
                                                                   false,
                                                                   100,
                                                                   ASKER);

    assertEquals(2, slots.size(), "only the hour the colleague is free is left, as two half-hour slots");
    assertEquals(at(19), slots.get(0).getStart());
  }

  @Test
  void anAttendeeWhoSharesNothingIsNamedNotDisclosedRatherThanFoundFree() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.NOBODY);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of());
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(COLLEAGUE))).thenReturn(List.of(busyEvent(9, 10)));

    AvailabilityConflicts report = availabilityService.getConflicts(List.of(ASKER, COLLEAGUE), at(9), at(10), ASKER);

    assertEquals(List.of(COLLEAGUE),
                 report.getNotDisclosedIdentityIds(),
                 "a refusal must be reported as not disclosed, never collapsed into 'nothing in the way'");
    assertTrue(report.getConflicts().isEmpty());
  }

  @Test
  void anAttendeeWhoSharesInSpacesIsCheckedAndReportedBusy() throws Exception {
    sharing(COLLEAGUE, AvailabilitySharing.SHARED_SPACES);
    membersOfASameSpace("user400", "user5");
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(ASKER))).thenReturn(List.of());
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(COLLEAGUE))).thenReturn(List.of(busyEvent(9, 10)));

    AvailabilityConflicts report = availabilityService.getConflicts(List.of(ASKER, COLLEAGUE), at(9), at(10), ASKER);

    assertTrue(report.getNotDisclosedIdentityIds().isEmpty());
    assertEquals(1, report.getConflicts().size());
    assertEquals(COLLEAGUE, report.getConflicts().get(0).getIdentityId());
    assertFalse(report.isAllAvailable());
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

  /**
   * Declares how widely a user shares their busy time.
   *
   * @param identityId the user whose choice is declared
   * @param sharing the choice
   */
  private void sharing(long identityId, AvailabilitySharing sharing) {
    when(agendaUserSettingsService.getAvailabilitySharing(identityId)).thenReturn(sharing);
  }

  /**
   * Declares that two users are in a same space and are both really members
   * of it — what the shared-spaces rule actually requires.
   *
   * @param firstUsername remote id of one user
   * @param secondUsername remote id of the other
   */
  private void membersOfASameSpace(String firstUsername, String secondUsername) {
    Space space = commonSpaceOf(firstUsername, secondUsername);
    when(spaceService.isMember(space, firstUsername)).thenReturn(true);
    when(spaceService.isMember(space, secondUsername)).thenReturn(true);
  }

  /**
   * Declares that a space is returned as "common" to two users, without
   * saying anything about whether either of them is a member of it — which is
   * what the platform query behind getCommonSpaces actually reports, since it
   * joins memberships of any status, invitations and join requests included.
   *
   * @param firstUsername remote id of one user
   * @param secondUsername remote id of the other
   * @return the space the lookup returns
   */
  private Space commonSpaceOf(String firstUsername, String secondUsername) {
    Space space = new Space();
    space.setId("42");
    space.setPrettyName("common");
    when(spaceService.getCommonSpaces(firstUsername, secondUsername)).thenReturn(listAccessOf(space));
    when(spaceService.getCommonSpaces(secondUsername, firstUsername)).thenReturn(listAccessOf(space));
    return space;
  }

  /**
   * Wraps spaces in the ListAccess the space service hands back.
   *
   * @param spaces the spaces the list holds
   * @return a list access over them
   */
  private ListAccess<Space> listAccessOf(Space... spaces) {
    return new ListAccess<>() {
      @Override
      public Space[] load(int offset, int limit) {
        return spaces;
      }

      @Override
      public int getSize() {
        return spaces.length;
      }
    };
  }

  /**
   * Mocks a space identity, which is what a space calendar's owner resolves
   * to.
   *
   * @param identityId the space identity's technical id
   * @return the mocked identity
   */
  private Identity mockSpace(long identityId) {
    Identity identity = Mockito.mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.isUser()).thenReturn(false);
    when(identity.isSpace()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("space" + identityId);
    when(identityManager.getIdentity(String.valueOf(identityId))).thenReturn(identity);
    return identity;
  }

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
