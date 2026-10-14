/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventResponseOutcome;
import org.exoplatform.agenda.model.EventSplit;
import org.exoplatform.agenda.rest.model.CapacityErrorEntity;
import org.exoplatform.agenda.rest.model.EventAttendeeEntity;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.rest.model.EventResponseOutcomeEntity;
import org.exoplatform.agenda.rest.model.EventSplitEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

import org.exoplatform.services.rest.impl.MultivaluedMapImpl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

/**
 * Pins the status contract of {@code PATCH /v1/agenda/events/{id}}, realigned
 * on the platform REST contract by eXIP 7.3.0.20 (EXO-89478): a caller who may
 * not edit the event gets 403 (was 401) and a refused field or value gets 400
 * with the message code in the body (was 500 through the generic catch).
 * <p>
 * The mapping lives in ordered catch blocks, which no service-level test can
 * see: a reordering, or a broader catch inserted above them, restores the old
 * answers with every other test still green.
 * <p>
 * Also pins the pre-check of {@code GET /v1/agenda/events/{id}/response/send}
 * (EXO-89479): it asks the Service for the answer right, so a viewer of an
 * open event gets through, and a refused caller is stopped before the
 * occurrence branch writes anything.
 * <p>
 * Since the Architects Lead's round 3 (2026-09-21) the two response endpoints
 * and the occurrence read answer a refusal with 403 and a fixed body, like the
 * PATCH of the same resource (they answered 401 with the exception message,
 * which carries the caller's identity). Pinned here as well.
 * <p>
 * The move of a split's customised dates
 * ({@code POST /v1/agenda/events/{id}/exceptionalOccurrences/move}, finding 14
 * of the integration review) answers under the same contract: 403, 400 with
 * the code, 404, and the identifiers kept on success.
 */
class AgendaEventRestTest {

  private static final String        USERNAME         = "testuser1";

  private static final long          USER_IDENTITY_ID = 5L;

  private static final long          EVENT_ID         = 42L;

  private AgendaEventService         agendaEventService;

  private AgendaEventAttendeeService attendeeService;

  private IdentityManager            identityManager;

  private AgendaEventRest            eventRest;

