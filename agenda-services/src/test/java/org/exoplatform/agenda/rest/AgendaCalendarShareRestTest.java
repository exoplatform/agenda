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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ChannelShares;
import org.exoplatform.agenda.model.ExternalShare;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaCalendarShareService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Drives the calendar share resource through Spring MVC's dispatcher
 * (EXO-90357): the paths, the bodies each answer carries, and the status
 * each service refusal maps to — not found, then forbidden, then bad
 * request.
 */
class AgendaCalendarShareRestTest {

  private static final long          CALENDAR = 20;

  private AgendaCalendarShareService service;

  private AgendaCalendarService      calendarService;

  private MockMvc                    mockMvc;

  /**
   * Builds the dispatcher over the resource, with Alice as a known user and
   * one calendar the owner shares.
   */
  @BeforeEach
  void setUp() {
    service = mock(AgendaCalendarShareService.class);
    calendarService = mock(AgendaCalendarService.class);
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity alice = new Identity("organization", "alice");
    alice.setId("3");
    alice.setEnable(true);
    Profile profile = new Profile(alice);
    profile.setProperty(Profile.FULL_NAME, "Alice Liddell");
    alice.setProfile(profile);
    when(identityManager.getIdentity("3")).thenReturn(alice);
    Identity owner = new Identity("organization", "owner");
    owner.setId("1");
    owner.setEnable(true);
    Profile ownerProfile = new Profile(owner);
    ownerProfile.setProperty(Profile.FULL_NAME, "Oscar Owner");
    owner.setProfile(ownerProfile);
    when(identityManager.getIdentity("1")).thenReturn(owner);
    Calendar calendar = new Calendar();
    calendar.setId(CALENDAR);
    calendar.setOwnerId(1);
    calendar.setName("Personal");
    calendar.setColor("#123456");
    when(calendarService.getCalendarById(CALENDAR)).thenReturn(calendar);
    mockMvc = MockMvcBuilders.standaloneSetup(new AgendaCalendarShareRest(service, calendarService, identityManager)).build();
  }

