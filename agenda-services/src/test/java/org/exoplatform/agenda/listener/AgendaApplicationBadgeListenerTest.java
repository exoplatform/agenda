/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.agenda.listener;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.plugin.AgendaApplicationBadgePlugin;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.appcenter.service.ApplicationBadgeService;

/**
 * The listener is glue, so what matters here is exactly who it reaches: the
 * attendee expansion (a space attendee stands for all its members) and the
 * paths where the attendee rows are already gone by the time it runs.
 */
@ExtendWith(MockitoExtension.class)
class AgendaApplicationBadgeListenerTest {

  private static final long                  EVENT_ID         = 12L;

  private static final long                  USER_IDENTITY_ID = 5L;

  private static final long                  SPACE_IDENTITY_ID = 7L;

  @Mock
  private ApplicationBadgeService             applicationBadgeService;

  @Mock
  private AgendaEventAttendeeService          attendeeService;

  @Mock
  private IdentityManager                     identityManager;

  @Mock
  private SpaceService                        spaceService;

  @Mock
  private ListenerService                     listenerService;

  @InjectMocks
  private AgendaApplicationBadgeListener      listener;

  private AgendaEventModification             modification;

  @BeforeEach
  void setup() {
    modification = new AgendaEventModification(EVENT_ID, 1L, USER_IDENTITY_ID);
  }

  @Test
  void refreshesTheBadgeOfAUserAttendee() throws Exception {
    mockUserIdentity(USER_IDENTITY_ID, "testuser");
    when(attendeeService.getEventAttendees(EVENT_ID)).thenReturn(attendees(USER_IDENTITY_ID));

    listener.onEvent(new Event<>("any", modification, null));

    verify(applicationBadgeService).updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME, "testuser");
  }

  @Test
  void expandsASpaceAttendeeToItsMembers() throws Exception {
    mockSpaceIdentity(SPACE_IDENTITY_ID, "marketing");
    Space space = new Space();
    space.setMembers(new String[] { "first", "second" });
    when(spaceService.getSpaceByPrettyName("marketing")).thenReturn(space);
    when(attendeeService.getEventAttendees(EVENT_ID)).thenReturn(attendees(SPACE_IDENTITY_ID));

    listener.onEvent(new Event<>("any", modification, null));

    // An event invited to a space is pending for every member, since a user's
    // pending count is computed against their identity plus their spaces
    verify(applicationBadgeService).updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME, "first");
    verify(applicationBadgeService).updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME, "second");
  }

  @Test
  void keepsRefreshingTheOtherAttendeesWhenASpaceNoLongerResolves() throws Exception {
    mockSpaceIdentity(SPACE_IDENTITY_ID, "deleted-space");
    mockUserIdentity(USER_IDENTITY_ID, "testuser");
    // The listener is asynchronous: the space can disappear between the
    // broadcast and the moment it runs
    when(spaceService.getSpaceByPrettyName("deleted-space")).thenReturn(null);
    when(attendeeService.getEventAttendees(EVENT_ID)).thenReturn(attendees(SPACE_IDENTITY_ID, USER_IDENTITY_ID));

    listener.onEvent(new Event<>("any", modification, null));

    // The unresolvable space must not abort the whole stream
    verify(applicationBadgeService).updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME, "testuser");
  }

  @Test
  void usesTheAttendeeSnapshotCarriedByTheEventInsteadOfLookingItUp() throws Exception {
    mockUserIdentity(USER_IDENTITY_ID, "testuser");

    // A deletion removes the attendee rows before broadcasting, so the snapshot
    // taken beforehand is the only way to reach them
    listener.onEvent(new Event<>("any", modification, attendees(USER_IDENTITY_ID)));

    verify(applicationBadgeService).updateBadge(AgendaApplicationBadgePlugin.BADGE_NAME, "testuser");
    verify(attendeeService, never()).getEventAttendees(anyLong());
  }

  @Test
  void ignoresADisabledOrDeletedIdentity() throws Exception {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, "gone");
    identity.setId(String.valueOf(USER_IDENTITY_ID));
    identity.setEnable(true);
    identity.setDeleted(true);
    when(identityManager.getIdentity(eq(USER_IDENTITY_ID))).thenReturn(identity);
    when(attendeeService.getEventAttendees(EVENT_ID)).thenReturn(attendees(USER_IDENTITY_ID));

    listener.onEvent(new Event<>("any", modification, null));

    verify(applicationBadgeService, never()).updateBadge(anyString(), anyString());
  }

  private EventAttendeeList attendees(long... identityIds) {
    List<EventAttendee> list = java.util.Arrays.stream(identityIds).mapToObj(identityId -> {
      EventAttendee attendee = new EventAttendee();
      attendee.setIdentityId(identityId);
      return attendee;
    }).toList();
    return new EventAttendeeList(list);
  }

  private void mockUserIdentity(long identityId, String username) {
    mockIdentity(identityId, new Identity(OrganizationIdentityProvider.NAME, username));
  }

  private void mockSpaceIdentity(long identityId, String prettyName) {
    mockIdentity(identityId, new Identity(SpaceIdentityProvider.NAME, prettyName));
  }

  private void mockIdentity(long identityId, Identity identity) {
    identity.setId(String.valueOf(identityId));
    identity.setEnable(true);
    lenient().when(identityManager.getIdentity(eq(identityId))).thenReturn(identity);
  }

}
