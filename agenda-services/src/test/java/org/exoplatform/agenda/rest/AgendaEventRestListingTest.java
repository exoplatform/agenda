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
package org.exoplatform.agenda.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Pins what the event listing endpoint hands the service: the client's
 * choices reach the {@link EventFilter} as sent, and nothing the client left
 * out is turned on for it.
 * <p>
 * The resource is a JAX-RS resource container, so it is called directly; the
 * service is a mock, and the filter it receives is the thing under test.
 */
class AgendaEventRestListingTest {

  private static final long  USER     = 7L;

  private static final long  SPACE    = 31L;

  private static final String START    = "2026-10-05T00:00:00Z";

  private static final String END      = "2026-10-12T00:00:00Z";

  private AgendaEventService agendaEventService;

  private AgendaEventRest    rest;

  @BeforeEach
  void setUp() throws Exception {
    agendaEventService = Mockito.mock(AgendaEventService.class);
    IdentityManager identityManager = Mockito.mock(IdentityManager.class);
    Identity user = Mockito.mock(Identity.class);
    when(user.getId()).thenReturn(String.valueOf(USER));
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "john")).thenReturn(user);
    when(agendaEventService.getEvents(any(EventFilter.class), any(ZoneId.class), anyLong())).thenReturn(List.of());
    rest = new AgendaEventRest(identityManager,
                               null,
                               Mockito.mock(AgendaCalendarService.class),
                               agendaEventService,
                               Mockito.mock(AgendaEventConferenceService.class),
                               Mockito.mock(AgendaRemoteEventService.class),
                               Mockito.mock(AgendaEventDatePollService.class),
                               Mockito.mock(AgendaEventReminderService.class),
                               Mockito.mock(AgendaEventAttendeeService.class),
                               null);
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("john")));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  /**
   * The opt-in that brings a space's subscribed calendars into a member's
   * own events view (EXO-90373) reaches the filter, and the calendars the
   * client names (EXO-90357) reach it as sent.
   *
   * @throws Exception never
   */
  @Test
  void theSubscribedCalendarsOptInAndTheNamedCalendarsReachTheFilter() throws Exception {
    Response response = rest.getEvents(List.of(SPACE), USER, null, START, END, "UTC", 0, null, null, List.of(12L, 13L), true);

    assertEquals(200, response.getStatus());
    EventFilter filter = filterPassed();
    assertTrue(filter.isSubscribedCalendarsIncluded(), "the client asked for the subscribed calendars");
    assertEquals(List.of(12L, 13L), filter.getCalendarIds());
    assertEquals(List.of(SPACE), filter.getOwnerIds());
    assertEquals(USER, filter.getAttendeeId());
  }

  /**
   * A client that does not ask gets neither: no subscribed calendars, and an
   * empty selection of shared calendars rather than the service's
   * "every shared calendar" default, which only a caller of the service
   * itself may rely on.
   *
   * @throws Exception never
   */
  @Test
  void aClientThatAsksForNeitherGetsNeither() throws Exception {
    Response response = rest.getEvents(List.of(SPACE), USER, null, START, END, "UTC", 0, null, null, null, false);

    assertEquals(200, response.getStatus());
    EventFilter filter = filterPassed();
    assertFalse(filter.isSubscribedCalendarsIncluded());
    assertEquals(List.of(), filter.getCalendarIds());
  }

  private EventFilter filterPassed() throws Exception {
    ArgumentCaptor<EventFilter> filter = ArgumentCaptor.forClass(EventFilter.class);
    verify(agendaEventService).getEvents(filter.capture(), eq(ZoneId.of("UTC")), eq(USER));
    return filter.getValue();
  }
}
