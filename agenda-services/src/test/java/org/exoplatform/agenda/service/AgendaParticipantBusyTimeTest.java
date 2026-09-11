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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.AvailabilityDisclosure;
import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.UserBusyTime;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the one thing this feature exists to get right: on the screen where a
 * date poll's slots are dragged out, a participant nobody could check must
 * never end up looking like a participant who is free.
 * <p>
 * Every pin here is written as a <strong>contrast between two participants
 * whose busy content is identical and empty</strong> — one who was read and
 * has nothing on, one who was not read at all. Without that contrast a mutant
 * that collapses the two produces the same empty list in both cases and a test
 * asserting only on the blocks would still pass; with it, the assertion that
 * fails is the one on the status, which is the property under test.
 */
class AgendaParticipantBusyTimeTest {

  private static final long             ORGANISER    = 5L;

  private static final long             SHARER       = 400L;

  private static final long             WITHHOLDER   = 700L;

  private static final long             BROKEN       = 800L;

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
    mockUser(ORGANISER);
    mockUser(SHARER);
    mockUser(WITHHOLDER);
    mockUser(BROKEN);
    mockSpace(SPACE);
    // Nobody shares anything unless a test says so.
    when(agendaUserSettingsService.getAvailabilitySharing(anyLong())).thenReturn(AvailabilitySharing.NOBODY);
  }

  @Test
  void aParticipantWhoSharesNothingIsNotReportedFree() throws Exception {
    // Both participants are stubbed to hold nothing over the window, so the
    // BLOCKS are identical and empty for both. Only the status tells them
    // apart, which is exactly what a screen has to read.
    sharesABusyTimeWith(SHARER, ORGANISER);
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(SHARER, WITHHOLDER),
                                                                   WINDOW_START,
                                                                   WINDOW_END,
                                                                   ORGANISER);

    assertEquals(AvailabilityDisclosure.DISCLOSED, busyTimeOf(busyTimes, SHARER).getDisclosure());
    assertEquals(AvailabilityDisclosure.NOT_DISCLOSED,
                 busyTimeOf(busyTimes, WITHHOLDER).getDisclosure(),
                 "a participant who shares nothing must not be reported as read-and-free");
  }

  @Test
  void aParticipantWhoSharesNothingCarriesNoBlockListAtAll() {
    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(WITHHOLDER), WINDOW_START, WINDOW_END, ORGANISER);

    // Not an empty list: an empty list is already the spelling of "read, and
    // nothing was in the way", and one idea gets one spelling.
    assertNull(busyTimeOf(busyTimes, WITHHOLDER).getBusy());
  }

  @Test
  void aParticipantWithNothingOnIsReportedFree() throws Exception {
    sharesABusyTimeWith(SHARER, ORGANISER);
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of());

    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(SHARER), WINDOW_START, WINDOW_END, ORGANISER);

    UserBusyTime busyTime = busyTimeOf(busyTimes, SHARER);
    assertEquals(AvailabilityDisclosure.DISCLOSED, busyTime.getDisclosure());
    assertNotNull(busyTime.getBusy(), "a calendar that was read answers with a list, empty or not");
    assertTrue(busyTime.getBusy().isEmpty());
  }

  @Test
  void aBrokenReadIsNotReportedFreeAndIsNotReportedWithheld() throws Exception {
    sharesABusyTimeWith(BROKEN, ORGANISER);
    when(agendaEventService.getEvents(any(), any(), eq(BROKEN))).thenThrow(new IllegalStateException("calendar store down"));

    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(BROKEN), WINDOW_START, WINDOW_END, ORGANISER);

    UserBusyTime busyTime = busyTimeOf(busyTimes, BROKEN);
    assertEquals(AvailabilityDisclosure.FAILED, busyTime.getDisclosure());
    assertNull(busyTime.getBusy(), "a read that broke must carry no block list, empty or otherwise");
  }

  @Test
  void oneUnreadableParticipantDoesNotBlankTheOthers() throws Exception {
    sharesABusyTimeWith(SHARER, ORGANISER);
    when(agendaEventService.getEvents(any(), any(), eq(SHARER))).thenReturn(List.of(busyEvent(9, 10)));

    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(SHARER, WITHHOLDER),
                                                                   WINDOW_START,
                                                                   WINDOW_END,
                                                                   ORGANISER);

    assertEquals(2, busyTimes.size());
    assertEquals(1, busyTimeOf(busyTimes, SHARER).getBusy().size());
    assertEquals(AvailabilityDisclosure.NOT_DISCLOSED, busyTimeOf(busyTimes, WITHHOLDER).getDisclosure());
  }

  @Test
  void everyParticipantAskedAboutGetsAnAnswer() {
    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(SHARER, WITHHOLDER, BROKEN, SPACE),
                                                                   WINDOW_START,
                                                                   WINDOW_END,
                                                                   ORGANISER);

    // Omitting anybody would be an unstated status, and a screen cannot name
    // somebody it was never told about.
    assertEquals(4, busyTimes.size());
    assertEquals(List.of(SHARER, WITHHOLDER, BROKEN, SPACE), busyTimes.stream().map(UserBusyTime::getIdentityId).toList());
  }

  @Test
  void aSpaceIsNeverReportedAsAReadParticipant() throws Exception {
    when(agendaEventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(busyEvent(9, 10)));

    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(SPACE), WINDOW_START, WINDOW_END, ORGANISER);

    assertEquals(AvailabilityDisclosure.NOT_DISCLOSED, busyTimeOf(busyTimes, SPACE).getDisclosure());
    verify(agendaEventService, never()).getEvents(any(), any(), anyLong());
  }

  @Test
  void theSameParticipantNamedTwiceGetsOneAnswer() {
    List<UserBusyTime> busyTimes = availabilityService.getBusyTime(List.of(WITHHOLDER, WITHHOLDER),
                                                                   WINDOW_START,
                                                                   WINDOW_END,
                                                                   ORGANISER);

    assertEquals(1, busyTimes.size());
  }

  // --- helpers ----------------------------------------------------------------

  /**
   * Finds one participant's answer.
   *
   * @param busyTimes the whole answer
   * @param identityId the participant wanted
   * @return their entry
   */
  private UserBusyTime busyTimeOf(List<UserBusyTime> busyTimes, long identityId) {
    return busyTimes.stream()
                    .filter(busyTime -> busyTime.getIdentityId() == identityId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no answer for identity " + identityId));
  }

  /**
   * Declares that one user discloses their busy time to another, through the
   * only route that widens the calendar ACL: both share a space they are
   * really members of, and the target's setting says shared spaces.
   *
   * @param targetIdentityId the user whose busy time is disclosed
   * @param viewerIdentityId the user it is disclosed to
   */
  private void sharesABusyTimeWith(long targetIdentityId, long viewerIdentityId) {
    when(agendaUserSettingsService.getAvailabilitySharing(targetIdentityId)).thenReturn(AvailabilitySharing.SHARED_SPACES);
    String targetUsername = "user" + targetIdentityId;
    String viewerUsername = "user" + viewerIdentityId;
    Space space = Mockito.mock(Space.class);
    @SuppressWarnings("unchecked")
    ListAccess<Space> commonSpaces = Mockito.mock(ListAccess.class);
    try {
      when(commonSpaces.load(0, 200)).thenReturn(new Space[] { space });
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    when(spaceService.getCommonSpaces(targetUsername, viewerUsername)).thenReturn(commonSpaces);
    when(spaceService.isMember(space, targetUsername)).thenReturn(true);
    when(spaceService.isMember(space, viewerUsername)).thenReturn(true);
  }

  /**
   * Declares a user identity.
   *
   * @param identityId the technical identifier
   */
  private void mockUser(long identityId) {
    Identity identity = Mockito.mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.isUser()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("user" + identityId);
    when(identityManager.getIdentity(String.valueOf(identityId))).thenReturn(identity);
  }

  /**
   * Declares a space identity.
   *
   * @param identityId the technical identifier
   */
  private void mockSpace(long identityId) {
    Identity identity = Mockito.mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.isUser()).thenReturn(false);
    when(identityManager.getIdentity(String.valueOf(identityId))).thenReturn(identity);
  }

  /**
   * An accepted, busy event over the given hours.
   *
   * @param startHour the hour it starts
   * @param endHour the hour it ends
   * @return the event
   */
  private Event busyEvent(int startHour, int endHour) {
    Event event = new Event();
    event.setStart(ZonedDateTime.of(2026, 7, 20, startHour, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 7, 20, endHour, 0, 0, 0, ZoneOffset.UTC));
    event.setStatus(EventStatus.CONFIRMED);
    event.setAvailability(EventAvailability.DEFAULT);
    return event;
  }

}
