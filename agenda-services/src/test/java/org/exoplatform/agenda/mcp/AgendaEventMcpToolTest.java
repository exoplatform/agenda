/**
 * Copyright (C) 2025 eXo Platform SAS.
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
package org.exoplatform.agenda.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.mcp.model.AgendaEventCollectionModel;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;

import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.social.translation.service.TranslationService;

class AgendaEventMcpToolTest {

  private static final String                USERNAME         = "testuser1";

  private static final long                  USER_IDENTITY_ID = 5L;

  private static final long                  EVENT_ID         = 42L;

  private static final long                  SPACE_ID         = 7L;

  private AgendaCalendarService              agendaCalendarService;

  private AgendaEventService                 agendaEventService;

  private AgendaEventConferenceService       agendaEventConferenceService;

  private AgendaEventAttendeeService         agendaEventAttendeeService;

  private org.exoplatform.agenda.service.AgendaEventDatePollService agendaEventDatePollService;

  private org.exoplatform.agenda.service.AgendaEventReminderService agendaEventReminderService;

  private AgendaUserSettingsService          agendaUserSettingsService;

  private UserPortalConfigService            portalConfigService;

  private ProfilePropertyService             profilePropertyService;

  private PermanentLinkService               permanentLinkService;

  private TranslationService                 translationService;

  private IdentityManager                    identityManager;

  private org.exoplatform.social.core.space.spi.SpaceService spaceService;

  private UserACL                            userAcl;

  private AgendaEventMcpTool                 tool;

  @BeforeEach
  void setUp() throws Exception {
    agendaCalendarService = Mockito.mock(AgendaCalendarService.class);
    agendaEventService = Mockito.mock(AgendaEventService.class);
    agendaEventConferenceService = Mockito.mock(AgendaEventConferenceService.class);
    agendaEventAttendeeService = Mockito.mock(AgendaEventAttendeeService.class);
    agendaEventDatePollService = Mockito.mock(org.exoplatform.agenda.service.AgendaEventDatePollService.class);
    agendaEventReminderService = Mockito.mock(org.exoplatform.agenda.service.AgendaEventReminderService.class);
    agendaUserSettingsService = Mockito.mock(AgendaUserSettingsService.class);
    portalConfigService = Mockito.mock(UserPortalConfigService.class);
    profilePropertyService = Mockito.mock(ProfilePropertyService.class);
    permanentLinkService = Mockito.mock(PermanentLinkService.class);
    translationService = Mockito.mock(TranslationService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    spaceService = Mockito.mock(org.exoplatform.social.core.space.spi.SpaceService.class);
    userAcl = Mockito.mock(UserACL.class);

    Identity currentUser = Mockito.mock(Identity.class);
    when(currentUser.getIdentityId()).thenReturn(USER_IDENTITY_ID);
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(currentUser);

    org.exoplatform.services.security.Identity aclIdentity =
                                                           Mockito.mock(org.exoplatform.services.security.Identity.class);

    tool = new AgendaEventMcpTool(agendaCalendarService,
                                  agendaEventService,
                                  agendaEventConferenceService,
                                  agendaEventAttendeeService,
                                  agendaEventDatePollService,
                                  agendaEventReminderService,
                                  agendaUserSettingsService,
                                  portalConfigService,
                                  profilePropertyService,
                                  permanentLinkService,
                                  translationService,
                                  identityManager,
                                  spaceService,
                                  userAcl) {
      @Override
      public String getCurrentUserName() {
        return USERNAME;
      }

      @Override
      public org.exoplatform.services.security.Identity getCurrentUserAclIdentity() {
        return aclIdentity;
      }

      @Override
      public Locale getCurrentUserLocale() {
        return Locale.ENGLISH;
      }
    };
  }

  // --- get_agenda_events ---------------------------------------------------

  @Test
  void getAgendaEventsReturnsEmptyCollection() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(Collections.emptyList());

    AgendaEventCollectionModel model = tool.getAgendaEvents(null, null, null, null);

    assertNotNull(model);
    assertEquals(0, model.getEvents().size());
    // a default limit is applied when no end date/limit is provided
    assertEquals(10, model.getUsedLimit());
  }

  // --- get_agenda_event_by_id ----------------------------------------------

  @Test
  void getAgendaEventByIdNotFoundFails() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> tool.getAgendaEventById(EVENT_ID));
  }

  // --- create_agenda_event -------------------------------------------------

  @Test
  void createAgendaEventWithoutSpaceIdFails() throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> tool.createAgendaEvent(null, "summary", null, null, null, null, null, null, null, null, null, null, null));
  }

  @Test
  void createAgendaEventWithoutPermissionFails() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> tool.createAgendaEvent(SPACE_ID, "summary", null, null, null, null, null, null, null, null, null, null, null));
  }

  // --- update_agenda_event -------------------------------------------------

  @Test
  void updateAgendaEventWithoutEventIdFails() throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> tool.updateAgendaEvent(null, "summary", null, null, null, null, null, null, null, null, null));
  }

  @Test
  void updateAgendaEventWithoutPermissionFails() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> tool.updateAgendaEvent(EVENT_ID, "summary", null, null, null, null, null, null, null, null, null));
  }

  // --- delete_agenda_event -------------------------------------------------

  @Test
  void deleteAgendaEventWithoutEventIdFails() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> tool.deleteAgendaEvent(null));
  }

  @Test
  void deleteAgendaEventWithoutPermissionFails() throws Exception {
    when(userAcl.hasDeletePermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.deleteAgendaEvent(EVENT_ID));
    verify(agendaEventService, never()).deleteEventById(anyLong(), anyLong());
  }

  @Test
  void deleteAgendaEventSucceeds() throws Exception {
    when(userAcl.hasDeletePermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    tool.deleteAgendaEvent(EVENT_ID);
    verify(agendaEventService, times(1)).deleteEventById(EVENT_ID, USER_IDENTITY_ID);
  }

  // --- accept / decline / cancel -------------------------------------------

  @Test
  void acceptAgendaEventInvitationWithoutPermissionFails() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.acceptAgendaEventInvitation(EVENT_ID));
  }

  @Test
  void acceptAgendaEventInvitationSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    tool.acceptAgendaEventInvitation(EVENT_ID);
    verify(agendaEventAttendeeService, times(1)).sendEventResponse(EVENT_ID,
                                                                   USER_IDENTITY_ID,
                                                                   EventAttendeeResponse.ACCEPTED);
  }

  @Test
  void declineAgendaEventInvitationSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    tool.declineAgendaEventInvitation(EVENT_ID, null);
    verify(agendaEventAttendeeService, times(1)).sendEventResponse(EVENT_ID,
                                                                   USER_IDENTITY_ID,
                                                                   EventAttendeeResponse.DECLINED);
  }

  @Test
  void cancelAgendaEventDelegatesToDecline() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    tool.cancelAgendaEvent(EVENT_ID, null);
    verify(agendaEventAttendeeService, times(1)).sendEventResponse(EVENT_ID,
                                                                   USER_IDENTITY_ID,
                                                                   EventAttendeeResponse.DECLINED);
  }

  @Test
  void cancelSingleOccurrenceMaterializesExceptionalAndCancelsIt() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    Event exceptional = Mockito.mock(Event.class);
    when(exceptional.getId()).thenReturn(99L);
    when(agendaEventService.saveEventExceptionalOccurrence(eq(EVENT_ID), any())).thenReturn(exceptional);

    tool.cancelAgendaEvent(EVENT_ID, "2026-07-20T09:00:00Z");

    // Cancelling one occurrence must NOT decline (which used to drop this-and-future)
    verify(agendaEventAttendeeService, never()).sendUpcomingEventResponse(anyLong(), any(), anyLong(), any());
    verify(agendaEventService, times(1)).saveEventExceptionalOccurrence(eq(EVENT_ID), any());
    verify(agendaEventService, times(1)).updateEventFields(eq(99L),
                                                           eq(java.util.Collections.singletonMap("status",
                                                                                                 java.util.Collections.singletonList("CANCELLED"))),
                                                           eq(false),
                                                           eq(false),
                                                           eq(USER_IDENTITY_ID));
  }

  // --- invite_users / invite_space -----------------------------------------

  @Test
  void inviteUsersToAgendaEventWithoutAttendeesFails() throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> tool.inviteUsersToAgendaEvent(EVENT_ID, Collections.emptyList()));
  }

  @Test
  void inviteUsersToAgendaEventWithoutPermissionFails() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> tool.inviteUsersToAgendaEvent(EVENT_ID, List.of("john")));
  }

  @Test
  void inviteSpaceToAgendaEventWithoutSpaceIdFails() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> tool.inviteSpaceToAgendaEvent(EVENT_ID, null));
  }

  @Test
  void inviteSpaceToAgendaEventWithoutPermissionFails() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.inviteSpaceToAgendaEvent(EVENT_ID, SPACE_ID));
  }

  // --- new scheduling tools ------------------------------------------------

  @Test
  void createDatePollWithoutSlotsFails() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    assertThrows(IllegalArgumentException.class,
                 () -> tool.createDatePoll(SPACE_ID, "poll", Collections.emptyList(), null, null, false, null));
  }

  @Test
  void createAgendaEventResolvesSpaceByName() throws Exception {
    // A space name (no space_id) must resolve to the space; here permission is denied on the resolved id, which proves
    // the name was resolved to SPACE_ID and the flow proceeded to the space create-permission check.
    org.exoplatform.social.core.space.model.Space space = Mockito.mock(org.exoplatform.social.core.space.model.Space.class);
    when(space.getSpaceId()).thenReturn(SPACE_ID);
    when(spaceService.getSpaceByPrettyName("EVA Space")).thenReturn(space);
    when(spaceService.isMember(space, USERNAME)).thenReturn(true);
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> tool.createAgendaEvent(null, "summary", null, null, "2026-07-20T14:00:00Z", "2026-07-20T15:00:00Z",
                                              null, null, null, null, null, null, "EVA Space"));
  }

  @Test
  void createDatePollResolvesSpaceByName() throws Exception {
    // A date poll given only a space NAME (space_id == null) must build its event on the RESOLVED space id, not on the
    // raw null spaceId. Pre-fix the event was built with getSpaceCalendarId(spaceId) which auto-unboxed the null Long to
    // an NPE; post-fix it uses getSpaceCalendarId(resolvedSpaceId == SPACE_ID). We prove the resolved id flowed through
    // by leaving getSpaceById(SPACE_ID) unstubbed: post-fix that yields a controlled ObjectNotFoundException on id 7
    // (NOT an NPE), whereas the pre-fix code throws NPE here.
    org.exoplatform.social.core.space.model.Space space = Mockito.mock(org.exoplatform.social.core.space.model.Space.class);
    when(space.getSpaceId()).thenReturn(SPACE_ID);
    when(spaceService.getSpaceByPrettyName("EVA Space")).thenReturn(space);
    when(spaceService.isMember(space, USERNAME)).thenReturn(true);
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    ObjectNotFoundException ex =
                              assertThrows(ObjectNotFoundException.class,
                                           () -> tool.createDatePoll(null,
                                                                     "poll",
                                                                     List.of("2026-07-20T14:00:00Z|2026-07-20T15:00:00Z"),
                                                                     null,
                                                                     null,
                                                                     false,
                                                                     "EVA Space"));
    // The resolved space id (7) reached getSpaceCalendarId -> getSpaceById, proving no NPE and correct resolution.
    assertTrue(ex.getMessage().contains(String.valueOf(SPACE_ID)));
    verify(spaceService).getSpaceById(SPACE_ID);
  }

  @Test
  void createAgendaEventWithoutSpaceOrNameGivesGuidance() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                 () -> tool.createAgendaEvent(null, "summary", null, null, "2026-07-20T14:00:00Z", "2026-07-20T15:00:00Z",
                                              null, null, null, null, null, null, null));
    assertTrue(ex.getMessage().toLowerCase().contains("space"));
  }

  @Test
  void getDatePollResultsWithoutEventIdFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.getDatePollResults(0L));
  }

  @Test
  void confirmDatePollWithoutOptionIdFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.confirmDatePoll(null));
  }

  @Test
  void getAvailabilityWithoutUsernamesFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.getAvailability(Collections.emptyList(), "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"));
  }

  @Test
  void suggestMeetingTimeWithoutDurationFails() {
    assertThrows(IllegalArgumentException.class,
                 () -> tool.suggestMeetingTime(List.of("john"), 0, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", null));
  }

  @Test
  void respondToAgendaEventWithInvalidResponseFails() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> tool.respondToAgendaEvent(EVENT_ID, "MAYBE", null));
  }

  @Test
  void setEventConferenceWithoutPermissionFails() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.setEventConference(EVENT_ID, "https://meet.example/x", null));
  }

  @Test
  void searchAgendaEventsWithoutQueryFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.searchAgendaEvents(" ", null, null, null));
  }

  @SuppressWarnings("unused")
  private Event mockEvent() {
    return Mockito.mock(Event.class);
  }

}
