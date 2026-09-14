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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Drives the calendar link resource through Spring MVC's dispatcher: the path
 * mapping of the feed ({@code /ical/{token}.ics}), its headers, and the status
 * each service answer maps to.
 */
class AgendaCalendarLinkRestTest {

  private static final String       TOKEN    = "Wz3pQyv5Hq0dS9bTf2LkMn8Rc1XeUa7GjYo4NiVhB6s";

  private static final String       DOCUMENT = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

  private AgendaCalendarLinkService service;

  private MockMvc                   mockMvc;

  /**
   * Builds the dispatcher over the resource.
   */
  @BeforeEach
  void setUp() {
    service = mock(AgendaCalendarLinkService.class);
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity creator = new Identity("organization", "manager");
    Profile profile = new Profile(creator);
    profile.setProperty(Profile.FULL_NAME, "Mary Manager");
    creator.setProfile(profile);
    when(identityManager.getIdentity("3")).thenReturn(creator);
    mockMvc = MockMvcBuilders.standaloneSetup(new AgendaCalendarLinkRest(service, identityManager)).build();
  }

  /**
   * The feed answers the document as UTF-8 {@code text/calendar}, private,
   * cacheable briefly, with an entity tag, and asks not to be indexed.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theFeedAnswersTheCalendarDocument() throws Exception {
    when(service.getCalendarFeed(TOKEN)).thenReturn(DOCUMENT);

    MvcResult result = mockMvc.perform(get("/ical/" + TOKEN + ".ics"))
                              .andExpect(status().isOk())
                              .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/calendar;charset=UTF-8"))
                              .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                              .andExpect(header().string("Referrer-Policy", "no-referrer"))
                              .andReturn();

    assertEquals(DOCUMENT, result.getResponse().getContentAsString());
    String cacheControl = result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
    assertTrue(cacheControl.contains("private") && cacheControl.contains("max-age=900"), cacheControl);
    assertTrue(result.getResponse().getHeader(HttpHeaders.ETAG).matches("^\"[0-9a-f]{64}\"$"));
    verify(service).getCalendarFeed(TOKEN);
  }

  /**
   * A client holding the current entity tag gets a bodiless 304.
   *
   * @throws Exception when the request fails
   */
  @Test
  void anUnchangedFeedAnswersNotModified() throws Exception {
    when(service.getCalendarFeed(TOKEN)).thenReturn(DOCUMENT);
    String entityTag = mockMvc.perform(get("/ical/" + TOKEN + ".ics")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

    MvcResult result = mockMvc.perform(get("/ical/" + TOKEN + ".ics").header(HttpHeaders.IF_NONE_MATCH, entityTag))
                              .andExpect(status().isNotModified())
                              .andReturn();

    assertEquals(0, result.getResponse().getContentAsByteArray().length);
  }

  /**
   * A token that opens nothing gets a bodiless, uncacheable 404 that does not
   * echo the token.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aDeadTokenAnswersABodilessNotFound() throws Exception {
    when(service.getCalendarFeed(anyString())).thenThrow(new ObjectNotFoundException("agenda.calendarLink.notFound"));

    MvcResult result = mockMvc.perform(get("/ical/" + TOKEN + ".ics"))
                              .andExpect(status().isNotFound())
                              .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                              .andReturn();

    assertEquals(0, result.getResponse().getContentAsByteArray().length);
    assertFalse(String.valueOf(result.getResponse().getHeaderNames()).contains(TOKEN));
  }

  /**
   * The creation answers the URL, built on this WAR's REST root, once.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aCreationAnswersTheUrlOnce() throws Exception {
    when(service.saveCalendarLink(20, "manager")).thenReturn(TOKEN);
    when(service.getCalendarLink(20, "manager")).thenReturn(new CalendarLink(20, 3, 1000, "digest", true));

    MvcResult result = mockMvc.perform(as("manager", post("/agenda/calendars/20/link").contextPath("/agenda")))
                              .andExpect(status().isOk())
                              .andExpect(jsonPath("$.exists").value(true))
                              .andExpect(jsonPath("$.active").value(true))
                              .andExpect(jsonPath("$.creatorName").value("Mary Manager"))
                              .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("/agenda/rest/ical/" + TOKEN + ".ics"), body);
    assertFalse(body.contains("digest"), "the token digest never leaves the server");

    String status = mockMvc.perform(as("manager", get("/calendars/20/link"))).andReturn().getResponse().getContentAsString();
    assertFalse(status.contains(TOKEN), "reading the status never returns the URL");
  }

  /**
   * The status of a calendar with no link says so.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aCalendarWithNoLinkSaysSo() throws Exception {
    mockMvc.perform(as("owner", get("/calendars/10/link")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.exists").value(false))
           .andExpect(jsonPath("$.active").value(false));
  }

  /**
   * The asking user is the session's; a refusal is a 403, a missing calendar a
   * 404, an invalid identifier a 400, and a deletion a 204.
   *
   * @throws Exception when a request fails
   */
  @Test
  void serviceAnswersMapToStatuses() throws Exception {
    when(service.getCalendarLink(20, "member")).thenThrow(new IllegalAccessException("refused"));
    when(service.saveCalendarLink(20, "member")).thenThrow(new IllegalAccessException("refused"));
    org.mockito.Mockito.doThrow(new IllegalAccessException("refused")).when(service).deleteCalendarLink(20, "member");
    when(service.getCalendarLink(999, "member")).thenThrow(new ObjectNotFoundException("missing"));
    when(service.saveCalendarLink(0, "member")).thenThrow(new IllegalArgumentException("agenda.calendarLink.invalidCalendar"));

    mockMvc.perform(as("member", get("/calendars/20/link"))).andExpect(status().isForbidden());
    mockMvc.perform(as("member", post("/calendars/20/link"))).andExpect(status().isForbidden());
    mockMvc.perform(as("member", delete("/calendars/20/link"))).andExpect(status().isForbidden());
    mockMvc.perform(as("member", get("/calendars/999/link"))).andExpect(status().isNotFound());
    mockMvc.perform(as("member", post("/calendars/0/link"))).andExpect(status().isBadRequest());
    mockMvc.perform(as("manager", delete("/calendars/20/link"))).andExpect(status().isNoContent());
    verify(service).deleteCalendarLink(20, "manager");
  }

  /**
   * Without the extension the feed path maps to nothing.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theFeedPathRequiresItsExtension() throws Exception {
    mockMvc.perform(get("/ical/" + TOKEN)).andExpect(status().isNotFound());
    verify(service, org.mockito.Mockito.never()).getCalendarFeed(anyString());
    verify(service, org.mockito.Mockito.never()).getCalendarLink(anyLong(), anyString());
  }

  /**
   * Sets the authenticated user of a request, as the container would.
   *
   * @param username the user
   * @param builder the request
   * @return the request
   */
  private MockHttpServletRequestBuilder as(String username, MockHttpServletRequestBuilder builder) {
    return builder.with(request -> {
      request.setRemoteUser(username);
      return request;
    });
  }

}
