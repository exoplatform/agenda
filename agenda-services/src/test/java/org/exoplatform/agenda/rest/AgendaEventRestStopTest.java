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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * The stop of the event REST service runs the deletions still waiting for their
 * delay on the thread that stops, lets a deletion running at that time end, and
 * runs no deletion its user undid.
 */
class AgendaEventRestStopTest {

  private static final String USERNAME         = "testuser1";

  private static final long   USER_IDENTITY_ID = 5L;

  private static final long   EVENT_ID         = 42L;

  private AgendaEventService  agendaEventService;

  private AgendaEventRest     eventRest;

  @BeforeEach
  void setUp() {
    IdentityManager identityManager = Mockito.mock(IdentityManager.class);
    agendaEventService = Mockito.mock(AgendaEventService.class);
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
                                    Mockito.mock(AgendaEventAttendeeService.class),
                                    Mockito.mock(PortalContainer.class));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void theStopGivesItsThreadBackItsContainerOnceTheWaitingDeletionsRan() throws Exception {
    eventRest.start();
    assertEquals(Response.Status.OK.getStatusCode(), deleteWithDelay(3600).getStatus());
    var callerContainer = ExoContainerContext.getCurrentContainerIfPresent();

    eventRest.stop();

    Mockito.verify(agendaEventService).deleteEventById(EVENT_ID, USER_IDENTITY_ID);
    // The thread that stops goes on with its own container, not the one the
    // deletion ran in
    assertSame(callerContainer, ExoContainerContext.getCurrentContainerIfPresent());
  }

  @Test
  void anUndoneDeletionIsNotRunWhenTheNodeStops() throws Exception {
    eventRest.start();
    deleteWithDelay(3600);

    Response response = eventRest.undoDeleteEvent(EVENT_ID);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    eventRest.stop();
    Mockito.verify(agendaEventService, Mockito.never()).deleteEventById(anyLong(), anyLong());
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), eventRest.undoDeleteEvent(EVENT_ID).getStatus());
  }

  @Test
  void aDeletionRunningWhenTheNodeStopsEnds() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    AtomicBoolean ended = new AtomicBoolean();
    Mockito.doAnswer(invocation -> {
      started.countDown();
      Thread.sleep(500);
      ended.set(true);
      return null;
    }).when(agendaEventService).deleteEventById(EVENT_ID, USER_IDENTITY_ID);
    eventRest.start();
    deleteWithDelay(1);
    assertTrue(started.await(10, TimeUnit.SECONDS));

    eventRest.stop();

    assertTrue(ended.get());
  }

  private Response deleteWithDelay(long delay) {
    try (MockedStatic<RestUtils> restUtils = Mockito.mockStatic(RestUtils.class, Mockito.CALLS_REAL_METHODS)) {
      restUtils.when(() -> RestUtils.getEventByIdAndUser(any(),
                                                         any(),
                                                         any(),
                                                         any(),
                                                         any(),
                                                         any(),
                                                         any(),
                                                         any(),
                                                         anyLong(),
                                                         anyBoolean(),
                                                         anyLong(),
                                                         any(),
                                                         any(),
                                                         any()))
               .thenReturn(new EventEntity());
      return eventRest.deleteEvent(EVENT_ID, delay, null);
    }
  }
}