  /**
   * Sharing answers the record, naming the colleague, and is never cached;
   * a failed delivery leaves no trace on the answer.
   *
   * @throws Exception when the request fails
   */
  @Test
  void sharingAnswersTheRecord() throws Exception {
    CalendarShare share = share();
    share.setDeliveredTo(null);
    when(service.share(CALENDAR, "alice", CalendarShareLevel.VIEW, "owner")).thenReturn(share);

    mockMvc.perform(as("owner", post("/calendars/20/shares").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}")))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
           .andExpect(jsonPath("$.shareeIdentityId").value(3))
           .andExpect(jsonPath("$.username").value("alice"))
           .andExpect(jsonPath("$.displayName").value("Alice Liddell"))
           .andExpect(jsonPath("$.source").value("EXO"))
           .andExpect(jsonPath("$.deliveryWarning").doesNotExist())
           .andExpect(jsonPath("$.deliveredTo").doesNotExist())
           .andExpect(jsonPath("$.disabled").value(false));
  }

  /**
   * The refusals map in the contract's order: 404, then 403, then 400 with
   * the message code as body.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theRefusalsMapToNotFoundThenForbiddenThenBadRequest() throws Exception {
    when(service.share(99, "alice", CalendarShareLevel.VIEW, "owner")).thenThrow(new ObjectNotFoundException("gone"));
    when(service.share(CALENDAR, "alice", CalendarShareLevel.VIEW, "stranger")).thenThrow(new IllegalAccessException("not yours"));
    when(service.share(CALENDAR, "nobody", CalendarShareLevel.VIEW, "owner")).thenThrow(new IllegalArgumentException(AgendaCalendarShareService.SHAREE_UNKNOWN));

    mockMvc.perform(as("owner", post("/calendars/99/shares").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}")))
           .andExpect(status().isNotFound());
    mockMvc.perform(as("stranger", post("/calendars/20/shares").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}")))
           .andExpect(status().isForbidden());
    mockMvc.perform(as("owner", post("/calendars/20/shares").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nobody\"}")))
           .andExpect(status().isBadRequest())
           .andExpect(status().reason(AgendaCalendarShareService.SHAREE_UNKNOWN));
  }

  /**
   * A share request naming a level shares at that level, and the answer says
   * which level the record carries (EXO-90378).
   *
   * @throws Exception when the request fails
   */
  @Test
  void sharingCanNameALevelAndTheAnswerCarriesIt() throws Exception {
    CalendarShare share = share();
    share.setLevel(CalendarShareLevel.EDIT);
    when(service.share(CALENDAR, "alice", CalendarShareLevel.EDIT, "owner")).thenReturn(share);

    mockMvc.perform(as("owner",
                       post("/calendars/20/shares").contentType(MediaType.APPLICATION_JSON)
                                                   .content("{\"username\":\"alice\",\"access\":\"EDIT\"}")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.access").value("EDIT"));
  }

  /**
   * Levelling a colleague answers no content, and the level the request named
   * is the one the service is asked for (EXO-90378).
   *
   * @throws Exception when the request fails
   */
  @Test
  void levellingAColleagueAnswersNoContent() throws Exception {
    mockMvc.perform(as("owner",
                       put("/calendars/20/shares/3").contentType(MediaType.APPLICATION_JSON)
                                                    .content("{\"access\":\"EDIT\"}")))
           .andExpect(status().isNoContent());

    verify(service).setLevel(CALENDAR, 3, CalendarShareLevel.EDIT, "owner");
  }

  /**
   * A level request that names no level, or one this version does not know,
   * is a bad request — never a silent VIEW, which would read as a deliberate
   * downgrade (EXO-90378). The other refusals map 404 then 403, as sharing's
   * do.
   *
   * @throws Exception when the request fails
   */
  @Test
  void anUnnamedOrUnknownLevelIsABadRequest() throws Exception {
    mockMvc.perform(as("owner", put("/calendars/20/shares/3").contentType(MediaType.APPLICATION_JSON).content("{}")))
           .andExpect(status().isBadRequest())
           .andExpect(status().reason(AgendaCalendarShareService.LEVEL_MANDATORY));
    mockMvc.perform(as("owner",
                       put("/calendars/20/shares/3").contentType(MediaType.APPLICATION_JSON)
                                                    .content("{\"access\":\"MANAGE\"}")))
           .andExpect(status().isBadRequest())
           .andExpect(status().reason(AgendaCalendarShareService.LEVEL_MANDATORY));
    verify(service, never()).setLevel(anyLong(), anyLong(), any(), anyString());

    when(service.setLevel(eq(99L), anyLong(), any(), anyString())).thenThrow(new ObjectNotFoundException("gone"));
    when(service.setLevel(eq(CALENDAR), anyLong(), any(), eq("stranger"))).thenThrow(new IllegalAccessException("not yours"));

    mockMvc.perform(as("owner",
                       put("/calendars/99/shares/3").contentType(MediaType.APPLICATION_JSON)
                                                    .content("{\"access\":\"VIEW\"}")))
           .andExpect(status().isNotFound());
    mockMvc.perform(as("stranger",
                       put("/calendars/20/shares/3").contentType(MediaType.APPLICATION_JSON)
                                                    .content("{\"access\":\"VIEW\"}")))
           .andExpect(status().isForbidden());
  }

  /**
   * The owner's listing carries the eXo shares and the access held outside
   * eXo apart, the channels asked first so a grant they hold for a colleague
   * is recorded before the shares are read.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theListingCarriesExoAndExternalSharesApart() throws Exception {
    when(service.getShares(CALENDAR, "owner")).thenReturn(List.of(share()));
    when(service.getChannelShares(CALENDAR, "owner"))
                                                     .thenReturn(new ChannelShares(List.of(new ExternalShare("caldav:1",
                                                                                                             "grant-9",
                                                                                                             "OUTSIDE_EXO",
                                                                                                             0,
                                                                                                             "x@y.org",
                                                                                                             true,
                                                                                                             true)),
                                                                                   true));

    mockMvc.perform(as("owner", get("/calendars/20/shares")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.meetingCopies").value(true))
           .andExpect(jsonPath("$.shares[0].username").value("alice"))
           .andExpect(jsonPath("$.shares[0].deliveredTo").value("caldav:1"))
           .andExpect(jsonPath("$.externalShares[0].externalId").value("grant-9"))
           .andExpect(jsonPath("$.externalShares[0].kind").value("OUTSIDE_EXO"));
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(service);
    order.verify(service).getChannelShares(CALENDAR, "owner");
    order.verify(service).getShares(CALENDAR, "owner");
    // One ask, not two (EXO-90385): the drawer's warning rides on the same
    // answer as the external shares, so no channel is made to read twice
    verify(service, never()).holdsMeetingCopies(anyLong(), anyString());
    verify(service, never()).getExternalShares(anyLong(), anyString());
  }

  /**
   * Unsharing answers no content and names the colleague by identity; a
   * stranger is refused.
   *
   * @throws Exception when the request fails
   */
  @Test
  void unsharingAnswersNoContent() throws Exception {
    doThrow(new IllegalAccessException("not yours")).when(service).unshare(CALENDAR, 3, "stranger");

    mockMvc.perform(as("owner", delete("/calendars/20/shares/3"))).andExpect(status().isNoContent());
    verify(service).unshare(CALENDAR, 3, "owner");
    mockMvc.perform(as("stranger", delete("/calendars/20/shares/3"))).andExpect(status().isForbidden());
  }

  /**
   * The counts answer a map by calendar identifier.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theCountsAnswerAMapByCalendar() throws Exception {
    when(service.countShareesByCalendar("owner")).thenReturn(Map.of(CALENDAR, 2L));

    mockMvc.perform(as("owner", get("/calendars/share-counts")))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
           .andExpect(jsonPath("$.20").value(2));
  }

  /**
   * The sharee's listing names each calendar, its owner and its colour, and
   * says whether the sharee hid it and which channel carries it.
   *
   * @throws Exception when the request fails
   */
  @Test
  void theSharedWithMeListingNamesTheCalendarAndItsOwner() throws Exception {
    CalendarShare share = share();
    share.setHidden(true);
    when(service.getSharedWithMe("alice")).thenReturn(List.of(share));

    mockMvc.perform(as("alice", get("/calendars/shared-with-me")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].calendarId").value(20))
           .andExpect(jsonPath("$[0].name").value("Personal"))
           .andExpect(jsonPath("$[0].color").value("#123456"))
           .andExpect(jsonPath("$[0].ownerUsername").value("owner"))
           .andExpect(jsonPath("$[0].ownerDisplayName").value("Oscar Owner"))
           .andExpect(jsonPath("$[0].hidden").value(true))
           .andExpect(jsonPath("$[0].deliveredTo").value("caldav:1"))
           .andExpect(jsonPath("$[0].deliveryRef").value("/cal/alice/shared/"));
  }

  /**
   * Hiding takes a boolean and answers no content; a missing flag is a bad
   * request, an unknown share a not found.
   *
   * @throws Exception when the request fails
   */
  @Test
  void hidingTakesABooleanAndAnswersNoContent() throws Exception {
    doThrow(new ObjectNotFoundException(AgendaCalendarShareService.SHARE_NOT_FOUND)).when(service).setHidden(eq(99L), anyString(), eq(true));

    mockMvc.perform(as("alice", put("/calendars/shared-with-me/20/hidden").contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")))
           .andExpect(status().isNoContent());
    verify(service).setHidden(CALENDAR, "alice", true);
    mockMvc.perform(as("alice", put("/calendars/shared-with-me/20/hidden").contentType(MediaType.APPLICATION_JSON).content("{}")))
           .andExpect(status().isBadRequest());
    mockMvc.perform(as("alice", put("/calendars/shared-with-me/99/hidden").contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")))
           .andExpect(status().isNotFound());
    verify(service, org.mockito.Mockito.never()).setHidden(anyLong(), anyString(), eq(false));
  }

  /**
   * Removing a share held on a channel's server answers no content when the
   * channel removed it, and a conflict bearing the code when the channel
   * could not — a server refusal is neither a bad request nor a server error
   * of eXo's.
   *
   * @throws Exception when the request fails
   */
  @Test
  void removingAnExternalShareAnswersNoContentOrAConflictTheDrawerCanWord() throws Exception {
    doThrow(new IllegalStateException(AgendaCalendarShareService.SHARE_NOT_REMOVED)).when(service)
                                                                                     .removeExternalShare(CALENDAR, "caldav:1", "refused", "owner");
    doThrow(new ObjectNotFoundException(AgendaCalendarShareService.NO_CHANNEL)).when(service)
                                                                                .removeExternalShare(CALENDAR, "matrix:1", "grant-9", "owner");

    mockMvc.perform(as("owner", delete("/calendars/20/external-shares/caldav:1/grant-9")))
           .andExpect(status().isNoContent());
    verify(service).removeExternalShare(CALENDAR, "caldav:1", "grant-9", "owner");
    mockMvc.perform(as("owner", delete("/calendars/20/external-shares/caldav:1/refused")))
           .andExpect(status().isConflict())
           .andExpect(status().reason(AgendaCalendarShareService.SHARE_NOT_REMOVED));
    mockMvc.perform(as("owner", delete("/calendars/20/external-shares/matrix:1/grant-9")))
           .andExpect(status().isNotFound())
           .andExpect(status().reason(AgendaCalendarShareService.NO_CHANNEL));
  }

  /**
   * The share the owner made with Alice, carried by a CalDAV channel.
   *
   * @return the share
   */
  private static CalendarShare share() {
    return new CalendarShare(5, CALENDAR, 3, CalendarShareLevel.VIEW, 1, 1000, CalendarShareSource.EXO, "caldav:1", "/cal/alice/shared/", false);
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
