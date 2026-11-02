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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.exoplatform.agenda.model.EventAttendeesExport;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Pins what the export endpoint adds to the service through the dispatcher:
 * the user from the session and their language from their settings, the date
 * and the zone read from the query, the file's type, name and cache headers,
 * and a refusal per service exception, never cached and never carrying the
 * engine's text; and the user an address typed in the participants drawer
 * belongs to, served as the drawer adds them and never cached.
 */
class AgendaEventAttendeeRestTest {

  private static final String         FILE_NAME = "20261020_Réunion d'équipe_participants list.csv";

  private static final String         CONTENT   = "\uFEFF\"Nom\",\"Réponse\"\r\n\"Jane Doe\",\"Oui\"\r\n";

  private AgendaEventAttendeeService service;

  private MockedStatic<Utils>        utils;

  private MockMvc                    mockMvc;

  /**
   * Builds the dispatcher over the resource, with Alice as the user of the
   * session, whose platform language is French.
   */
  @BeforeEach
  void setUp() {
    service = mock(AgendaEventAttendeeService.class);
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity alice = new Identity("organization", "alice");
    alice.setId("3");
    when(identityManager.getOrCreateUserIdentity("alice")).thenReturn(alice);
    utils = mockStatic(Utils.class);
    utils.when(() -> Utils.getUserLanguage("alice")).thenReturn("fr");
    mockMvc = MockMvcBuilders.standaloneSetup(new AgendaEventAttendeeRest(service, identityManager)).build();
  }

  @AfterEach
  void tearDown() {
    utils.close();
  }

  /**
   * The file is served as UTF-8 CSV, saved under its name in both forms of
   * the header, and never stored by a cache: it carries profile data. The
   * service gets the session's user, their language, the date as an instant
   * and the zone.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theFileIsServedAsAnUncachedCsvAttachment() throws Exception {
    when(service.exportEventAttendees(12L,
                                      ZonedDateTime.of(2026, 10, 21, 7, 0, 0, 0, ZoneOffset.UTC),
                                      3L,
                                      Locale.FRENCH,
                                      ZoneId.of("Europe/Paris"))).thenReturn(new EventAttendeesExport(FILE_NAME, CONTENT));

    MvcResult result = mockMvc.perform(as("alice",
                                          get("/events/12/attendees/export").param("occurrenceId", "2026-10-21T09:00:00+02:00")
                                                                            .param("timeZoneId", "Europe/Paris")))
                              .andExpect(status().isOk())
                              .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                              .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                              .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                         "attachment; filename=\"20261020_R_union d'_quipe_participants list.csv\";"
                                                             + " filename*=UTF-8''20261020_R%C3%A9union%20d%27%C3%A9quipe_participants%20list.csv"))
                              .andReturn();

    assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8), result.getResponse().getContentAsByteArray());
  }

  /**
   * With no date and no zone, the service exports the event as a whole and
   * dates the file in UTC; a user whose settings name no language gets the
   * request's.
   *
   * @throws Exception when the request fails
   */
  @Test
  void withoutDateZoneOrLanguageTheDefaultsApply() throws Exception {
    utils.when(() -> Utils.getUserLanguage("alice")).thenReturn("");
    when(service.exportEventAttendees(eq(12L), isNull(), eq(3L), eq(Locale.GERMAN), eq(ZoneOffset.UTC)))
                                                                                                  .thenReturn(new EventAttendeesExport(FILE_NAME,
                                                                                                                                       CONTENT));

    mockMvc.perform(as("alice", get("/events/12/attendees/export").locale(Locale.GERMAN))).andExpect(status().isOk());

    verify(service).exportEventAttendees(eq(12L), isNull(), eq(3L), eq(Locale.GERMAN), eq(ZoneOffset.UTC));
  }

  /**
   * A missing event or a cancelled date is a 404, a user who may not update
   * the event a 403, a date poll a 400 with its code; each refusal writes its
   * code and is never cached.
   *
   * @throws Exception when the request fails
   */
  @Test
  void eachRefusalAnswersItsStatusAndCode() throws Exception {
    when(service.exportEventAttendees(eq(404L), any(), anyLong(), any(), any())).thenThrow(new ObjectNotFoundException("gone"));
    when(service.exportEventAttendees(eq(403L), any(), anyLong(), any(), any())).thenThrow(new IllegalAccessException("no"));
    when(service.exportEventAttendees(eq(400L),
                                      any(),
                                      anyLong(),
                                      any(),
                                      any())).thenThrow(new IllegalArgumentException("agenda.export.datePoll"));

    mockMvc.perform(as("alice", get("/events/404/attendees/export")))
           .andExpect(status().isNotFound())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.message").value("agenda.export.eventNotFound"));
    mockMvc.perform(as("alice", get("/events/403/attendees/export")))
           .andExpect(status().isForbidden())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.message").value("agenda.export.forbidden"));
    mockMvc.perform(as("alice", get("/events/400/attendees/export")))
           .andExpect(status().isBadRequest())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.message").value("agenda.export.datePoll"));
  }

