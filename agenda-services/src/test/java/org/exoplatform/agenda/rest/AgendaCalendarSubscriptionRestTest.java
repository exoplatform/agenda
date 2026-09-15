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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionRequestEntity;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionServiceImpl;
import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Pins the REST contract of calendar subscriptions (EXO-90278): the statuses
 * the service's refusals map to, the refusal code written in the body of each —
 * never left to Spring's error page, which the platform answers without its
 * message — and nothing else, responses never cached, and every handler closed
 * to an anonymous request behind real method security.
 */
class AgendaCalendarSubscriptionRestTest {

  private AnnotationConfigApplicationContext context;

  private AgendaCalendarSubscriptionService  service;

  private AgendaCalendarSubscriptionRest     resource;

  private MockHttpServletRequest             request;

  /**
   * Starts a context with method security over the resource, as a signed-in
   * user.
   */
  @BeforeEach
  void startContext() {
    context = new AnnotationConfigApplicationContext(MethodSecurityConfiguration.class);
    resource = context.getBean(AgendaCalendarSubscriptionRest.class);
    service = context.getBean(AgendaCalendarSubscriptionService.class);
    request = new MockHttpServletRequest();
    request.setRemoteUser("john");
    SecurityContextHolder.getContext()
                         .setAuthentication(new PreAuthenticatedAuthenticationToken("john",
                                                                                    "john",
                                                                                    List.of(new SimpleGrantedAuthority("users"))));
  }

  /**
   * Closes the context and forgets the authentication.
   */
  @AfterEach
  void stopContext() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  /**
   * Asserts a refusal: its status, a body carrying its code as
   * {@code message} and nothing else, and no caching.
   *
   * @param response the answer
   * @param status the expected status
   * @param code the expected code
   */
  private static void assertRefusal(ResponseEntity<?> response, HttpStatus status, String code) {
    assertEquals(status, response.getStatusCode());
    assertEquals(Map.of("message", code), response.getBody());
    assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
  }

