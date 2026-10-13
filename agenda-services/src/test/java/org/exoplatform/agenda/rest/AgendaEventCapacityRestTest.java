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

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.impl.ApplicationContextImpl;
import org.exoplatform.services.rest.impl.MultivaluedMapImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the answers of the REST writes to a capacity limit the event refuses,
 * on the container: each refusal is a 400 whose body is the code the
 * frontend acts on, and a closing confirmed by the user goes through.
 */
public class AgendaEventCapacityRestTest extends BaseAgendaEventTest {

  /**
   * The entity build reads the current user through the social
   * {@code EntityBuilder}, which requires an authenticated conversation state
   * and a bound JAX-RS application context, as every real REST call has.
   */
  @Before
  public void setUpRestContext() {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
    ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
    Mockito.when(applicationContext.getProperties()).thenReturn(new HashMap<>());
    Mockito.when(applicationContext.getBaseUri()).thenReturn(URI.create("http://localhost:8080/portal/rest"));
    ApplicationContextImpl.setCurrent(applicationContext);
  }

  /**
   * Clears the contexts installed for the test.
   */
  @After
  public void tearDownRestContext() {
    ApplicationContextImpl.setCurrent(null);
    ConversationState.setCurrent(null);
  }

  /**
   * A negative limit is refused at creation with a 400 carrying its code.
   *
   * @throws Exception when the model event can't be created
   */
  @Test
  public void aNegativeLimitIsABadRequestAtCreation() throws Exception {
    EventEntity toCreate = entityOf(openEvent(2).getId());
    toCreate.setId(0);
    toCreate.setCapacity(-1);

    Response response = eventRest().createEvent("UTC", toCreate);

    assertBadRequest("agenda.capacity.invalid", response);
  }

  /**
   * A limit below the Yes answers is refused by a full save with a 400
   * carrying its code.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aLimitBelowTheYesAnswersIsABadRequestOnAFullSave() throws Exception {
    long eventId = openEvent(2, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity);
    answer(eventId, testuser3Identity);
    EventEntity toSave = entityOf(eventId);
    toSave.setCapacity(1);

    Response response = eventRest().updateEvent(toSave, "UTC", false);

    assertBadRequest("agenda.capacity.belowAccepted", response);
    assertEquals(Integer.valueOf(2), agendaEventService.getEventById(eventId).getCapacity());
  }

  /**
   * A full save closing an event with a waiting list is a 400 carrying the
   * code that asks for the user's confirmation; confirmed, it is saved.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aClosingIsABadRequestUntilTheUserConfirmsIt() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity);
    answer(eventId, testuser3Identity);

    EventEntity toSave = entityOf(eventId);
    toSave.setOpen(false);
    assertBadRequest("agenda.capacity.closeNeedsConfirmation", eventRest().updateEvent(toSave, "UTC", false));

    EventEntity confirmed = entityOf(eventId);
    confirmed.setOpen(false);
    Response response = eventRest().updateEvent(confirmed, "UTC", true);

    assertEquals(200, response.getStatus());
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendee(id(testuser3Identity), null).getResponse());
  }

  /**
   * A limit patched on an event that may hold none is a 400 carrying its code.
   *
   * @throws Exception when the event can't be created
   */
  @Test
  public void aLimitPatchedOnALockedEventIsABadRequest() throws Exception {
    Event locked = spaceEvent(0);
    locked.setOpen(false);
    long eventId = createEvent(locked, id(testuser1Identity)).getId();
    MultivaluedMap<String, String> fields = new MultivaluedMapImpl();
    fields.putSingle("capacity", "2");

    Response response = eventRest().updateEventFields(eventId, false, false, "UTC", false, fields);

    assertBadRequest("agenda.capacity.notAllowed", response);
  }

  private static void assertBadRequest(String code, Response response) {
    assertEquals("unexpected status, body: " + response.getEntity(), 400, response.getStatus());
    assertEquals(code, response.getEntity());
  }

  private AgendaEventRest eventRest() {
    return new AgendaEventRest(identityManager,
                               Mockito.mock(UserPortalConfigService.class),
                               agendaCalendarService,
                               agendaEventService,
                               agendaEventConferenceService,
                               agendaRemoteEventService,
                               agendaEventDatePollService,
                               agendaEventReminderService,
                               agendaEventAttendeeService,
                               Mockito.mock(PortalContainer.class));
  }

  /**
   * An event of no series in the space's calendar, starting in sixty days,
   * open, with the limit given: not created yet.
   */
  private Event spaceEvent(int capacity) {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    event.setOpen(true);
    event.setCapacity(capacity);
    return event;
  }

  private Event openEvent(int capacity, Identity... attendees) throws Exception {
    return createEvent(spaceEvent(capacity), id(testuser1Identity), attendees);
  }

  private void answer(long eventId, Identity identity) throws Exception {
    agendaEventAttendeeService.sendEventResponse(eventId, id(identity), EventAttendeeResponse.ACCEPTED);
  }

  /**
   * The entity of an event as the form reads it before saving it.
   */
  private EventEntity entityOf(long eventId) {
    return RestUtils.getEventEntity(identityManager,
                                    agendaCalendarService,
                                    agendaEventService,
                                    agendaRemoteEventService,
                                    agendaEventDatePollService,
                                    agendaEventReminderService,
                                    agendaEventConferenceService,
                                    agendaEventAttendeeService,
                                    agendaEventService.getEventById(eventId),
                                    null,
                                    ZoneOffset.UTC,
                                    List.of("all"));
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }
}
