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
package org.exoplatform.agenda.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventSplit;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Pins the status contract of {@code POST /agenda/rest/events/{id}/split}, a
 * Spring MVC resource since the integration review of eXIP 7.3.0.21: the
 * continuation's identifier on success; 403, 404, and 400 with the message
 * code in {@code {"message": ...}}, the date that blocks beside it for a limit
 * below the Yes answers; a fixed code, never an exception's text, for a bad
 * date or zone, an unreadable body and a failure. Also pins how the change's
 * participants are resolved, and in which zone its dates are read.
 * <p>
 * The JSON converter is set up as the platform's
 * ({@code spring.jackson.deserialization.fail-on-null-for-primitives=false}),
 * on Jackson 3 as Spring MVC's is.
 */
class AgendaEventSplitRestTest {

  private static final String        USERNAME         = "testuser1";

  private static final long          USER_IDENTITY_ID = 5L;

  private static final long          EVENT_ID         = 42L;

  private static final String        SPLIT_URL        = "/events/" + EVENT_ID + "/split";

  private AgendaEventService         agendaEventService;

  private AgendaEventAttendeeService attendeeService;

  private IdentityManager            identityManager;

  private MockMvc                    mockMvc;

  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    agendaEventService = mock(AgendaEventService.class);
    attendeeService = mock(AgendaEventAttendeeService.class);
    Identity userIdentity = mock(Identity.class);
    when(userIdentity.getId()).thenReturn(String.valueOf(USER_IDENTITY_ID));
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(userIdentity);
    AgendaEventSplitRest resource = new AgendaEventSplitRest(identityManager, agendaEventService, attendeeService);
    JsonMapper platformMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    mockMvc = MockMvcBuilders.standaloneSetup(resource)
                             .setMessageConverters(new JacksonJsonHttpMessageConverter(platformMapper))
                             .build();
  }

  @Test
  void aSplitRemovingAnEmailNobodyInvitedCreatesNoIdentity() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split(null, false, body("[]", "[" + attendee(null, "GUEST_USER", "nobody@example.com") + "]"))
                                                                                                 .andExpect(status().isForbidden())
                                                                                                 .andExpect(jsonPath("$.message").value("agenda.split.forbidden"));

    assertTrue(capturedSplit().removedIdentityIds().isEmpty());
    verify(identityManager, never()).getOrCreateIdentity(any(), eq("nobody@example.com"));
  }

  @Test
  void aSplitResolvesARemovalAmongTheSeriesAttendeesByRemoteId() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split(null, false, body("[]", "[" + attendee(null, OrganizationIdentityProvider.NAME, "testuser2") + ","
        + attendee(null, OrganizationIdentityProvider.NAME, "testuser3") + "]"));

    assertEquals(Set.of(7L), capturedSplit().removedIdentityIds());
  }

  @Test
  void aSplitResolvesARemovalAmongTheSeriesAttendeesByIdentityId() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split(null, false, body("[]", "[" + attendee("7", null, null) + "," + attendee("8", null, null) + "]"));

    assertEquals(Set.of(7L), capturedSplit().removedIdentityIds());
  }

  @Test
  void aSplitResolvesAnAddedEmailGuestAsANewEventDoes() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();
    Identity guest = mock(Identity.class);
    when(guest.getId()).thenReturn("9");
    when(identityManager.getOrCreateIdentity("GUEST_USER", "guest@example.com")).thenReturn(guest);

    split(null, true, body("[" + attendee(null, "GUEST_USER", "guest@example.com") + "]", "[]"));

    assertEquals(List.of(9L), capturedSplit().addedIdentityIds());
    verify(agendaEventService).splitEvent(eq(EVENT_ID),
                                          any(),
                                          eq(ZonedDateTime.parse("2026-10-05T09:00:00Z")),
                                          eq(true),
                                          eq(true),
                                          eq(USER_IDENTITY_ID));
  }

  @Test
  void aSplitInvitesAnAddedAddressOfAPlatformUserAsThatUser() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();
    Identity user = mock(Identity.class);
    when(user.getId()).thenReturn("11");
    when(user.getProviderId()).thenReturn(OrganizationIdentityProvider.NAME);
    when(user.getRemoteId()).thenReturn("testuser3");
    when(attendeeService.getUserIdentityByEmail(eq("Testuser3@Example.com"), anyLong())).thenReturn(user);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser3")).thenReturn(user);

    split(null, true, body("[" + attendee("testuser3@example.com", "GUEST_USER", "Testuser3@Example.com") + "]", "[]"));

    assertEquals(List.of(11L), capturedSplit().addedIdentityIds());
    verify(identityManager, never()).getOrCreateIdentity(eq("GUEST_USER"), any());
  }

  @Test
  void aRefusedSplitGetsBadRequestWithItsMessageCodeAndDate() throws Exception {
    seriesAttendees(7L, "testuser2");
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new IllegalArgumentException("agenda.capacity.closeNeedsConfirmation"));
    split(null, false, body("[]", "[]")).andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").value("agenda.capacity.closeNeedsConfirmation"))
                                        .andExpect(jsonPath("$.date").doesNotExist());

    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new CapacityBelowAcceptedException(LocalDate.of(2026,
                                                                                                                                                                10,
                                                                                                                                                                7)));
    split(null, false, body("[]", "[]")).andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").value("agenda.capacity.belowAccepted"))
                                        .andExpect(jsonPath("$.date").value("2026-10-07"));
  }

  @Test
  void aSplitOfAnUnknownSeriesGetsNotFound() throws Exception {
    seriesAttendees(7L, "testuser2");
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND));

    split(null, false, body("[]", "[]")).andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.message").value("agenda.eventNotFound"));
  }

  @Test
  void aSplitAnswersTheContinuationsIdentifier() throws Exception {
    seriesAttendees(7L, "testuser2");
    Event continuation = new Event();
    continuation.setId(77L);
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong())).thenReturn(continuation);

    split("UTC", false, body("[]", "[]")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(77));
  }

  @Test
  void aSplitPassesItsZoneAndTheZoneTheSeriesWasReadIn() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split("Etc/UTC", false, body("[]", "[]"));

    EventSplit split = capturedSplit();
    assertEquals(ZoneId.of("UTC"), split.event().getTimeZoneId());
    assertEquals(ZoneId.of("Etc/UTC"), split.readZone());
  }

  @Test
  void aSplitGivingNoZoneReadsItsDatesInTheZoneTheSeriesWasReadInAndKeepsTheSeriesZone() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split("Europe/Paris", false, body(null, "2026-10-05T10:00:00", "2026-10-05T11:00:00", "[]", "[]"))
                                                                                                   .andExpect(status().isForbidden());

    EventSplit split = capturedSplit();
    assertEquals(null, split.event().getTimeZoneId());
    assertEquals(ZonedDateTime.parse("2026-10-05T08:00:00Z").toInstant(), split.event().getStart().toInstant());
  }

  @Test
  void aSplitGivingNoZoneNorTheZoneTheSeriesWasReadInKeepsTheSeriesZone() throws Exception {
    seriesAttendees(7L, "testuser2");
    refusedAsForbidden();

    split(null, false, body(null, null, null, "[]", "[]"));

    assertEquals(null, capturedSplit().event().getTimeZoneId());
  }

  @Test
  void aSplitInAZoneThatDoesNotExistGetsBadRequestWithACodeAndWritesNothing() throws Exception {
    seriesAttendees(7L, "testuser2");

    split("Mars/Olympus_Mons", false, body("[]", "[]")).andExpect(status().isBadRequest())
                                                       .andExpect(jsonPath("$.message").value("agenda.split.invalidDate"));

    verify(agendaEventService, never()).splitEvent(anyLong(), any(), any(), anyBoolean(), anyBoolean(), anyLong());
  }

  @Test
  void aSplitFromADateThatDoesNotParseGetsBadRequestWithACode() throws Exception {
    seriesAttendees(7L, "testuser2");
    String body = body("[]", "[]").replace("2026-10-05T09:00:00.000Z", "the fifth of October");

    String answer = split(null, false, body).andExpect(status().isBadRequest())
                                            .andExpect(jsonPath("$.message").value("agenda.split.invalidDate"))
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString();

    assertFalse(answer.contains("fifth"), answer);
  }

  @Test
  void aFailedSplitAnswersAFixedCodeAndNeverTheExceptionText() throws Exception {
    seriesAttendees(7L, "testuser2");
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new IllegalStateException("could not execute statement [Duplicate entry] [insert into EXO_AGENDA_EVENT]"));

    String answer = split(null, false, body("[]", "[]")).andExpect(status().isInternalServerError())
                                                        .andExpect(jsonPath("$.message").value("agenda.split.failed"))
                                                        .andReturn()
                                                        .getResponse()
                                                        .getContentAsString();

    assertFalse(answer.contains("EXO_AGENDA_EVENT"), answer);
  }

  @Test
  void anUncodedRefusalNeverEchoesItsText() throws Exception {
    seriesAttendees(7L, "testuser2");
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new IllegalArgumentException("Unknown column 'x' in where clause"));

    String answer = split(null, false, body("[]", "[]")).andExpect(status().isBadRequest())
                                                        .andExpect(jsonPath("$.message").value("agenda.split.invalid"))
                                                        .andReturn()
                                                        .getResponse()
                                                        .getContentAsString();

    assertFalse(answer.contains("column"), answer);
  }

  @Test
  void anUnreadableBodyGetsBadRequestWithACodeAndNeverTheParserText() throws Exception {
    String answer = split(null, false, "{\"event\": {\"summary\": ").andExpect(status().isBadRequest())
                                                                    .andExpect(jsonPath("$.message").value("agenda.split.invalid"))
                                                                    .andReturn()
                                                                    .getResponse()
                                                                    .getContentAsString();

    assertFalse(answer.contains("Unexpected"), answer);
    verify(agendaEventService, never()).splitEvent(anyLong(), any(), any(), anyBoolean(), anyBoolean(), anyLong());
  }

  @Test
  void aSplitWithNoEventGetsBadRequestWithItsCode() throws Exception {
    split(null, false, "{\"splitFrom\": \"2026-10-05T09:00:00.000Z\"}").andExpect(status().isBadRequest())
                                                                      .andExpect(jsonPath("$.message").value("agenda.eventMandatory"));
    mockMvc.perform(post("/events/0/split").with(request -> {
      request.setRemoteUser(USERNAME);
      return request;
    }).contentType(MediaType.APPLICATION_JSON).content(body("[]", "[]")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("agenda.eventIdMandatory"));
  }

  /** The series holds one attendee, the user given. */
  private void seriesAttendees(long identityId, String username) {
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.getProviderId()).thenReturn(OrganizationIdentityProvider.NAME);
    when(identity.getRemoteId()).thenReturn(username);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(attendeeService.getEventAttendees(EVENT_ID))
                                                     .thenReturn(new EventAttendeeList(List.of(new EventAttendee(1L,
                                                                                                                 EVENT_ID,
                                                                                                                 identityId,
                                                                                                                 EventAttendeeResponse.ACCEPTED))));
  }

  /**
   * Refuses the split as asked by a caller who may not split the series, so
   * that the split asked is captured without the continuation being read
   * back.
   */
  private void refusedAsForbidden() throws Exception {
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
                                                                                                    .thenThrow(new IllegalAccessException("not allowed"));
  }

  private ResultActions split(String timeZoneId, boolean confirmClose, String body) throws Exception {
    var request = post(SPLIT_URL).with(r -> {
      r.setRemoteUser(USERNAME);
      return r;
    }).contentType(MediaType.APPLICATION_JSON).content(body).param("confirmClose", String.valueOf(confirmClose));
    if (timeZoneId != null) {
      request.param("timeZoneId", timeZoneId);
    }
    return mockMvc.perform(request);
  }

  /**
   * A change that splits the series from the 5th of October 2026, in UTC.
   */
  private static String body(String added, String removed) {
    return body("UTC", null, null, added, removed);
  }

  private static String body(String timeZoneId, String start, String end, String added, String removed) {
    return "{\"event\": {\"summary\": \"summary\"" + field("timeZoneId", timeZoneId) + field("start", start) + field("end", end)
        + "}, \"addedAttendees\": " + added + ", \"removedAttendees\": " + removed
        + ", \"splitFrom\": \"2026-10-05T09:00:00.000Z\", \"sendInvitation\": true}";
  }

  private static String field(String name, String value) {
    return value == null ? "" : ", \"" + name + "\": \"" + value + "\"";
  }

  private static String attendee(String id, String providerId, String remoteId) {
    return "{\"id\": 0, \"identity\": {" + String.join(", ",
                                                     List.of("\"id\": " + quoted(id),
                                                             "\"providerId\": " + quoted(providerId),
                                                             "\"remoteId\": " + quoted(remoteId)))
        + "}}";
  }

  private static String quoted(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private EventSplit capturedSplit() throws Exception {
    ArgumentCaptor<EventSplit> split = ArgumentCaptor.forClass(EventSplit.class);
    verify(agendaEventService).splitEvent(eq(EVENT_ID), split.capture(), any(), anyBoolean(), anyBoolean(), anyLong());
    return split.getValue();
  }

}