  /**
   * An argument refusal that carries no export code answers the generic one:
   * the engine's text never reaches the body.
   *
   * @throws Exception when the request fails
   */
  @Test
  void anUncodedRefusalNeverEchoesItsText() throws Exception {
    when(service.exportEventAttendees(eq(12L),
                                      any(),
                                      anyLong(),
                                      any(),
                                      any())).thenThrow(new IllegalArgumentException("Text '/internal/path' could not be parsed"));

    mockMvc.perform(as("alice", get("/events/12/attendees/export")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("agenda.export.invalid"));
  }

  /**
   * A date or a zone that cannot be read is refused before the service is
   * asked.
   *
   * @throws Exception when the request fails
   */
  @Test
  void anUnreadableDateOrZoneIsRefusedFirst() throws Exception {
    mockMvc.perform(as("alice", get("/events/12/attendees/export").param("occurrenceId", "tomorrow")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("agenda.export.invalid"));
    mockMvc.perform(as("alice", get("/events/12/attendees/export").param("timeZoneId", "Mars/Olympus_Mons")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("agenda.export.invalid"));

    verify(service, never()).exportEventAttendees(anyLong(), any(), anyLong(), any(), any());
  }

  /**
   * The user an address belongs to is served as the participants drawer adds
   * them, and never stored by a cache: who owns an address is answered to a
   * signed-in user, and only while its owner shows it.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theUserOfAnAddressIsServedAsTheDrawerAddsThem() throws Exception {
    Identity jane = new Identity("organization", "jane");
    jane.setId("11");
    Profile profile = new Profile(jane);
    profile.setProperty(Profile.FULL_NAME, "Jane Doe");
    profile.setAvatarUrl("/portal/rest/v1/social/users/jane/avatar?lastModified=7");
    jane.setProfile(profile);
    when(service.getUserIdentityByEmail("Jane.Doe@example.com")).thenReturn(jane);

    mockMvc.perform(as("alice", get("/events/attendees/user").param("email", "Jane.Doe@example.com")))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.id").value("11"))
           .andExpect(jsonPath("$.providerId").value("organization"))
           .andExpect(jsonPath("$.remoteId").value("jane"))
           .andExpect(jsonPath("$.fullName").value("Jane Doe"))
           .andExpect(jsonPath("$.avatarUrl").value("/portal/rest/v1/social/users/jane/avatar?lastModified=7"));
  }

  /**
   * A user whose profile gives no name and no avatar is served with the
   * username and the platform's avatar of that username.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aUserWithNoNameOrAvatarIsServedWithTheUsernameAndItsAvatar() throws Exception {
    Identity john = new Identity("organization", "john");
    john.setId("12");
    when(service.getUserIdentityByEmail("john@example.com")).thenReturn(john);

    mockMvc.perform(as("alice", get("/events/attendees/user").param("email", "john@example.com")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.fullName").value("john"))
           .andExpect(jsonPath("$.avatarUrl").value("/portal/rest/v1/social/users/john/avatar"));
  }

  /**
   * An address that resolves to no user to invite answers no content, never
   * stored by a cache either.
   *
   * @throws Exception when the request fails
   */
  @Test
  void anAddressOfNoUserIsAnsweredWithNoContent() throws Exception {
    mockMvc.perform(as("alice", get("/events/attendees/user").param("email", "someone@outside.org")))
           .andExpect(status().isNoContent())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"));

    verify(service).getUserIdentityByEmail("someone@outside.org");
  }

  /**
   * A user directory that cannot be read leaves the address unchecked: the
   * lookup answers that it is unavailable, never no content, which the drawer
   * would take for a person from outside the platform.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aDirectoryThatCannotBeReadAnswersUnavailable() throws Exception {
    when(service.getUserIdentityByEmail("jane@example.com")).thenThrow(new IllegalStateException("directory down"));

    mockMvc.perform(as("alice", get("/events/attendees/user").param("email", "jane@example.com")))
           .andExpect(status().isServiceUnavailable())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.message").value("agenda.attendees.lookupFailed"));
  }

  /**
   * A lookup with no address, or a blank one, is refused with its code before
   * the service is asked.
   *
   * @throws Exception when the request fails
   */
  @Test
  void aLookupWithNoAddressIsABadRequest() throws Exception {
    mockMvc.perform(as("alice", get("/events/attendees/user")))
           .andExpect(status().isBadRequest())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
           .andExpect(jsonPath("$.message").value("agenda.attendees.emailMandatory"));
    mockMvc.perform(as("alice", get("/events/attendees/user").param("email", " ")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("agenda.attendees.emailMandatory"));

    verify(service, never()).getUserIdentityByEmail(any());
  }

  private MockHttpServletRequestBuilder as(String username, MockHttpServletRequestBuilder builder) {
    return builder.with(request -> {
      request.setRemoteUser(username);
      return request;
    });
  }

}
