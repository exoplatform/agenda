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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.exoplatform.agenda.constant.AvailabilityDisclosure;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserBusyTime;
import org.exoplatform.agenda.rest.model.UserBusyTimeEntity;
import org.exoplatform.agenda.service.AgendaAvailabilityService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pins what leaves the server, and on whose behalf.
 * <p>
 * Two properties, both of them the reason this endpoint is allowed to exist at
 * all: the caller is whoever the container authenticated and never whoever the
 * request claims to be, and a busy block carries a time range and nothing that
 * could say what the person is doing.
 */
class AgendaAvailabilityRestTest {

  private static final long              ORGANISER    = 5L;

  private static final long              PARTICIPANT  = 400L;

  private static final long              IMPERSONATED = 999L;

  private static final ZonedDateTime     WINDOW_START = ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, ZoneOffset.UTC);

  private static final ZonedDateTime     WINDOW_END   = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneOffset.UTC);

  private AgendaAvailabilityService      availabilityService;

  private IdentityManager                identityManager;

  private HttpServletRequest             request;

  private AgendaAvailabilityRest         availabilityRest;

  @BeforeEach
  void setUp() {
    availabilityService = Mockito.mock(AgendaAvailabilityService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    request = Mockito.mock(HttpServletRequest.class);
    availabilityRest = new AgendaAvailabilityRest(availabilityService, identityManager);

    Identity organiser = Mockito.mock(Identity.class);
    when(organiser.getId()).thenReturn(String.valueOf(ORGANISER));
    when(identityManager.getOrCreateUserIdentity("organiser")).thenReturn(organiser);
    when(request.getRemoteUser()).thenReturn("organiser");
    when(availabilityService.getBusyTime(any(), any(), any(), anyLong())).thenReturn(List.of());
  }

  @Test
  void theAskingUserComesFromTheSessionAndNeverFromTheRequest() {
    // Every parameter the request could carry claims to be somebody else. A
    // reader of any of them fails the argument assertion below rather than
    // throwing, so the mutant dies of the property under test.
    when(request.getParameter(anyString())).thenReturn(String.valueOf(IMPERSONATED));

    availabilityRest.getBusyTime(request,
                                 List.of(PARTICIPANT),
                                 WINDOW_START.toString(),
                                 WINDOW_END.toString(),
                                 "UTC");

    verify(availabilityService).getBusyTime(eq(List.of(PARTICIPANT)), any(), any(), eq(ORGANISER));
  }

  @Test
  void theEndpointDeclaresNoParameterForTheAskingUser() throws Exception {
    // A structural pin beside the behavioural one above: the identity cannot
    // be supplied because there is nowhere to supply it.
    Set<String> parameterNames =
                              Set.of(AgendaAvailabilityRest.class.getMethod("getBusyTime",
                                                                            HttpServletRequest.class,
                                                                            List.class,
                                                                            String.class,
                                                                            String.class,
                                                                            String.class)
                                                                  .getParameters())
                                 .stream()
                                 .map(parameter -> parameter.getName().toLowerCase())
                                 .collect(java.util.stream.Collectors.toSet());

    assertFalse(parameterNames.stream().anyMatch(name -> name.contains("user") || name.contains("asker")),
                "the asking user must not be a parameter of this endpoint");
  }

  @Test
  void anUnreadableWindowIsABadRequestAndNeverReachesTheService() {
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> availabilityRest.getBusyTime(request,
                                                                                        List.of(PARTICIPANT),
                                                                                        "not-a-date",
                                                                                        WINDOW_END.toString(),
                                                                                        "UTC"));

    assertEquals(400, exception.getStatusCode().value());
    verify(availabilityService, Mockito.never()).getBusyTime(any(), any(), any(), anyLong());
  }

  @Test
  void onlyTimeRangesReachTheClient() throws Exception {
    when(availabilityService.getBusyTime(any(), any(), any(), anyLong()))
                                                                        .thenReturn(List.of(new UserBusyTime(PARTICIPANT,
                                                                                                             AvailabilityDisclosure.DISCLOSED,
                                                                                                             List.of(new TimeBlock(WINDOW_START,
                                                                                                                                   WINDOW_END)))));

    List<UserBusyTimeEntity> entities = availabilityRest.getBusyTime(request,
                                                                     List.of(PARTICIPANT),
                                                                     WINDOW_START.toString(),
                                                                     WINDOW_END.toString(),
                                                                     "UTC");

    // Serialised, because that is what actually reaches the browser: a field
    // added to the entity, or a domain object let through whole, changes this
    // key set and nothing else in the suite would notice.
    JsonNode json = new ObjectMapper().valueToTree(entities).get(0);
    assertEquals(Set.of("identityId", "disclosure", "busy"), fieldNames(json));
    assertEquals(Set.of("start", "end"), fieldNames(json.get("busy").get(0)));
  }

  @Test
  void anUnreadParticipantCarriesNoBlockListOnTheWire() throws Exception {
    when(availabilityService.getBusyTime(any(), any(), any(), anyLong()))
                                                                        .thenReturn(List.of(new UserBusyTime(PARTICIPANT,
                                                                                                             AvailabilityDisclosure.NOT_DISCLOSED,
                                                                                                             null),
                                                                                            new UserBusyTime(PARTICIPANT + 1,
                                                                                                             AvailabilityDisclosure.DISCLOSED,
                                                                                                             List.of())));

    List<UserBusyTimeEntity> entities = availabilityRest.getBusyTime(request,
                                                                     List.of(PARTICIPANT),
                                                                     WINDOW_START.toString(),
                                                                     WINDOW_END.toString(),
                                                                     "UTC");

    // The contrast is the pin: both participants have no busy content, and
    // only the status and the presence of the list tell them apart.
    assertEquals("not_disclosed", entities.get(0).getDisclosure());
    assertNull(entities.get(0).getBusy(), "an unread participant must not reach the client with an empty list");
    assertEquals("disclosed", entities.get(1).getDisclosure());
    assertTrue(entities.get(1).getBusy().isEmpty());
  }

  /**
   * The field names of a JSON object.
   *
   * @param node the object
   * @return its keys
   */
  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new java.util.HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

}
