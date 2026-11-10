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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.impl.ApplicationContextImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;

/**
 * Splits a series through {@code POST /agenda/rest/events/{id}/split} on the
 * real services, with the body the event page sends: the series as the page
 * read it from the JAX-RS event resource, with the page's own fields. The
 * split runs with no JAX-RS context bound, as on a Spring MVC request.
 */
public class AgendaEventSplitRestPageTest extends BaseAgendaEventTest {

  private final JsonMapper platformMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

  private MockMvc          mockMvc;

  /**
   * Builds the resource on the real services, and the session of its user.
   */
  @Before
  public void setUpResource() {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
    AgendaEventSplitRest resource = new AgendaEventSplitRest(identityManager, agendaEventService, agendaEventAttendeeService);
    mockMvc = MockMvcBuilders.standaloneSetup(resource)
                             .setMessageConverters(new JacksonJsonHttpMessageConverter(platformMapper))
                             .build();
  }

  /**
   * Clears the contexts installed for the test.
   */
  @After
  public void tearDownResource() {
    ApplicationContextImpl.setCurrent(null);
    ConversationState.setCurrent(null);
  }

  /**
   * The page sends the series it read, with its participants, its creator and
   * its identifier taken out, the fields of its own left in and a primitive it
   * cleared, and a user it adds: the split answers the continuation it
   * created, which carries the edit, the series' participant with their
   * answer and the user added, while the series keeps its dates before the
   * split date only.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void theSeriesThePageReadSplitsWithNoJaxRsContext() throws Exception {
    ZonedDateTime start = LocalDate.now(ZoneOffset.UTC).plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event seriesEvent = newEventInstance(start, start.plusHours(1), false);
    seriesEvent.setTimeZoneId(ZoneOffset.UTC);
    seriesEvent.getRecurrence().setUntil(null);
    seriesEvent.getRecurrence().setCount(4);
    seriesEvent.setCalendarId(spaceCalendar.getId());
    seriesEvent.setOpen(false);
    Event series = createEvent(seriesEvent, id(testuser1Identity), testuser2Identity);
    agendaEventAttendeeService.sendEventResponse(series.getId(), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
    ZonedDateTime splitFrom = start.plusDays(2);

    ObjectNode pageEvent = (ObjectNode) platformMapper.readTree(pageJson(series.getId()));
    assertNotNull("the page's series has no modifier to send back", pageEvent.get("modifier").get("profile"));
    // EventService#formatEventToSave: the calendar's owner reduced to its
    // keys, no creator, no participants; the dialog takes the id out
    ObjectNode owner = (ObjectNode) pageEvent.get("calendar").get("owner");
    owner.retain("id", "providerId", "remoteId");
    pageEvent.remove(List.of("id", "attendees", "creator"));
    pageEvent.put("summary", "Edited from the third date");
    pageEvent.put("start", AgendaDateUtils.toRFC3339Date(splitFrom));
    pageEvent.put("end", AgendaDateUtils.toRFC3339Date(splitFrom.plusHours(1)));
    pageEvent.put("timed", true);
    pageEvent.put("name", "the calendar view's own field");
    pageEvent.putNull("allowAttendeeToInvite");
    String body = "{\"event\": " + pageEvent + ", \"addedAttendees\": [{\"identity\": {\"id\": \"" + testuser3Identity.getId()
        + "\", \"providerId\": \"organization\", \"remoteId\": \"" + testuser3Identity.getRemoteId()
        + "\"}}], \"removedAttendees\": [], \"splitFrom\": \"" + AgendaDateUtils.toRFC3339Date(splitFrom)
        + "\", \"sendInvitation\": false}";

    MockHttpServletResponse response = mockMvc.perform(post("/events/" + series.getId() + "/split").with(request -> {
      request.setRemoteUser(testuser1Identity.getRemoteId());
      return request;
    }).param("timeZoneId", "UTC").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    String answer = response.getContentAsString();
    assertEquals(answer, 200, response.getStatus());

    long continuationId = platformMapper.readTree(answer).get("id").asLong();
    assertNotEquals(series.getId(), continuationId);
    Event continuation = agendaEventService.getEventById(continuationId);
    assertEquals("Edited from the third date", continuation.getSummary());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventAttendees(continuationId)
                                           .getEventAttendee(id(testuser2Identity), null)
                                           .getResponse());
    assertNotNull("the user added is not on the continuation",
                  agendaEventAttendeeService.getEventAttendees(continuationId).getEventAttendee(id(testuser3Identity), null));
    Event ended = agendaEventService.getEventById(series.getId());
    assertEquals("the series keeps its dates before the split date only",
                 2,
                 agendaEventService.getEventOccurrencesInPeriod(ended, start.minusDays(1), start.plusDays(10), ZoneOffset.UTC, 0)
                                   .size());
  }

  /**
   * @return the series as the event page holds it: read from the JAX-RS
   *         event resource, all expanded, written by its JSON writer, within
   *         the JAX-RS context that read has
   */
  private String pageJson(long seriesId) throws Exception {
    ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
    Mockito.when(applicationContext.getProperties()).thenReturn(new HashMap<>());
    Mockito.when(applicationContext.getBaseUri()).thenReturn(URI.create("http://localhost:8080/portal/rest"));
    ApplicationContextImpl.setCurrent(applicationContext);
    try {
      Event stored = agendaEventService.getEventById(seriesId, ZoneOffset.UTC, id(testuser1Identity));
      // Changed since its creation by someone else than its creator: the
      // page then holds a modifier, with the whole profile JAX-RS wrote
      stored.setModifierId(id(testuser2Identity));
      EventEntity series = RestUtils.getEventEntity(identityManager,
                                                    agendaCalendarService,
                                                    agendaEventService,
                                                    agendaRemoteEventService,
                                                    agendaEventDatePollService,
                                                    agendaEventReminderService,
                                                    agendaEventConferenceService,
                                                    agendaEventAttendeeService,
                                                    stored,
                                                    null,
                                                    ZoneOffset.UTC,
                                                    List.of("all"));
      return new JsonGeneratorImpl().createJsonObject(series).toString();
    } finally {
      ApplicationContextImpl.setCurrent(null);
    }
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }

}
