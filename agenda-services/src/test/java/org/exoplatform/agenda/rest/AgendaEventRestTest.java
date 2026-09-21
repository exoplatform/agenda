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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import org.exoplatform.services.rest.impl.MultivaluedMapImpl;
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
           .updateEventFields(eq(EVENT_ID), any(), anyBoolean(), anyBoolean(), anyLong());

    Response response = patch(fields("open", "true"));

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
  }

  @Test
  void aRefusedFieldOrValueGetsBadRequestWithItsMessageCode() throws Exception {
    when(agendaEventService.getEventById(eq(EVENT_ID), any(), anyLong())).thenReturn(new Event());
    Mockito.doThrow(new IllegalArgumentException("agenda.openEvent.notAllowed"))
           .when(agendaEventService)
           .updateEventFields(eq(EVENT_ID), any(), anyBoolean(), anyBoolean(), anyLong());

    Response response = patch(fields("open", "true"));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("agenda.openEvent.notAllowed", response.getEntity());
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

    Response response = eventRest.sendEventResponse(null, EVENT_ID, null, "ACCEPTED", false, null, false);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    Mockito.verify(attendeeService).sendEventResponse(EVENT_ID, USER_IDENTITY_ID, EventAttendeeResponse.ACCEPTED);
  }

  @Test
  void aRefusedCallerLeavesNoExceptionalOccurrenceBehind() throws Exception {
    Event event = new Event();
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    when(attendeeService.canRespondToEvent(event, USER_IDENTITY_ID)).thenReturn(false);

    Response response = eventRest.sendEventResponse(null, EVENT_ID, "2026-09-18T08:00:00.000Z", "ACCEPTED", false, null, false);

    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    Mockito.verify(agendaEventService, Mockito.never()).saveEventExceptionalOccurrence(anyLong(), any());
    Mockito.verify(attendeeService, Mockito.never()).sendEventResponse(anyLong(), anyLong(), any());
  }

  private Response patch(MultivaluedMap<String, String> fields) {
    return eventRest.updateEventFields(EVENT_ID, false, false, null, fields);
  }

  private MultivaluedMap<String, String> fields(String name, String value) {
    MultivaluedMap<String, String> fields = new MultivaluedMapImpl();
    fields.put(name, Collections.unmodifiableList(List.of(value)));
    return fields;
  }
}