  /**
   * A listing maps the subscriptions with their URL and is never cached; a user
   * with no usable identity is refused with its code.
   *
   * @throws Exception never
   */
  @Test
  void theListingCarriesTheUrlAndIsNeverCached() throws Exception {
    CalendarSubscription subscription = new CalendarSubscription();
    subscription.setId(11);
    subscription.setCalendarId(77);
    subscription.setName("Holidays");
    subscription.setUrl("https://feeds.example.org/holidays.ics");
    subscription.setLastError("agenda.calendarSubscription.unreachable");
    when(service.getSubscriptions("john")).thenReturn(List.of(subscription));

    ResponseEntity<?> response = resource.getSubscriptions(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    assertEquals("[CalendarSubscriptionStatusEntity(id=11, calendarId=77, name=Holidays, color=null, "
        + "url=https://feeds.example.org/holidays.ics, lastSuccessDate=0, lastAttemptDate=0, nextRefreshDate=0, "
        + "lastError=agenda.calendarSubscription.unreachable, truncated=false, createdDate=0)]", String.valueOf(response.getBody()));

    when(service.getSubscriptions("john")).thenThrow(new IllegalAccessException("no identity"));
    assertRefusal(resource.getSubscriptions(request), HttpStatus.FORBIDDEN, "agenda.calendarSubscription.forbidden");
  }

  /**
   * A refused link answers 400 with its code in the body, and a refusal whose
   * message is not an agenda code answers a generic one: nothing else of a
   * message goes back.
   *
   * @throws Exception never
   */
  @Test
  void aRefusedLinkAnswersBadRequestWithItsCodeOnly() throws Exception {
    when(service.checkUrl(anyString(), eq("john"))).thenThrow(new IllegalArgumentException("agenda.calendarSubscription.ownCalendar"));
    assertRefusal(resource.checkUrl(request, new CalendarSubscriptionRequestEntity("u", null, null)),
                  HttpStatus.BAD_REQUEST,
                  "agenda.calendarSubscription.ownCalendar");

    when(service.createSubscription(any(), any(), any(), eq("john"))).thenThrow(new IllegalArgumentException("https://secret@x"));
    assertRefusal(resource.createSubscription(request, new CalendarSubscriptionRequestEntity("u", null, null)),
                  HttpStatus.BAD_REQUEST,
                  "agenda.calendarSubscription.invalidRequest");
    assertRefusal(resource.createSubscription(request, null), HttpStatus.BAD_REQUEST, "agenda.calendarSubscription.invalidUrl");

    when(service.updateSubscription(eq(4L), any(), any(), any(), eq("john"))).thenThrow(new IllegalArgumentException("agenda.calendarSubscription.alreadySubscribed"));
    assertRefusal(resource.updateSubscription(request, 4, null), HttpStatus.BAD_REQUEST, "agenda.calendarSubscription.alreadySubscribed");
  }

  /**
   * Not found answers 404, not the owner 403, each with its code in the body.
   *
   * @throws Exception never
   */
  @Test
  void notFoundAndNotTheOwnerMapTo404And403() throws Exception {
    when(service.updateSubscription(eq(1L), any(), any(), any(), eq("john"))).thenThrow(new ObjectNotFoundException("none"));
    when(service.updateSubscription(eq(2L), any(), any(), any(), eq("john"))).thenThrow(new IllegalAccessException("mary's"));
    doThrow(new ObjectNotFoundException("none")).when(service).deleteSubscription(1L, "john");
    doThrow(new IllegalAccessException("mary's")).when(service).deleteSubscription(2L, "john");
    when(service.refreshSubscription(1L, "john")).thenThrow(new ObjectNotFoundException("none"));
    when(service.refreshSubscription(2L, "john")).thenThrow(new IllegalAccessException("mary's"));

    assertRefusal(resource.updateSubscription(request, 1, null), HttpStatus.NOT_FOUND, "agenda.calendarSubscription.notFound");
    assertRefusal(resource.updateSubscription(request, 2, null), HttpStatus.FORBIDDEN, "agenda.calendarSubscription.forbidden");
    assertRefusal(resource.deleteSubscription(request, 1), HttpStatus.NOT_FOUND, "agenda.calendarSubscription.notFound");
    assertRefusal(resource.deleteSubscription(request, 2), HttpStatus.FORBIDDEN, "agenda.calendarSubscription.forbidden");
    assertRefusal(resource.refreshSubscription(request, 1), HttpStatus.NOT_FOUND, "agenda.calendarSubscription.notFound");
    assertRefusal(resource.refreshSubscription(request, 2), HttpStatus.FORBIDDEN, "agenda.calendarSubscription.forbidden");
    assertEquals(HttpStatus.NO_CONTENT, resource.deleteSubscription(request, 3).getStatusCode());
  }

  /**
   * A refresh asked too soon, or a read beyond the user's bound, answers 429; a
   * refresh already running 409; each with its code in the body. Any other
   * state is not a refusal and is not answered as one.
   *
   * @throws Exception never
   */
  @Test
  void aRefreshTooSoonOrRunningMapsTo429Or409() throws Exception {
    when(service.refreshSubscription(1L, "john")).thenThrow(new IllegalStateException(AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON));
    when(service.refreshSubscription(2L, "john")).thenThrow(new IllegalStateException(AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS));
    when(service.refreshSubscription(3L, "john")).thenThrow(new IllegalStateException("codec gone"));

    assertRefusal(resource.refreshSubscription(request, 1), HttpStatus.TOO_MANY_REQUESTS, AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON);
    assertRefusal(resource.refreshSubscription(request, 2), HttpStatus.CONFLICT, AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS);
    assertThrows(IllegalStateException.class, () -> resource.refreshSubscription(request, 3));

    when(service.checkUrl(anyString(), eq("john"))).thenThrow(new IllegalStateException(AgendaCalendarSubscriptionServiceImpl.TOO_MANY_READS));
    assertRefusal(resource.checkUrl(request, new CalendarSubscriptionRequestEntity("u", null, null)),
                  HttpStatus.TOO_MANY_REQUESTS,
                  AgendaCalendarSubscriptionServiceImpl.TOO_MANY_READS);
  }

  /**
   * Through Spring MVC with no error page and no {@code server.error.*}
   * property at all, the serialized answer to a refusal carries its code: the
   * body is the resource's own, not the error page's.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theSerializedRefusalCarriesItsCodeWithoutTheErrorPage() throws Exception {
    AgendaCalendarSubscriptionService mvcService = mock(AgendaCalendarSubscriptionService.class);
    when(mvcService.checkUrl(anyString(), eq("alice"))).thenThrow(new IllegalArgumentException("agenda.calendarSubscription.ownCalendar"));
    doThrow(new ObjectNotFoundException("none")).when(mvcService).deleteSubscription(9L, "alice");
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgendaCalendarSubscriptionRest(mvcService)).build();

    var check = mvc.perform(post("/calendars/subscriptions/check").contentType(MediaType.APPLICATION_JSON)
                                                                  .content("{\"url\":\"http://localhost:8080/agenda/rest/ical/T.ics\"}")
                                                                  .with(sent -> {
                                                                    sent.setRemoteUser("alice");
                                                                    return sent;
                                                                  }))
                   .andReturn()
                   .getResponse();
    assertEquals(400, check.getStatus());
    assertEquals("{\"message\":\"agenda.calendarSubscription.ownCalendar\"}", check.getContentAsString());
    assertEquals("no-store", check.getHeader(HttpHeaders.CACHE_CONTROL));

    var missing = mvc.perform(delete("/calendars/subscriptions/9").with(sent -> {
      sent.setRemoteUser("alice");
      return sent;
    })).andReturn().getResponse();
    assertEquals(404, missing.getStatus());
    assertEquals("{\"message\":\"agenda.calendarSubscription.notFound\"}", missing.getContentAsString());
  }

  /**
   * An anonymous request reaches no handler; a signed-in one reaches them all.
   *
   * @throws Exception never
   */
  @Test
  void anAnonymousRequestReachesNoHandler() throws Exception {
    when(service.createSubscription(any(), any(), any(), anyString())).thenReturn(new CalendarSubscription());
    when(service.updateSubscription(anyLong(), any(), any(), any(), anyString())).thenReturn(new CalendarSubscription());
    when(service.refreshSubscription(anyLong(), anyString())).thenReturn(new CalendarSubscription());
    CalendarSubscriptionRequestEntity body = new CalendarSubscriptionRequestEntity("https://feeds.example.org/a.ics", null, null);

    assertNotNull(resource.getSubscriptions(request));
    assertNotNull(resource.checkUrl(request, body));
    assertNotNull(resource.createSubscription(request, body));
    assertNotNull(resource.updateSubscription(request, 1, body));
    assertNotNull(resource.refreshSubscription(request, 1));
    assertNotNull(resource.deleteSubscription(request, 1));

    SecurityContextHolder.getContext()
                         .setAuthentication(new AnonymousAuthenticationToken("key",
                                                                             "__anonim",
                                                                             List.of(new SimpleGrantedAuthority("guests"))));
    assertThrows(AccessDeniedException.class, () -> resource.getSubscriptions(request));
    assertThrows(AccessDeniedException.class, () -> resource.checkUrl(request, body));
    assertThrows(AccessDeniedException.class, () -> resource.createSubscription(request, body));
    assertThrows(AccessDeniedException.class, () -> resource.updateSubscription(request, 1, body));
    assertThrows(AccessDeniedException.class, () -> resource.refreshSubscription(request, 1));
    assertThrows(AccessDeniedException.class, () -> resource.deleteSubscription(request, 1));
  }

  /**
   * Method security as the portal enables it, over the resource.
   */
  @Configuration
  @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
  static class MethodSecurityConfiguration {

    /**
     * The service the resource calls, mocked.
     *
     * @return the mock
     */
    @Bean
    AgendaCalendarSubscriptionService subscriptionService() {
      return mock(AgendaCalendarSubscriptionService.class);
    }

    /**
     * The resource under test.
     *
     * @param subscriptionService the mocked service
     * @return the resource
     */
    @Bean
    AgendaCalendarSubscriptionRest agendaCalendarSubscriptionRest(AgendaCalendarSubscriptionService subscriptionService) {
      return new AgendaCalendarSubscriptionRest(subscriptionService);
    }
  }

}
