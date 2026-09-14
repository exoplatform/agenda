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

import java.util.List;

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
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionRequestEntity;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionServiceImpl;
import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Pins the REST contract of calendar subscriptions (EXO-90278): the statuses
 * the service's refusals map to, the refusal codes carried back and nothing
 * else, responses never cached, and every handler closed to an anonymous
 * request behind real method security.
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
   * The status and the reason a handler answers.
   *
   * @param call the handler call
   * @return the exception
   */
  private static ResponseStatusException refused(Runnable call) {
    return assertThrows(ResponseStatusException.class, call::run);
  }

  /**
   * A listing maps the subscriptions with their URL and is never cached.
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
  }

  /**
   * A refused link answers 400 with its code, and a refusal whose message is not
   * an agenda code answers a generic one: nothing else of a message goes back.
   *
   * @throws Exception never
   */
  @Test
  void aRefusedLinkAnswersBadRequestWithItsCodeOnly() throws Exception {
    when(service.checkUrl(anyString(), eq("john"))).thenThrow(new IllegalArgumentException("agenda.calendarSubscription.refusedAddress"));
    ResponseStatusException check = refused(() -> resource.checkUrl(request, new CalendarSubscriptionRequestEntity("u", null, null)));
    assertEquals(HttpStatus.BAD_REQUEST, check.getStatusCode());
    assertEquals("agenda.calendarSubscription.refusedAddress", check.getReason());

    when(service.createSubscription(any(), any(), any(), eq("john"))).thenThrow(new IllegalArgumentException("https://secret@x"));
    ResponseStatusException create = refused(() -> resource.createSubscription(request,
                                                                               new CalendarSubscriptionRequestEntity("u", null, null)));
    assertEquals(HttpStatus.BAD_REQUEST, create.getStatusCode());
    assertEquals("agenda.calendarSubscription.invalidRequest", create.getReason());
  }

  /**
   * Not found answers 404, not the owner 403.
   *
   * @throws Exception never
   */
  @Test
  void notFoundAndNotTheOwnerMapTo404And403() throws Exception {
    when(service.updateSubscription(eq(1L), any(), any(), any(), eq("john"))).thenThrow(new ObjectNotFoundException("none"));
    when(service.updateSubscription(eq(2L), any(), any(), any(), eq("john"))).thenThrow(new IllegalAccessException("mary's"));
    doThrow(new ObjectNotFoundException("none")).when(service).deleteSubscription(1L, "john");
    doThrow(new IllegalAccessException("mary's")).when(service).deleteSubscription(2L, "john");

    assertEquals(HttpStatus.NOT_FOUND, refused(() -> resource.updateSubscription(request, 1, null)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN, refused(() -> resource.updateSubscription(request, 2, null)).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, refused(() -> resource.deleteSubscription(request, 1)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN, refused(() -> resource.deleteSubscription(request, 2)).getStatusCode());
    assertEquals(HttpStatus.NO_CONTENT, resource.deleteSubscription(request, 3).getStatusCode());
  }

  /**
   * A refresh asked too soon answers 429, one already running 409.
   *
   * @throws Exception never
   */
  @Test
  void aRefreshTooSoonOrRunningMapsTo429Or409() throws Exception {
    when(service.refreshSubscription(1L, "john")).thenThrow(new IllegalStateException(AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON));
    when(service.refreshSubscription(2L, "john")).thenThrow(new IllegalStateException(AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS));
    when(service.refreshSubscription(3L, "john")).thenThrow(new IllegalStateException("codec gone"));

    ResponseStatusException tooSoon = refused(() -> resource.refreshSubscription(request, 1));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, tooSoon.getStatusCode());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON, tooSoon.getReason());
    assertEquals(HttpStatus.CONFLICT, refused(() -> resource.refreshSubscription(request, 2)).getStatusCode());
    assertThrows(IllegalStateException.class, () -> resource.refreshSubscription(request, 3));
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
