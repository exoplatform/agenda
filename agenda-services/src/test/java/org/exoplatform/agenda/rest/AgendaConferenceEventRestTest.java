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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.rest.model.ConferenceEventEntity;
import org.exoplatform.agenda.service.AgendaConferenceEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pins what the call-to-event endpoint answers, and on whose behalf: the
 * reader is whoever the container authenticated, an occurrence opens through
 * its series and its date, and nothing found is a 204, not an error.
 */
class AgendaConferenceEventRestTest {

  private static final long             READER  = 7L;

  private static final String           MOMENT  = "2026-10-20T10:45:00Z";

  private static final ZonedDateTime    START   = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, ZoneOffset.UTC);

  private AgendaConferenceEventService  conferenceEventService;

  private HttpServletRequest            request;

  private AgendaConferenceEventRest     rest;

  private MockedStatic<NotificationUtils> notificationUtils;

  @BeforeEach
  void setUp() {
    conferenceEventService = mock(AgendaConferenceEventService.class);
    IdentityManager identityManager = mock(IdentityManager.class);
    request = mock(HttpServletRequest.class);
    rest = new AgendaConferenceEventRest(conferenceEventService, identityManager);
    Identity reader = mock(Identity.class);
    when(reader.getId()).thenReturn(String.valueOf(READER));
    when(identityManager.getOrCreateUserIdentity("reader")).thenReturn(reader);
    when(request.getRemoteUser()).thenReturn("reader");
    notificationUtils = mockStatic(NotificationUtils.class, CALLS_REAL_METHODS);
    notificationUtils.when(NotificationUtils::getDefaultSite).thenReturn("dw");
  }

  @AfterEach
  void tearDown() {
    notificationUtils.close();
  }

  /**
   * A single event comes back with its title, its dates and the address
   * opening it by its identifier, read for the authenticated user.
   */
  @Test
  void aSingleEventOpensByItsIdentifier() {
    Event event = event(10L, 0L);
    when(conferenceEventService.getConferenceEvent("jitsi", "g_space-abc", ZonedDateTime.parse(MOMENT), 12, READER)).thenReturn(event);

    ResponseEntity<ConferenceEventEntity> response = rest.getConferenceEvent(request, "jitsi", "g_space-abc", MOMENT, 12);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    ConferenceEventEntity entity = response.getBody();
    assertEquals(10L, entity.getId());
    assertEquals("Weekly sync", entity.getTitle());
    assertEquals("2026-10-20T10:00:00.000Z", entity.getStart());
    assertEquals("2026-10-20T11:00:00.000Z", entity.getEnd());
    assertNull(entity.getOccurrenceId());
    assertEquals("/portal/dw/agenda?eventId=10", entity.getUrl());
    assertEquals("no-store", response.getHeaders().getCacheControl(), "the answer depends on the reader");
  }

  /**
   * A computed occurrence opens through its series and its date in UTC.
   */
  @Test
  void anOccurrenceOpensThroughItsSeriesAndDate() {
    Event occurrence = event(0L, 20L);
    occurrence.setOccurrence(new EventOccurrence(START, false, false));
    when(conferenceEventService.getConferenceEvent(anyString(), anyString(), any(), anyInt(), anyLong())).thenReturn(occurrence);

    ConferenceEventEntity entity = rest.getConferenceEvent(request, "jitsi", "g_space-abc", MOMENT, 12).getBody();

    assertEquals(20L, entity.getParentId());
    assertEquals("2026-10-20T10:00:00.000Z", entity.getOccurrenceId());
    assertEquals("/portal/dw/agenda?parentId=20&occurrenceId=2026-10-20T10:00:00.000Z", entity.getUrl());
  }

  /**
   * No event the reader can read is a 204 with no body.
   */
  @Test
  void noEventIsNoContent() {
    ResponseEntity<ConferenceEventEntity> response = rest.getConferenceEvent(request, "jitsi", "g_space-abc", MOMENT, 12);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertNull(response.getBody());
    assertEquals("no-store", response.getHeaders().getCacheControl(), "nothing for one reader is not nothing for another");
  }

  /**
   * An unreadable moment, and an input the service refuses, are 400s with
   * their code.
   */
  @Test
  void badInputsAreBadRequests() {
    ResponseStatusException unreadable = assertThrows(ResponseStatusException.class,
                                                      () -> rest.getConferenceEvent(request, "jitsi", "g_space-abc", "yesterday", 12));
    assertEquals(HttpStatus.BAD_REQUEST, unreadable.getStatusCode());
    assertEquals(AgendaConferenceEventRest.INVALID_MOMENT, unreadable.getReason());

    when(conferenceEventService.getConferenceEvent(eq(""), anyString(), any(), anyInt(), anyLong())).thenThrow(new IllegalArgumentException("code"));
    ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                                                   () -> rest.getConferenceEvent(request, "", "g_space-abc", MOMENT, 12));
    assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
    assertEquals("code", refused.getReason());
  }

  /**
   * The reader is the authenticated user, and the endpoint declares no
   * parameter through which a request could name another one; the caller's
   * grace reaches the lookup.
   */
  @Test
  void theReaderIsTheAuthenticatedUser() throws Exception {
    rest.getConferenceEvent(request, "jitsi", "g_space-abc", MOMENT, 3);
    verify(conferenceEventService).getConferenceEvent("jitsi", "g_space-abc", ZonedDateTime.parse(MOMENT), 3, READER);

    Method method = AgendaConferenceEventRest.class.getMethod("getConferenceEvent",
                                                              HttpServletRequest.class,
                                                              String.class,
                                                              String.class,
                                                              String.class,
                                                              int.class);
    assertFalse(Arrays.stream(method.getParameters())
                      .anyMatch(parameter -> parameter.getName().toLowerCase().contains("identity")
                          || parameter.getName().toLowerCase().contains("user")));
  }

  /**
   * The grace defaults to web conferencing's own default, twelve hours.
   */
  @Test
  void theGraceDefaultsToTwelveHours() {
    assertEquals("12", AgendaConferenceEventRest.DEFAULT_GRACE_HOURS);
  }

  /**
   * Builds a one-hour event.
   *
   * @param id the event identifier
   * @param parentId the series, or 0
   * @return the event
   */
  private static Event event(long id, long parentId) {
    Event event = new Event();
    event.setId(id);
    event.setParentId(parentId);
    event.setSummary("Weekly sync");
    event.setStart(START);
    event.setEnd(START.plusHours(1));
    return event;
  }

}
