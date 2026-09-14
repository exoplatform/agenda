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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Pins that the calendar feed is the only door agenda's Spring REST opens
 * without a session.
 * <p>
 * The portal's security chain lets every {@code /rest/} request through,
 * anonymous included — Spring's anonymous token counts as authenticated — and
 * leaves the decision to method security ({@code securedEnabled}); social's
 * {@code LoginRest} relies on exactly that. So "permit exactly this path" is a
 * property of the handlers, and two tests hold it: one reads every agenda
 * controller and lists the handlers without {@code @Secured}; the other runs the
 * resource behind real method security as the anonymous user the chain hands
 * over, and as a signed-in one.
 */
class AgendaRestSecurityTest {

  private AnnotationConfigApplicationContext context;

  private AgendaCalendarLinkRest             resource;

  /**
   * Starts a context with method security over the resource.
   *
   * @throws Exception never, the mocked service declares it
   */
  @BeforeEach
  void startContext() throws Exception {
    context = new AnnotationConfigApplicationContext(MethodSecurityConfiguration.class);
    resource = context.getBean(AgendaCalendarLinkRest.class);
    AgendaCalendarLinkService service = context.getBean(AgendaCalendarLinkService.class);
    when(service.getCalendarFeed(anyString())).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
    when(service.saveCalendarLink(anyLong(), anyString())).thenReturn("token");
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
   * Across every agenda {@code @RestController}, the feed is the only handler
   * that does not require a signed-in user.
   *
   * @throws Exception when a controller class cannot be loaded
   */
  @Test
  void theFeedIsTheOnlyHandlerWithoutSecured() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(RestController.class));
    List<String> unsecured = new ArrayList<>();
    int handlers = 0;
    int controllers = 0;
    for (BeanDefinition candidate : scanner.findCandidateComponents("org.exoplatform.agenda")) {
      controllers++;
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          handlers++;
          if (!method.isAnnotationPresent(Secured.class) && !controller.isAnnotationPresent(Secured.class)) {
            unsecured.add(controller.getSimpleName() + "#" + method.getName());
          }
        }
      }
    }
    assertTrue(controllers >= 3 && handlers >= 6, "the scan must find the agenda controllers: " + controllers + "/" + handlers);
    assertEquals(List.of("AgendaCalendarLinkRest#getCalendarFeed"), unsecured);
  }

  /**
   * The anonymous user the portal chain hands over reads a feed and is refused
   * every management handler.
   */
  @Test
  void anAnonymousRequestReachesTheFeedAndNothingElse() {
    SecurityContextHolder.getContext()
                         .setAuthentication(new AnonymousAuthenticationToken("key",
                                                                             "__anonim",
                                                                             List.of(new SimpleGrantedAuthority("guests"))));
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertNotNull(resource.getCalendarFeed("token").getBody());
    assertThrows(AccessDeniedException.class, () -> resource.getCalendarLink(request, 10));
    assertThrows(AccessDeniedException.class, () -> resource.saveCalendarLink(request, 10));
    assertThrows(AccessDeniedException.class, () -> resource.deleteCalendarLink(request, 10));
  }

  /**
   * A signed-in user passes method security on the management handlers — the
   * service decides the rest.
   */
  @Test
  void aSignedInUserReachesTheManagementHandlers() {
    SecurityContextHolder.getContext()
                         .setAuthentication(new PreAuthenticatedAuthenticationToken("john",
                                                                                    "john",
                                                                                    List.of(new SimpleGrantedAuthority("users"))));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteUser("john");

    assertNotNull(resource.getCalendarLink(request, 10));
    assertNotNull(resource.deleteCalendarLink(request, 10));
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
    AgendaCalendarLinkService calendarLinkService() {
      return mock(AgendaCalendarLinkService.class);
    }

    /**
     * The resource under test.
     *
     * @param calendarLinkService the mocked service
     * @return the resource
     */
    @Bean
    AgendaCalendarLinkRest agendaCalendarLinkRest(AgendaCalendarLinkService calendarLinkService) {
      return new AgendaCalendarLinkRest(calendarLinkService, mock(IdentityManager.class));
    }
  }

}
