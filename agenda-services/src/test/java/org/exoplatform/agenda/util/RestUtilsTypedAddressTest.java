/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.rest.model.EventAttendeeEntity;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

/**
 * An address typed to invite someone that the platform recognises is the
 * user whose address it is (eXIP 7.3.0.21, US06): the participants of a write
 * are resolved before they are read, and a guest read back from the server
 * stays one.
 */
class RestUtilsTypedAddressTest {

  private static final String        ADDRESS = "Jane.Doe@Example.com";

  private static final long          CALLER_ID = 3L;

  private IdentityManager            identityManager;

  private AgendaEventAttendeeService attendeeService;

  @BeforeEach
  void setUp() {
    identityManager = Mockito.mock(IdentityManager.class);
    attendeeService = Mockito.mock(AgendaEventAttendeeService.class);
    Identity jane = Mockito.mock(Identity.class);
    when(jane.getId()).thenReturn("11");
    when(jane.getProviderId()).thenReturn(OrganizationIdentityProvider.NAME);
    when(jane.getRemoteId()).thenReturn("jane");
    when(attendeeService.getUserIdentityByEmail(ADDRESS, CALLER_ID)).thenReturn(jane);
  }

  /**
   * An address the user directory cannot check fails the write, rather than
   * invite a platform user as a guest from outside the platform.
   */
  @Test
  void anAddressTheDirectoryCannotCheckFailsTheWrite() {
    when(attendeeService.getUserIdentityByEmail("john@example.com", CALLER_ID)).thenThrow(new IllegalStateException("directory down"));
    List<EventAttendeeEntity> attendees = list(attendee("john@example.com", AgendaGuestUserIdentityProvider.NAME, "john@example.com"));

    assertThrows(IllegalStateException.class, () -> RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID));
  }

  @Test
  void anAddressTypedThatThePlatformRecognisesIsItsUser() {
    List<EventAttendeeEntity> attendees = list(attendee(ADDRESS.toLowerCase(), AgendaGuestUserIdentityProvider.NAME, ADDRESS));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    assertEquals(1, attendees.size());
    IdentityEntity identity = attendees.get(0).getIdentity();
    assertEquals("11", identity.getId());
    assertEquals(OrganizationIdentityProvider.NAME, identity.getProviderId());
    assertEquals("jane", identity.getRemoteId());
  }

  @Test
  void aGuestReadBackFromTheServerStaysAGuest() {
    Identity guest = Mockito.mock(Identity.class);
    when(identityManager.getIdentity("9")).thenReturn(guest);
    List<EventAttendeeEntity> attendees = list(attendee("9", AgendaGuestUserIdentityProvider.NAME, ADDRESS));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    IdentityEntity identity = attendees.get(0).getIdentity();
    assertEquals("9", identity.getId());
    assertEquals(AgendaGuestUserIdentityProvider.NAME, identity.getProviderId());
    assertEquals(ADDRESS, identity.getRemoteId());
  }

  @Test
  void anAddressThePlatformDoesNotRecogniseStaysAGuest() {
    List<EventAttendeeEntity> attendees = list(attendee(null, AgendaGuestUserIdentityProvider.NAME, "someone@outside.org"));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    assertEquals(AgendaGuestUserIdentityProvider.NAME, attendees.get(0).getIdentity().getProviderId());
    assertEquals("someone@outside.org", attendees.get(0).getIdentity().getRemoteId());
  }

  @Test
  void aUserPickedAndTypedIsInvitedOnce() {
    List<EventAttendeeEntity> attendees = list(attendee("organization:jane", OrganizationIdentityProvider.NAME, "jane"),
                                               attendee(ADDRESS, AgendaGuestUserIdentityProvider.NAME, ADDRESS));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    assertEquals(1, attendees.size());
    assertEquals("jane", attendees.get(0).getIdentity().getRemoteId());
  }

  @Test
  void anAddressTypedTwiceIsInvitedOnce() {
    List<EventAttendeeEntity> attendees = list(attendee(ADDRESS, AgendaGuestUserIdentityProvider.NAME, ADDRESS),
                                               attendee(ADDRESS, AgendaGuestUserIdentityProvider.NAME, ADDRESS));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    assertEquals(1, attendees.size());
    assertEquals("11", attendees.get(0).getIdentity().getId());
  }

  @Test
  void aUserGivenByItsIdAloneIsNotInvitedTwice() {
    List<EventAttendeeEntity> attendees = list(attendee("11", null, null),
                                               attendee(ADDRESS, AgendaGuestUserIdentityProvider.NAME, ADDRESS));

    RestUtils.resolveTypedAddresses(attendees, identityManager, attendeeService, CALLER_ID);

    assertEquals(1, attendees.size());
    assertEquals("11", attendees.get(0).getIdentity().getId());
  }

  private static List<EventAttendeeEntity> list(EventAttendeeEntity... attendees) {
    return new ArrayList<>(List.of(attendees));
  }

  private static EventAttendeeEntity attendee(String id, String providerId, String remoteId) {
    IdentityEntity identity = new IdentityEntity();
    identity.setId(id);
    identity.setProviderId(providerId);
    identity.setRemoteId(remoteId);
    return new EventAttendeeEntity(0, identity, null);
  }

}
