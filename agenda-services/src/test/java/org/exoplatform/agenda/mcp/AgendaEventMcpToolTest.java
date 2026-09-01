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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.mcp.model.AgendaEventAttendeeModel;
import org.exoplatform.agenda.mcp.model.AgendaEventCollectionModel;
import org.exoplatform.agenda.mcp.model.AgendaEventModel;
import org.exoplatform.agenda.mcp.model.AvailabilityModel;
import org.exoplatform.agenda.mcp.model.ConferenceModel;
import org.exoplatform.agenda.mcp.model.DatePollModel;
import org.exoplatform.agenda.mcp.model.ScheduleConflictEventModel;
import org.exoplatform.agenda.mcp.model.ScheduleConflictsModel;
import org.exoplatform.agenda.mcp.model.TimeBlockModel;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.service.AgendaAvailabilityServiceImpl;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaScheduleConflictServiceImpl;
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

  private static final long                  CALENDAR_ID      = 500L;

  private static final long                  OWNER_IDENTITY_ID = 900L;

  private static final String                SPACE_PRETTY_NAME = "eva-space";

  private static final ZonedDateTime         START            = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime         END              = ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC);

  private static final String                WINDOW_START_TEXT = "2026-07-20T00:00:00Z";

  private static final String                WINDOW_END_TEXT  = "2026-07-21T00:00:00Z";

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

  private Identity                           currentUserIdentityMock;

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

    currentUserIdentityMock = Mockito.mock(Identity.class);
    when(currentUserIdentityMock.getIdentityId()).thenReturn(USER_IDENTITY_ID);
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(currentUserIdentityMock);

    org.exoplatform.services.security.Identity aclIdentity =
                                                           Mockito.mock(org.exoplatform.services.security.Identity.class);

    when(agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID)).thenReturn(new AgendaUserSettings());
    // Nobody shares their busy time here: these tests exercise the tools as
    // glue, and the sharing rule itself is pinned in
    // AgendaAvailabilityServiceImplTest. Left unstubbed, every gate decision
    // would rest on a mock's null.
    when(agendaUserSettingsService.getAvailabilitySharing(anyLong())).thenReturn(AvailabilitySharing.NOBODY);

    when(identityManager.getIdentity(String.valueOf(USER_IDENTITY_ID))).thenReturn(currentUserIdentityMock);
    when(currentUserIdentityMock.getId()).thenReturn(String.valueOf(USER_IDENTITY_ID));
    when(currentUserIdentityMock.isUser()).thenReturn(true);

    tool = new AgendaEventMcpTool(agendaCalendarService,
                                  agendaEventService,
                                  new AgendaAvailabilityServiceImpl(agendaEventService,
                                                                    identityManager,
                                                                    spaceService,
                                                                    agendaUserSettingsService),
                                  new AgendaScheduleConflictServiceImpl(agendaEventService, agendaEventAttendeeService),
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

  // --- test helpers ----------------------------------------------------------

  /** Stubs the space <-> calendar <-> owner-identity chain used by both getSpaceCalendarId and getCalendarSpaceId. */
  private org.exoplatform.social.core.space.model.Space stubSpaceCalendarChain(long spaceId,
                                                                               String prettyName,
                                                                               long ownerIdentityId,
                                                                               long calendarId) {
    org.exoplatform.social.core.space.model.Space space = Mockito.mock(org.exoplatform.social.core.space.model.Space.class);
    when(space.getSpaceId()).thenReturn(spaceId);
    when(space.getPrettyName()).thenReturn(prettyName);
    when(space.getDisplayName()).thenReturn(prettyName);
    when(spaceService.getSpaceById(spaceId)).thenReturn(space);
    when(spaceService.getSpaceByPrettyName(prettyName)).thenReturn(space);

    Identity ownerIdentity = Mockito.mock(Identity.class);
    when(ownerIdentity.getIdentityId()).thenReturn(ownerIdentityId);
    when(ownerIdentity.getRemoteId()).thenReturn(prettyName);
    when(identityManager.getOrCreateSpaceIdentity(prettyName)).thenReturn(ownerIdentity);
    when(identityManager.getIdentity(ownerIdentityId)).thenReturn(ownerIdentity);

    Calendar calendar = new Calendar();
    calendar.setId(calendarId);
    calendar.setOwnerId(ownerIdentityId);
    when(agendaCalendarService.getOrCreateCalendarByOwnerId(ownerIdentityId)).thenReturn(calendar);
    when(agendaCalendarService.getCalendarById(calendarId)).thenReturn(calendar);
    return space;
  }

  /** Builds a real Event with creatorId=0 (so toAgendaEventModel's getUserModel short-circuits to null, no UI lookup). */
  private Event buildEvent(long id, long calendarId, ZonedDateTime start, ZonedDateTime end, EventStatus status) {
    Event event = new Event();
    event.setId(id);
    event.setCalendarId(calendarId);
    event.setCreatorId(0L);
    event.setStart(start);
    event.setEnd(end);
    event.setStatus(status);
    event.setSummary("summary");
    return event;
  }

  // --- decline / cancel guard clauses -----------------------------------------

  @Test
  void declineAgendaEventInvitationWithoutEventIdFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.declineAgendaEventInvitation(null, null));
  }

  @Test
  void declineAgendaEventInvitationWithoutPermissionFails() {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.declineAgendaEventInvitation(EVENT_ID, null));
  }

  @Test
  void declineAgendaEventInvitationWithOccurrenceIdSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);

    tool.declineAgendaEventInvitation(EVENT_ID, "2026-07-20T09:00:00Z");

    verify(agendaEventAttendeeService,
          times(1)).sendUpcomingEventResponse(eq(EVENT_ID), any(), eq(USER_IDENTITY_ID), eq(EventAttendeeResponse.DECLINED));
    verify(agendaEventAttendeeService, never()).sendEventResponse(anyLong(), anyLong(), any());
  }

  @Test
  void cancelAgendaEventWithoutEventIdFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.cancelAgendaEvent(null, null));
  }

  @Test
  void cancelSingleOccurrenceWithoutEditPermissionFails() {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> tool.cancelAgendaEvent(EVENT_ID, "2026-07-20T09:00:00Z"));
  }

  // --- create_agenda_event happy paths + conflicts ----------------------------

  @Test
  void createAgendaEventSucceedsWithRecurrenceCount() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event created = buildEvent(600L, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.createEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(USER_IDENTITY_ID)))
                                                                                                                     .thenReturn(created);

    AgendaEventModel model = tool.createAgendaEvent(SPACE_ID,
                                                    "Kickoff",
                                                    "desc",
                                                    "room 1",
                                                    "2026-07-20T09:00:00Z",
                                                    "2026-07-20T10:00:00Z",
                                                    null,
                                                    "daily",
                                                    2,
                                                    null,
                                                    5,
                                                    null,
                                                    null);

    assertNotNull(model);
    assertTrue(model.getConflicts().isAllAvailable());
    verify(agendaEventService,
          times(1)).createEvent(argThat(e -> e.getRecurrence() != null
                                              && e.getRecurrence().getFrequency() == EventRecurrenceFrequency.DAILY
                                              && e.getRecurrence().getInterval() == 2
                                              && e.getRecurrence().getCount() == 5),
                                any(),
                                any(),
                                any(),
                                any(),
                                any(),
                                anyBoolean(),
                                eq(USER_IDENTITY_ID));
  }

  @Test
  void createAgendaEventDetectsConflictsAndFailsWhenRequested() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);

    // Availability is readable by its owner only, so the acting user is the
    // one attendee whose clash can actually be detected here.
    when(currentUserIdentityMock.getRemoteId()).thenReturn(USERNAME);
    Event busyEvent = buildEvent(700L, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(busyEvent));

    Identity silentIdentity = Mockito.mock(Identity.class);
    when(silentIdentity.getIdentityId()).thenReturn(202L);
    when(identityManager.getOrCreateUserIdentity("silent.user")).thenReturn(silentIdentity);
    // identityManager.getIdentity("202") intentionally left unstubbed -> null -> "unresolved identity" branch

    Identity freeIdentity = Mockito.mock(Identity.class);
    when(freeIdentity.getIdentityId()).thenReturn(203L);
    when(freeIdentity.isUser()).thenReturn(true);
    when(freeIdentity.getId()).thenReturn("203");
    when(freeIdentity.getRemoteId()).thenReturn("free.user");
    when(identityManager.getOrCreateUserIdentity("free.user")).thenReturn(freeIdentity);
    when(identityManager.getIdentity("203")).thenReturn(freeIdentity);

    IllegalStateException ex =
                              assertThrows(IllegalStateException.class,
                                           () -> tool.createAgendaEvent(SPACE_ID,
                                                                        "Kickoff",
                                                                        null,
                                                                        null,
                                                                        "2026-07-20T09:00:00Z",
                                                                        "2026-07-20T10:00:00Z",
                                                                        List.of(USERNAME, "silent.user", "free.user"),
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        true,
                                                                        null));
    assertTrue(ex.getMessage().contains("1"));
    verify(agendaEventService, never()).createEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
  }

  @Test
  void conflictsReportBusyAndNameTheAttendeesThatCouldNotBeChecked() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    when(currentUserIdentityMock.getRemoteId()).thenReturn(USERNAME);

    // A TENTATIVE-status event is reported as busy like any other. The event
    // query behind free/busy filters on CONFIRMED, so a "tentative" clash
    // could never actually be produced and is no longer derived from a field
    // the query cannot vary.
    Event tentative = buildEvent(700L, CALENDAR_ID, START, END, EventStatus.TENTATIVE);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(tentative));

    Identity colleague = Mockito.mock(Identity.class);
    when(colleague.getIdentityId()).thenReturn(400L);
    when(colleague.getId()).thenReturn("400");
    when(colleague.isUser()).thenReturn(true);
    when(colleague.getRemoteId()).thenReturn("colleague");
    when(identityManager.getOrCreateUserIdentity("colleague")).thenReturn(colleague);
    when(identityManager.getIdentity("400")).thenReturn(colleague);

    Event created = buildEvent(600L, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.createEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(USER_IDENTITY_ID)))
                                                                                                                     .thenReturn(created);

    AgendaEventModel model = tool.createAgendaEvent(SPACE_ID,
                                                    "Kickoff",
                                                    null,
                                                    null,
                                                    "2026-07-20T09:00:00Z",
                                                    "2026-07-20T10:00:00Z",
                                                    List.of(USERNAME, "colleague"),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null);

    assertEquals(1, model.getConflicts().getConflicts().size());
    assertEquals("busy", model.getConflicts().getConflicts().get(0).getStatus());
    assertEquals(List.of("colleague"),
                 model.getConflicts().getNotDisclosed(),
                 "an attendee whose availability we may not read must be named, not assumed free");
    assertFalse(model.getConflicts().isAllAvailable());
  }

  @Test
  void createAgendaEventResolvesSpaceByDisplayNameViaMemberSpacesFallback() throws Exception {
    when(spaceService.getSpaceByPrettyName("Eva Team")).thenReturn(null);
    org.exoplatform.social.core.space.model.Space memberSpace =
                                                               Mockito.mock(org.exoplatform.social.core.space.model.Space.class);
    when(memberSpace.getSpaceId()).thenReturn(SPACE_ID);
    when(memberSpace.getDisplayName()).thenReturn("Eva Team");
    when(memberSpace.getPrettyName()).thenReturn("eva-team");
    @SuppressWarnings("unchecked")
    org.exoplatform.commons.utils.ListAccess<org.exoplatform.social.core.space.model.Space> memberSpaces =
                                                                                                          Mockito.mock(org.exoplatform.commons.utils.ListAccess.class);
    when(memberSpaces.getSize()).thenReturn(1);
    when(memberSpaces.load(0, 1)).thenReturn(new org.exoplatform.social.core.space.model.Space[] { memberSpace });
    when(spaceService.getMemberSpaces(USERNAME)).thenReturn(memberSpaces);
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> tool.createAgendaEvent(null,
                                              "summary",
                                              null,
                                              null,
                                              "2026-07-20T14:00:00Z",
                                              "2026-07-20T15:00:00Z",
                                              null,
                                              null,
                                              null,
                                              null,
                                              null,
                                              null,
                                              "Eva Team"));
  }

  // --- update_agenda_event happy paths -----------------------------------------

  @Test
  void updateAgendaEventUpdatesFieldsWithoutRecurrence() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event existing = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(existing);

    AgendaEventModel model =
                           tool.updateAgendaEvent(EVENT_ID, "New summary", null, null, null, null, null, null, null, null, null);

    assertNotNull(model);
    assertTrue(model.getConflicts().isAllAvailable());
    verify(agendaEventService,
          times(1)).updateEventFields(eq(EVENT_ID),
                                      eq(Collections.singletonMap("summary", Collections.singletonList("New summary"))),
                                      eq(false),
                                      eq(false),
                                      eq(USER_IDENTITY_ID));
    verify(agendaEventService, never()).updateEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
  }

  @Test
  void updateAgendaEventNotFoundFails() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(null);
    assertThrows(ObjectNotFoundException.class,
                 () -> tool.updateAgendaEvent(EVENT_ID, "x", null, null, null, null, null, null, null, null, null));
  }

  @Test
  void updateAgendaEventWithRecurrenceUntilSucceeds() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event existing = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(existing);
    when(agendaEventService.updateEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(USER_IDENTITY_ID)))
                                                                                                                     .thenReturn(existing);

    AgendaEventModel model = tool.updateAgendaEvent(EVENT_ID,
                                                    "Weekly sync",
                                                    "desc",
                                                    "room",
                                                    null,
                                                    null,
                                                    "weekly",
                                                    null,
                                                    "2026-09-01T00:00:00Z",
                                                    null,
                                                    null);

    assertNotNull(model);
    verify(agendaEventService,
          times(1)).updateEvent(argThat(e -> e.getRecurrence() != null
                                             && e.getRecurrence().getFrequency() == EventRecurrenceFrequency.WEEKLY
                                             && e.getRecurrence().getUntil() != null),
                                any(),
                                any(),
                                any(),
                                any(),
                                any(),
                                eq(false),
                                eq(USER_IDENTITY_ID));
    verify(agendaEventService, never()).updateEventFields(anyLong(), any(), anyBoolean(), anyBoolean(), anyLong());
  }

  // --- invite_users / invite_space happy paths --------------------------------

  @Test
  void inviteUsersToAgendaEventSucceeds() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(agendaEventAttendeeService.getEventAttendees(EVENT_ID)).thenReturn(new EventAttendeeList(Collections.emptyList()));
    Identity johnIdentity = Mockito.mock(Identity.class);
    when(johnIdentity.getIdentityId()).thenReturn(300L);
    when(identityManager.getOrCreateUserIdentity("john")).thenReturn(johnIdentity);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event fetched = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(fetched);

    AgendaEventModel model = tool.inviteUsersToAgendaEvent(EVENT_ID, List.of("john"));

    assertNotNull(model);
    verify(agendaEventAttendeeService,
          times(1)).saveEventAttendees(any(), any(), eq(USER_IDENTITY_ID), eq(false), eq(false), any());
  }

  @Test
  void inviteSpaceToAgendaEventSucceeds() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(SPACE_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(agendaEventAttendeeService.getEventAttendees(EVENT_ID)).thenReturn(new EventAttendeeList(Collections.emptyList()));
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event fetched = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(fetched);

    AgendaEventModel model = tool.inviteSpaceToAgendaEvent(EVENT_ID, SPACE_ID);

    assertNotNull(model);
    verify(agendaEventAttendeeService,
          times(1)).saveEventAttendees(any(), any(), eq(USER_IDENTITY_ID), eq(false), eq(false), any());
  }

  // --- date polls happy paths --------------------------------------------------

  @Test
  void createDatePollSucceedsWithRanking() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(userAcl.hasAccessPermission(any(), eq("800"), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    when(identityManager.getIdentity(USER_IDENTITY_ID)).thenReturn(currentUserIdentityMock);
    when(currentUserIdentityMock.isUser()).thenReturn(true);
    when(currentUserIdentityMock.getRemoteId()).thenReturn(USERNAME);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(Collections.emptyList());
    Event created = buildEvent(800L, CALENDAR_ID, START, END, EventStatus.TENTATIVE);
    when(agendaEventService.createEvent(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(USER_IDENTITY_ID)))
                                                                                                                     .thenReturn(created);
    when(agendaEventService.getEventById(eq(800L), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(created);
    when(agendaEventDatePollService.getEventDateOptions(eq(800L), eq(ZoneOffset.UTC))).thenReturn(Collections.emptyList());

    DatePollModel model = tool.createDatePoll(SPACE_ID,
                                              "Team sync",
                                              List.of("2026-07-20T09:00:00Z|2026-07-20T09:30:00Z",
                                                      "2026-07-20T10:00:00Z|2026-07-20T10:30:00Z"),
                                              null,
                                              null,
                                              true,
                                              null);

    assertNotNull(model);
    assertEquals(800L, model.getEventId());
  }

  @Test
  void createDatePollWithInvalidSlotFormatFails() throws Exception {
    when(userAcl.hasPermission(any(),
                               eq(String.valueOf(SPACE_ID)),
                               any(),
                               any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    assertThrows(IllegalArgumentException.class,
                 () -> tool.createDatePoll(SPACE_ID, "poll", List.of("not-a-valid-slot"), null, null, false, null));
  }

  @Test
  void voteDatePollSucceedsAndReturnsResultsWithVoters() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    Event event = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.TENTATIVE);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(event);
    EventDateOption option = new EventDateOption(10L, EVENT_ID, START, END, false, true, List.of(USER_IDENTITY_ID));
    when(agendaEventDatePollService.getEventDateOptions(eq(EVENT_ID), eq(ZoneOffset.UTC))).thenReturn(List.of(option));
    when(identityManager.getIdentity(USER_IDENTITY_ID)).thenReturn(currentUserIdentityMock);
    when(currentUserIdentityMock.isUser()).thenReturn(true);
    when(currentUserIdentityMock.getRemoteId()).thenReturn(USERNAME);

    DatePollModel model = tool.voteDatePoll(EVENT_ID, List.of(10L));

    assertEquals(1, model.getOptions().size());
    assertEquals(1, model.getOptions().get(0).getVoteCount());
    assertEquals(List.of(USERNAME), model.getOptions().get(0).getVoters());
    verify(agendaEventDatePollService, times(1)).saveEventVotes(EVENT_ID, List.of(10L), USER_IDENTITY_ID);
  }

  @Test
  void getDatePollResultsSucceedsWithNoVoters() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    Event event = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.TENTATIVE);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(event);
    EventDateOption option = new EventDateOption(10L, EVENT_ID, START, END, false, false, null);
    when(agendaEventDatePollService.getEventDateOptions(eq(EVENT_ID), eq(ZoneOffset.UTC))).thenReturn(List.of(option));

    DatePollModel model = tool.getDatePollResults(EVENT_ID);

    assertEquals(1, model.getOptions().size());
    assertEquals(0, model.getOptions().get(0).getVoteCount());
    assertTrue(model.getOptions().get(0).getVoters().isEmpty());
  }

  @Test
  void getDatePollResultsNotFoundFails() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> tool.getDatePollResults(EVENT_ID));
  }

  @Test
  void confirmDatePollSucceeds() throws Exception {
    EventDateOption option = new EventDateOption(10L, EVENT_ID, START, END, false, false, null);
    when(agendaEventDatePollService.getEventDateOption(eq(10L), eq(ZoneOffset.UTC))).thenReturn(option);
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event event = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(event);

    AgendaEventModel model = tool.confirmDatePoll(10L);

    assertNotNull(model);
    assertTrue(model.getConflicts().isAllAvailable());
    verify(agendaEventService, times(1)).selectEventDateOption(EVENT_ID, 10L, USER_IDENTITY_ID);
  }

  @Test
  void confirmDatePollNotFoundFails() {
    when(agendaEventDatePollService.getEventDateOption(eq(10L), eq(ZoneOffset.UTC))).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> tool.confirmDatePoll(10L));
  }

  // --- free/busy + suggestions --------------------------------------------------

  @Test
  void getAvailabilityMergesOverlappingBusyIntervalsAndComputesFreeBlocks() throws Exception {
    Event a = buildEvent(1L,
                        CALENDAR_ID,
                        ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC),
                        ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC),
                        EventStatus.CONFIRMED);
    Event b = buildEvent(2L,
                        CALENDAR_ID,
                        ZonedDateTime.of(2026, 7, 20, 9, 30, 0, 0, ZoneOffset.UTC),
                        ZonedDateTime.of(2026, 7, 20, 10, 30, 0, 0, ZoneOffset.UTC),
                        EventStatus.CONFIRMED);
    Event c = buildEvent(3L,
                        CALENDAR_ID,
                        ZonedDateTime.of(2026, 7, 20, 14, 0, 0, 0, ZoneOffset.UTC),
                        ZonedDateTime.of(2026, 7, 20, 15, 0, 0, 0, ZoneOffset.UTC),
                        EventStatus.CONFIRMED);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(a, b, c));

    List<AvailabilityModel> result =
                                    tool.getAvailability(List.of(USERNAME), "2026-07-20T08:00:00Z", "2026-07-20T20:00:00Z");

    assertEquals(1, result.size());
    AvailabilityModel availability = result.get(0);
    assertEquals(2, availability.getBusy().size());
    assertEquals(3, availability.getFree().size());
  }

  @Test
  void getAvailabilityFullyBusyWindowYieldsNoFreeBlocks() throws Exception {
    Event fullyBusy = buildEvent(4L,
                                CALENDAR_ID,
                                ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, ZoneOffset.UTC),
                                ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC),
                                EventStatus.CONFIRMED);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(fullyBusy));

    List<AvailabilityModel> result =
                                    tool.getAvailability(List.of(USERNAME), "2026-07-20T08:00:00Z", "2026-07-20T10:00:00Z");

    assertEquals(1, result.size());
    assertTrue(result.get(0).getFree().isEmpty());
  }

  @Test
  void getAvailabilitySkipsUnknownUsernames() throws Exception {
    when(identityManager.getOrCreateUserIdentity("ghost")).thenReturn(null);

    List<AvailabilityModel> result =
                                    tool.getAvailability(List.of("ghost"), "2026-07-20T08:00:00Z", "2026-07-20T20:00:00Z");

    assertTrue(result.isEmpty());
  }

  @Test
  void getAvailabilityOfAnotherUserIsRefused() throws Exception {
    Identity colleague = Mockito.mock(Identity.class);
    when(colleague.getIdentityId()).thenReturn(400L);
    when(colleague.getId()).thenReturn("400");
    when(colleague.isUser()).thenReturn(true);
    when(identityManager.getOrCreateUserIdentity("colleague")).thenReturn(colleague);
    when(identityManager.getIdentity("400")).thenReturn(colleague);
    // Stubbed so that, with the guard gone, the call would succeed and the
    // assertion below is what fails.
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), anyLong()))
                                                                            .thenReturn(List.of(buildEvent(9L,
                                                                                                           CALENDAR_ID,
                                                                                                           START,
                                                                                                           END,
                                                                                                           EventStatus.CONFIRMED)));

    assertThrows(IllegalAccessException.class,
                 () -> tool.getAvailability(List.of("colleague"), "2026-07-20T08:00:00Z", "2026-07-20T20:00:00Z"));
  }

  @Test
  void suggestMeetingTimeWithAnotherAttendeeIsRefused() throws Exception {
    Identity colleague = Mockito.mock(Identity.class);
    when(colleague.getIdentityId()).thenReturn(400L);
    when(colleague.getId()).thenReturn("400");
    when(colleague.isUser()).thenReturn(true);
    when(identityManager.getOrCreateUserIdentity("colleague")).thenReturn(colleague);
    when(identityManager.getIdentity("400")).thenReturn(colleague);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), anyLong())).thenReturn(Collections.emptyList());

    assertThrows(IllegalAccessException.class,
                 () -> tool.suggestMeetingTime(List.of(USERNAME, "colleague"),
                                               30,
                                               "2026-07-20T09:00:00Z",
                                               "2026-07-20T13:00:00Z",
                                               null));
  }

  @Test
  void suggestMeetingTimeFindsFreeSlotsRespectingMorningConstraint() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(Collections.emptyList());

    List<TimeBlockModel> suggestions = tool.suggestMeetingTime(List.of(USERNAME),
                                                               30,
                                                               "2026-07-20T09:00:00Z",
                                                               "2026-07-20T13:00:00Z",
                                                               "mornings only please");

    assertEquals(6, suggestions.size());
  }

  @Test
  void suggestMeetingTimeWithoutConstraintsReturnsAllSlots() throws Exception {
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(Collections.emptyList());

    List<TimeBlockModel> suggestions =
                                      tool.suggestMeetingTime(List.of(USERNAME),
                                                              30,
                                                              "2026-07-20T09:00:00Z",
                                                              "2026-07-20T13:00:00Z",
                                                              null);

    assertEquals(8, suggestions.size());
  }

  // --- attendees / response / reminders / conference / search -----------------

  @Test
  void getEventAttendeesWithoutEventIdFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.getEventAttendees(0L));
  }

  @Test
  void getEventAttendeesPublicSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    Event event = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(agendaEventAttendeeService.getEventAttendees(EVENT_ID))
                                                                .thenReturn(new EventAttendeeList(List.of(new EventAttendee(1L,
                                                                                                                            0L,
                                                                                                                            EventAttendeeResponse.ACCEPTED))));

    List<AgendaEventAttendeeModel> attendees = tool.getEventAttendees(EVENT_ID);

    assertEquals(1, attendees.size());
    assertEquals(EventAttendeeResponse.ACCEPTED, attendees.get(0).getResponse());
  }

  @Test
  void respondToAgendaEventWithoutResponseFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.respondToAgendaEvent(EVENT_ID, " ", null));
  }

  @Test
  void respondToAgendaEventRejectsNeedsAction() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> tool.respondToAgendaEvent(EVENT_ID, "needs_action", null));
  }

  @Test
  void respondToAgendaEventSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event fetched = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(eq(EVENT_ID), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(fetched);

    AgendaEventModel model = tool.respondToAgendaEvent(EVENT_ID, "tentative", "on my way");

    assertNotNull(model);
    verify(agendaEventAttendeeService,
          times(1)).sendEventResponse(EVENT_ID, USER_IDENTITY_ID, EventAttendeeResponse.TENTATIVE);
  }

  @Test
  void setEventRemindersSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    Event event = buildEvent(EVENT_ID, CALENDAR_ID, START, END, EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(agendaEventReminderService.getEventReminders(EVENT_ID,
                                                      USER_IDENTITY_ID)).thenReturn(List.of(new EventReminder(USER_IDENTITY_ID,
                                                                                                              30,
                                                                                                              ReminderPeriodType.MINUTE)));

    List<String> reminders = tool.setEventReminders(EVENT_ID, List.of("30 MINUTE", "1 DAY"));

    assertEquals(1, reminders.size());
    assertEquals("30 MINUTE", reminders.get(0));
    verify(agendaEventReminderService, times(1)).saveEventReminders(eq(event), any(), eq(USER_IDENTITY_ID));
  }

  @Test
  void setEventRemindersWithInvalidFormatFails() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> tool.setEventReminders(EVENT_ID, List.of("garbage")));
  }

  @Test
  void getEventConferenceSucceeds() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    EventConference conference = new EventConference();
    conference.setType("jitsi");
    conference.setUrl("https://meet.example/room");
    when(agendaEventConferenceService.getEventConferences(EVENT_ID)).thenReturn(List.of(conference));

    ConferenceModel model = tool.getEventConference(EVENT_ID);

    assertEquals("jitsi", model.getType());
    assertEquals("https://meet.example/room", model.getUrl());
  }

  @Test
  void getEventConferenceReturnsEmptyModelWhenNone() throws Exception {
    when(userAcl.hasAccessPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(agendaEventConferenceService.getEventConferences(EVENT_ID)).thenReturn(Collections.emptyList());

    ConferenceModel model = tool.getEventConference(EVENT_ID);

    assertNull(model.getType());
    assertNull(model.getUrl());
  }

  @Test
  void setEventConferenceSucceedsWithUrl() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);

    ConferenceModel model = tool.setEventConference(EVENT_ID, "https://meet.example/x", null);

    assertEquals("web", model.getType());
    assertEquals("https://meet.example/x", model.getUrl());
    verify(agendaEventConferenceService, times(1)).saveEventConferences(eq(EVENT_ID), any());
  }

  @Test
  void setEventConferenceWithBlankUrlRemovesConference() throws Exception {
    when(userAcl.hasEditPermission(any(), eq(String.valueOf(EVENT_ID)), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);

    ConferenceModel model = tool.setEventConference(EVENT_ID, " ", null);

    assertNull(model.getType());
    assertNull(model.getUrl());
    verify(agendaEventConferenceService, times(1)).saveEventConferences(EVENT_ID, Collections.emptyList());
  }

  @Test
  void searchAgendaEventsSucceeds() {
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    EventSearchResult result = new EventSearchResult();
    result.setId(EVENT_ID);
    result.setCalendarId(CALENDAR_ID);
    result.setCreatorId(0L);
    result.setStart(START);
    result.setEnd(END);
    result.setSummary("Kickoff");
    when(agendaEventService.search(any())).thenReturn(List.of(result));

    List<AgendaEventModel> models = tool.searchAgendaEvents("kickoff", null, null, null);

    assertEquals(1, models.size());
    assertEquals("Kickoff", models.get(0).getSummary());
  }

  @Test
  void searchAgendaEventsFiltersOutOfRangeResults() {
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    EventSearchResult result = new EventSearchResult();
    result.setId(EVENT_ID);
    result.setCalendarId(CALENDAR_ID);
    result.setCreatorId(0L);
    result.setStart(START);
    result.setEnd(END);
    result.setSummary("Old meeting");
    when(agendaEventService.search(any())).thenReturn(List.of(result));

    List<AgendaEventModel> models =
                                  tool.searchAgendaEvents("meeting", "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z", null);

    assertTrue(models.isEmpty());
  }

  @SuppressWarnings("unused")
  private Event mockEvent() {
    return Mockito.mock(Event.class);
  }


  // --- get_schedule_conflicts ----------------------------------------------

  @Test
  void getScheduleConflictsWithoutAWindowFails() {
    assertThrows(IllegalArgumentException.class, () -> tool.getScheduleConflicts(null, WINDOW_END_TEXT));
    assertThrows(IllegalArgumentException.class, () -> tool.getScheduleConflicts(WINDOW_START_TEXT, " "));
  }

  @Test
  void getScheduleConflictsReportsOneGroupWithWhatTheCallerNeeds() throws Exception {
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event mine = buildEvent(601L, CALENDAR_ID, at(9), at(11), EventStatus.CONFIRMED);
    mine.setCreatorId(USER_IDENTITY_ID);
    Event theirs = buildEvent(602L, CALENDAR_ID, at(10), at(12), EventStatus.CONFIRMED);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(mine, theirs));

    ScheduleConflictsModel model = tool.getScheduleConflicts(WINDOW_START_TEXT, WINDOW_END_TEXT);

    assertNotNull(model);
    assertEquals(WINDOW_START_TEXT, model.getFromDate());
    assertEquals(WINDOW_END_TEXT, model.getToDate());
    assertFalse(model.isTruncated());
    assertEquals(1, model.getConflicts().size());
    List<ScheduleConflictEventModel> events = model.getConflicts().get(0).getEvents();
    assertEquals(2, events.size());
    assertEquals(601L, events.get(0).getEventId());
    assertEquals("summary", events.get(0).getSummary());
    assertNotNull(events.get(0).getStart());
    assertNotNull(events.get(0).getUrl());
    assertEquals(SPACE_ID, events.get(0).getSpaceId());
    assertTrue(events.get(0).isCreatedByMe());
    assertFalse(events.get(1).isCreatedByMe());
  }

  /**
   * The whole point of moving the arithmetic server-side: 09:00 - 10:00
   * followed by 10:00 - 11:00 comes back as nothing to report.
   */
  @Test
  void getScheduleConflictsReportsNothingForBackToBackEvents() throws Exception {
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID)))
                                                                                       .thenReturn(List.of(buildEvent(601L,
                                                                                                                      CALENDAR_ID,
                                                                                                                      at(9),
                                                                                                                      at(10),
                                                                                                                      EventStatus.CONFIRMED),
                                                                                                           buildEvent(602L,
                                                                                                                      CALENDAR_ID,
                                                                                                                      at(10),
                                                                                                                      at(11),
                                                                                                                      EventStatus.CONFIRMED)));

    assertTrue(tool.getScheduleConflicts(WINDOW_START_TEXT, WINDOW_END_TEXT).getConflicts().isEmpty());
  }

  /**
   * A materialised occurrence has no id of its own, so what is published is
   * the series id plus the occurrence's date — the pair the write tools that
   * act on a single occurrence expect.
   */
  @Test
  void getScheduleConflictsPublishesTheSeriesIdOfAnOccurrence() throws Exception {
    stubSpaceCalendarChain(SPACE_ID, SPACE_PRETTY_NAME, OWNER_IDENTITY_ID, CALENDAR_ID);
    Event occurrence = buildEvent(0L, CALENDAR_ID, at(9), at(11), EventStatus.CONFIRMED);
    occurrence.setParentId(900L);
    occurrence.setOccurrence(new EventOccurrence(at(9)));
    Event other = buildEvent(602L, CALENDAR_ID, at(10), at(12), EventStatus.CONFIRMED);
    when(agendaEventService.getEvents(any(), eq(ZoneOffset.UTC), eq(USER_IDENTITY_ID))).thenReturn(List.of(occurrence, other));

    ScheduleConflictEventModel reported = tool.getScheduleConflicts(WINDOW_START_TEXT, WINDOW_END_TEXT)
                                              .getConflicts()
                                              .get(0)
                                              .getEvents()
                                              .get(0);

    assertEquals(900L, reported.getEventId());
    assertNotNull(reported.getOccurrenceId());
  }

  private ZonedDateTime at(int hour) {
    return ZonedDateTime.of(2026, 7, 20, hour, 0, 0, 0, ZoneOffset.UTC);
  }


}