  @BeforeEach
  void setUp() {
    identityManager = Mockito.mock(IdentityManager.class);
    agendaEventService = Mockito.mock(AgendaEventService.class);
    attendeeService = Mockito.mock(AgendaEventAttendeeService.class);
    // The resource resolves its caller from the conversation state, as every
    // real request does
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(USERNAME)));
    Identity userIdentity = Mockito.mock(Identity.class);
    when(userIdentity.getId()).thenReturn(String.valueOf(USER_IDENTITY_ID));
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, USERNAME)).thenReturn(userIdentity);
    eventRest = new AgendaEventRest(identityManager,
                                    Mockito.mock(UserPortalConfigService.class),
                                    Mockito.mock(AgendaCalendarService.class),
                                    agendaEventService,
                                    Mockito.mock(AgendaEventConferenceService.class),
                                    Mockito.mock(AgendaRemoteEventService.class),
                                    Mockito.mock(AgendaEventDatePollService.class),
                                    Mockito.mock(AgendaEventReminderService.class),
                                    attendeeService,
                                    Mockito.mock(PortalContainer.class));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void aCallerWithoutTheEditRightGetsForbidden() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), any(), anyLong())).thenReturn(new Event());
    Mockito.doThrow(new IllegalAccessException("not allowed"))
           .when(agendaEventService)
           .updateEventFields(eq(EVENT_ID), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyLong());

    Response response = patch(fields("open", "true"));

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
  }

  @Test
  void aRefusedFieldOrValueGetsBadRequestWithItsMessageCode() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), any(), anyLong())).thenReturn(new Event());
    Mockito.doThrow(new IllegalArgumentException("agenda.openEvent.notAllowed"))
           .when(agendaEventService)
           .updateEventFields(eq(EVENT_ID), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyLong());

    Response response = patch(fields("open", "true"));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("agenda.openEvent.notAllowed", response.getEntity());
  }

  @Test
  void aPatchCarriesTheClosingConfirmationToTheService() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), any(), anyLong())).thenReturn(new Event());

    eventRest.updateEventFields(EVENT_ID, false, false, null, true, fields("open", "false"));

    Mockito.verify(agendaEventService)
           .updateEventFields(eq(EVENT_ID), any(), eq(false), eq(false), eq(true), eq(USER_IDENTITY_ID));
  }

  @Test
  void anUnknownEventGetsNotFound() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), any(), anyLong())).thenReturn(null);

    Response response = patch(fields("open", "true"));

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  void anEmptyFieldSetGetsBadRequest() {
    Response response = patch(new MultivaluedMapImpl());

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void aViewerOfAnOpenEventAnswersThroughTheLink() throws Exception {
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(true);
    // isEventAttendee stays unstubbed, false: the caller is a self-registrant
    when(attendeeService.sendEventResponse(EVENT_ID, null, USER_IDENTITY_ID, EventAttendeeResponse.ACCEPTED, true))
      .thenReturn(new EventResponseOutcome(EventAttendeeResponse.ACCEPTED, null));

    Response response = eventRest.sendEventResponse(null, EVENT_ID, null, "ACCEPTED", false, null, false);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON, String.valueOf(response.getMetadata().getFirst("Content-Type")));
    assertEquals(new EventResponseOutcomeEntity(EventAttendeeResponse.ACCEPTED, null), response.getEntity());
  }

  /**
   * An answer from a date on returns the answer stored at that date, which is
   * not always the one sent (a place on the waiting list asked for is a seat
   * where one is free), and the first date it left waiting, in RFC 3339.
   */
  @Test
  void anAnswerFromADateReturnsTheAnswerStoredAndTheFirstDateItLeftWaiting() throws Exception {
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(true);
    ZonedDateTime date = ZonedDateTime.of(2026, 9, 18, 8, 0, 0, 0, ZoneOffset.UTC);
    when(attendeeService.sendUpcomingEventResponse(EVENT_ID, date, USER_IDENTITY_ID, EventAttendeeResponse.WAITING, true))
      .thenReturn(new EventResponseOutcome(EventAttendeeResponse.ACCEPTED, date.plusDays(2)));

    Response response = eventRest.sendEventResponse(null, EVENT_ID, "2026-09-18T08:00:00.000Z", "WAITING", true, null, false);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(new EventResponseOutcomeEntity(EventAttendeeResponse.ACCEPTED, "2026-09-20T08:00:00.000Z"),
                 response.getEntity());
  }

  /**
   * An external attendee following a Yes link on a full date is told the
   * answer stored, a place on the waiting list, not the Yes they clicked.
   */
  @Test
  void aGuestWhoseYesWaitsIsToldSo() throws Exception {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(IdentityConstants.ANONIM)));
    Identity guest = Mockito.mock(Identity.class);
    when(guest.getId()).thenReturn(String.valueOf(USER_IDENTITY_ID));
    when(attendeeService.decryptUserIdentity(EVENT_ID, "token", EventAttendeeResponse.ACCEPTED)).thenReturn(guest);
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(true);
    when(attendeeService.sendEventResponse(EVENT_ID, null, USER_IDENTITY_ID, EventAttendeeResponse.ACCEPTED, true))
      .thenReturn(new EventResponseOutcome(EventAttendeeResponse.WAITING, null));

    ResourceBundle bundle = new ListResourceBundle() {
      @Override
      protected Object[][] getContents() {
        return new Object[][] { { "agenda.guestResponse.recorded", "Your answer has been recorded: {0}" },
            { "agenda.accepted", "Accepted" }, { "agenda.waiting", "Waiting list" } };
      }
    };
    ResourceBundleService bundles = Mockito.mock(ResourceBundleService.class);
    when(bundles.getSharedResourceBundleNames()).thenReturn(new String[0]);
    when(bundles.getResourceBundle(any(String[].class), any())).thenReturn(bundle);
    Response response;
    try (MockedStatic<ExoContainerContext> container = Mockito.mockStatic(ExoContainerContext.class)) {
      container.when(() -> ExoContainerContext.getService(ResourceBundleService.class)).thenReturn(bundles);
      response = eventRest.sendEventResponse(null, EVENT_ID, null, "ACCEPTED", false, "token", true);
    }

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(MediaType.TEXT_HTML, String.valueOf(response.getMetadata().getFirst("Content-Type")));
    String page = String.valueOf(response.getEntity());
    assertTrue(page.contains("Your answer has been recorded: Waiting list"), page);
  }

  @Test
  void aRefusedCallerLeavesNoExceptionalOccurrenceBehind() throws Exception {
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(false);

    Response response = eventRest.sendEventResponse(null, EVENT_ID, "2026-09-18T08:00:00.000Z", "ACCEPTED", false, null, false);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    assertEquals("Not allowed to answer this event", response.getEntity());
    Mockito.verify(agendaEventService, Mockito.never()).saveEventExceptionalOccurrence(anyLong(), any());
    Mockito.verify(attendeeService, Mockito.never()).sendEventResponse(anyLong(), anyLong(), any());
    Mockito.verify(attendeeService, Mockito.never()).sendEventResponse(anyLong(), any(), anyLong(), any(), anyBoolean());
  }

  /**
   * An answer to one date of a series goes to the Service with the date, a
   * place on the waiting list included, which asks for a seat as a Yes does:
   * the Service finds or creates the date's row under the lock of the
   * series, and the endpoint writes no row itself.
   */
  @Test
  void anAnswerToOneDateIsSentWithItsDateAndWritesNoRowHere() throws Exception {
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(true);
    when(attendeeService.sendEventResponse(EVENT_ID,
                                           ZonedDateTime.of(2026, 9, 18, 8, 0, 0, 0, ZoneOffset.UTC),
                                           USER_IDENTITY_ID,
                                           EventAttendeeResponse.WAITING,
                                           true))
      .thenReturn(new EventResponseOutcome(EventAttendeeResponse.WAITING, null));

    Response response = eventRest.sendEventResponse(null, EVENT_ID, "2026-09-18T08:00:00.000Z", "WAITING", false, null, false);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(new EventResponseOutcomeEntity(EventAttendeeResponse.WAITING, null), response.getEntity());
    Mockito.verify(agendaEventService, Mockito.never()).saveEventExceptionalOccurrence(anyLong(), any());
  }

  @Test
  void aCallerWhoMayNotReadTheirAnswerGetsForbiddenWithAFixedBody() throws Exception {
    when(attendeeService.getEventResponse(EVENT_ID, null, USER_IDENTITY_ID))
      .thenThrow(new IllegalAccessException("User " + USER_IDENTITY_ID + " may not answer event " + EVENT_ID));

    Response response = eventRest.getEventResponse(EVENT_ID, null, null);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    // The exception message names the caller; the body must not
    assertEquals("Not allowed to answer this event", response.getEntity());
  }

  @Test
  void aCallerWhoCannotAccessTheParentOfAnOccurrenceGetsForbidden() throws Exception {
    when(agendaEventService.getEventOccurrence(eq(EVENT_ID), any(), any(), eq(USER_IDENTITY_ID)))
      .thenThrow(new IllegalAccessException("User with identity id " + USER_IDENTITY_ID + " is not allowed to access event with id " + EVENT_ID));

    Response response = eventRest.getEventOccurrence(EVENT_ID, "2026-09-18T08:00:00.000Z", null, null);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    assertEquals("Not allowed to access the occurrence served", response.getEntity());
  }

  @Test
  void aSplitByACallerWithoutTheEditRightGetsForbidden() throws Exception {
    Mockito.doThrow(new IllegalAccessException("not allowed"))
           .when(agendaEventService)
           .moveExceptionalOccurrences(eq(EVENT_ID), eq(43L), any(), anyLong());
    Response response = eventRest.moveEventExceptionalOccurrences(EVENT_ID, 43L, "2026-10-05T09:00:00.000Z");
    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
  }

  @Test
  void aSplitOfTwoEventsItCannotJoinGetsBadRequestWithItsMessageCode() throws Exception {
    Mockito.doThrow(new IllegalArgumentException("agenda.seriesSplit.notAllowed"))
           .when(agendaEventService)
           .moveExceptionalOccurrences(eq(EVENT_ID), eq(43L), any(), anyLong());
    Response response = eventRest.moveEventExceptionalOccurrences(EVENT_ID, 43L, "2026-10-05T09:00:00.000Z");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("agenda.seriesSplit.notAllowed", response.getEntity());
  }

  @Test
  void aSplitOfAnUnknownEventGetsNotFound() throws Exception {
    Mockito.doThrow(new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND))
           .when(agendaEventService)
           .moveExceptionalOccurrences(eq(EVENT_ID), eq(43L), any(), anyLong());
    Response response = eventRest.moveEventExceptionalOccurrences(EVENT_ID, 43L, "2026-10-05T09:00:00.000Z");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  void aSplitAnswersTheIdentifiersKept() throws Exception {
    java.util.List<Long> kept = java.util.Arrays.asList(7L, 8L);
    when(agendaEventService.moveExceptionalOccurrences(eq(EVENT_ID), eq(43L), any(), anyLong())).thenReturn(kept);
    Response response = eventRest.moveEventExceptionalOccurrences(EVENT_ID, 43L, "2026-10-05T09:00:00.000Z");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(kept, response.getEntity());
  }

  @Test
  void aSplitRemovingAnEmailNobodyInvitedCreatesNoIdentity() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of(attendee(null, "GUEST_USER", "nobody@example.com")));

    Response response = eventRest.splitEvent(EVENT_ID, null, false, body);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    assertTrue(capturedSplit().removedIdentityIds().isEmpty());
    Mockito.verify(identityManager, Mockito.never()).getOrCreateIdentity(any(), eq("nobody@example.com"));
  }

  @Test
  void aSplitResolvesARemovalAmongTheSeriesAttendeesByRemoteId() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(),
                                      List.of(attendee(null, OrganizationIdentityProvider.NAME, "testuser2"),
                                              attendee(null, OrganizationIdentityProvider.NAME, "testuser3")));

    eventRest.splitEvent(EVENT_ID, null, false, body);

    assertEquals(java.util.Set.of(7L), capturedSplit().removedIdentityIds());
  }

  @Test
  void aSplitResolvesARemovalAmongTheSeriesAttendeesByIdentityId() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of(attendee("7", null, null), attendee("8", null, null)));

    eventRest.splitEvent(EVENT_ID, null, false, body);

    assertEquals(java.util.Set.of(7L), capturedSplit().removedIdentityIds());
  }

  @Test
  void aSplitResolvesAnAddedEmailGuestAsANewEventDoes() throws Exception {
    seriesAttendees(7L, "testuser2");
    Identity guest = Mockito.mock(Identity.class);
    when(guest.getId()).thenReturn("9");
    when(identityManager.getOrCreateIdentity("GUEST_USER", "guest@example.com")).thenReturn(guest);
    EventSplitEntity body = splitBody(List.of(attendee(null, "GUEST_USER", "guest@example.com")), List.of());

    eventRest.splitEvent(EVENT_ID, null, true, body);

    assertEquals(List.of(9L), capturedSplit().addedIdentityIds());
    Mockito.verify(agendaEventService)
           .splitEvent(eq(EVENT_ID), any(), eq(ZonedDateTime.parse("2026-10-05T09:00:00Z")), eq(true), eq(true), eq(USER_IDENTITY_ID));
  }

  @Test
  void aRefusedSplitGetsBadRequestWithItsMessageCodeAndDate() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of());
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
      .thenThrow(new IllegalArgumentException("agenda.capacity.closeNeedsConfirmation"));
    Response refusal = eventRest.splitEvent(EVENT_ID, null, false, body);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), refusal.getStatus());
    assertEquals("agenda.capacity.closeNeedsConfirmation", refusal.getEntity());

    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
      .thenThrow(new CapacityBelowAcceptedException(java.time.LocalDate.of(2026, 10, 7)));
    Response belowAccepted = eventRest.splitEvent(EVENT_ID, null, false, body);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), belowAccepted.getStatus());
    assertEquals(new CapacityErrorEntity("agenda.capacity.belowAccepted", "2026-10-07"), belowAccepted.getEntity());
  }

  @Test
  void aSplitOfAnUnknownSeriesGetsNotFound() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of());
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
      .thenThrow(new AgendaException(AgendaExceptionType.EVENT_NOT_FOUND));

    Response response = eventRest.splitEvent(EVENT_ID, null, false, body);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  void aSplitPassesItsZoneAndTheZoneTheSeriesWasReadIn() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of());

    eventRest.splitEvent(EVENT_ID, "Etc/UTC", false, body);

    EventSplit split = capturedSplit();
    assertEquals(java.time.ZoneId.of("UTC"), split.event().getTimeZoneId());
    assertEquals(java.time.ZoneId.of("Etc/UTC"), split.readZone());
  }

  @Test
  void aSplitGivingNoZoneReadsItsDatesInTheZoneTheSeriesWasReadInAndKeepsTheSeriesZone() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of());
    body.getEvent().setTimeZoneId(null);
    body.getEvent().setStart("2026-10-05T10:00:00");
    body.getEvent().setEnd("2026-10-05T11:00:00");

    Response response = eventRest.splitEvent(EVENT_ID, "Europe/Paris", false, body);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    EventSplit split = capturedSplit();
    assertEquals(null, split.event().getTimeZoneId());
    assertEquals(ZonedDateTime.parse("2026-10-05T08:00:00Z").toInstant(), split.event().getStart().toInstant());
  }

  @Test
  void aSplitGivingNoZoneNorTheZoneTheSeriesWasReadInKeepsTheSeriesZone() throws Exception {
    seriesAttendees(7L, "testuser2");
    EventSplitEntity body = splitBody(List.of(), List.of());
    body.getEvent().setTimeZoneId(null);

    eventRest.splitEvent(EVENT_ID, null, false, body);

    assertEquals(null, capturedSplit().event().getTimeZoneId());
  }

  /** The series holds one attendee, the user given. */
  private void seriesAttendees(long identityId, String username) {
    Identity identity = Mockito.mock(Identity.class);
    when(identity.getId()).thenReturn(String.valueOf(identityId));
    when(identity.getProviderId()).thenReturn(OrganizationIdentityProvider.NAME);
    when(identity.getRemoteId()).thenReturn(username);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(attendeeService.getEventAttendees(EVENT_ID))
      .thenReturn(new EventAttendeeList(List.of(new EventAttendee(1L, EVENT_ID, identityId, EventAttendeeResponse.ACCEPTED))));
  }

  /**
   * A body that splits the series from the 5th of October 2026, refused by
   * default as a caller who may not split it, so that the split asked is
   * captured without the continuation being read back.
   */
  private EventSplitEntity splitBody(List<EventAttendeeEntity> added, List<EventAttendeeEntity> removed) throws Exception {
    when(agendaEventService.splitEvent(eq(EVENT_ID), any(), any(), anyBoolean(), anyBoolean(), anyLong()))
      .thenThrow(new IllegalAccessException("not allowed"));
    EventEntity event = new EventEntity();
    event.setSummary("summary");
    event.setTimeZoneId("UTC");
    return new EventSplitEntity(event, added, removed, "2026-10-05T09:00:00.000Z", true);
  }

  private EventSplit capturedSplit() throws Exception {
    ArgumentCaptor<EventSplit> split = ArgumentCaptor.forClass(EventSplit.class);
    Mockito.verify(agendaEventService).splitEvent(eq(EVENT_ID), split.capture(), any(), anyBoolean(), anyBoolean(), anyLong());
    return split.getValue();
  }

  private static EventAttendeeEntity attendee(String id, String providerId, String remoteId) {
    IdentityEntity identity = new IdentityEntity();
    identity.setId(id);
    identity.setProviderId(providerId);
    identity.setRemoteId(remoteId);
    return new EventAttendeeEntity(0, identity, null);
  }

  private Response patch(MultivaluedMap<String, String> fields) {
    return eventRest.updateEventFields(EVENT_ID, false, false, null, false, fields);
  }

  private MultivaluedMap<String, String> fields(String name, String value) {
    MultivaluedMap<String, String> fields = new MultivaluedMapImpl();
    fields.put(name, Collections.unmodifiableList(List.of(value)));
    return fields;
  }
}
