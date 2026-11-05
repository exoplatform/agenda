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
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * The lookup of an address against directories the test container does not
 * run: the platform's own stores an address as given and looks it up by
 * equality, as the database collation compares, case-sensitive on some; a
 * looser one finds more than the address. Only the address itself resolves,
 * and the directory is never asked for a pattern.
 */
class AgendaEventAttendeeUserLookupTest {

  private static final long             CALLER_ID = 99L;

  private UserHandler                    userHandler;

  private IdentityManager                identityManager;

  private MockedStatic<ExoContainerContext> container;

  private AgendaEventAttendeeServiceImpl attendeeService;

  @BeforeEach
  void setUp() {
    userHandler = mock(UserHandler.class);
    OrganizationService organizationService = mock(OrganizationService.class);
    when(organizationService.getUserHandler()).thenReturn(userHandler);
    ProfilePropertySetting emailSetting = new ProfilePropertySetting();
    emailSetting.setId(1L);
    emailSetting.setPropertyName(Profile.EMAIL);
    emailSetting.setActive(true);
    emailSetting.setVisible(true);
    ProfilePropertyService profilePropertyService = mock(ProfilePropertyService.class);
    when(profilePropertyService.getProfileSettingByName(Profile.EMAIL)).thenReturn(emailSetting);
    container = Mockito.mockStatic(ExoContainerContext.class);
    container.when(() -> ExoContainerContext.getService(OrganizationService.class)).thenReturn(organizationService);
    container.when(() -> ExoContainerContext.getService(ProfilePropertyService.class)).thenReturn(profilePropertyService);
    identityManager = mock(IdentityManager.class);
    // The lookups are made for an internal user
    Identity caller = mock(Identity.class);
    when(identityManager.getIdentity(String.valueOf(CALLER_ID))).thenReturn(caller);
    attendeeService = new AgendaEventAttendeeServiceImpl(mock(AgendaEventAttendeeStorage.class),
                                                         mock(AgendaEventStorage.class),
                                                         mock(AgendaCalendarService.class),
                                                         mock(ListenerService.class),
                                                         identityManager,
                                                         mock(SpaceService.class),
                                                         mock(CodecInitializer.class));
  }

  @AfterEach
  void tearDown() {
    container.close();
  }

  /**
   * A directory comparing case, as on PostgreSQL: an address typed in capitals
   * resolves to the one stored in lower case.
   */
  @Test
  void anAddressTypedInCapitalsResolvesToItsLowerCaseOwner() throws Exception {
    found("Jane.Doe@Example.com");
    found("jane.doe@example.com", user("jane", "jane.doe@example.com"));
    Identity jane = identity("jane", "21");

    assertEquals(jane, attendeeService.getUserIdentityByEmail("Jane.Doe@Example.com", CALLER_ID));
  }

  /**
   * A looser directory, as the in-memory one of tests: only the address itself
   * resolves among those it finds.
   */
  @Test
  void onlyTheAddressItselfResolvesAmongThoseTheDirectoryFinds() throws Exception {
    found("jane@example.com", user("jane2", "jane@example.com.fr"), user("jane", "JANE@example.com"));
    Identity jane = identity("jane", "22");
    identity("jane2", "23");

    assertEquals(jane, attendeeService.getUserIdentityByEmail("jane@example.com", CALLER_ID));
  }

  @Test
  void twoUsersOfOneAddressResolveToNobody() throws Exception {
    found("twin@example.com", user("twin1", "Twin@example.com"), user("twin2", "twin@Example.com"));
    identity("twin1", "24");
    identity("twin2", "25");

    assertNull(attendeeService.getUserIdentityByEmail("twin@example.com", CALLER_ID));
  }

  /**
   * The directory reads a star as a pattern even in a lookup by equality, and
   * reads every user a pattern matches: a text that is not an address, or
   * holds a star, is never handed to it.
   */
  @Test
  void aTextThatIsNotAnAddressOrHoldsAStarIsNeverHandedToTheDirectory() throws Exception {
    for (String text : new String[] { "*", "*@example.com", "jane*@example.com", "jane@example.com*", "jane", "jane@",
        "@example.com", "jane doe@example.com", "jane@@example.com" }) {
      assertNull(attendeeService.getUserIdentityByEmail(text, CALLER_ID), text);
    }
    Mockito.verify(userHandler, Mockito.never()).findUsersByQuery(any(), any());
  }

  /**
   * The directory is only ever asked for an address by equality, never by a
   * pattern, whose grammar is the database's: an address holding an
   * underscore, a percent sign or brackets resolves to its owner as any other.
   */
  @Test
  void anAddressHoldingAPatternCharacterOfTheDatabaseResolvesByEquality() throws Exception {
    found("john_doe@example.com", user("john", "john_doe@example.com"));
    found("50%off@example.com", user("promo", "50%off@example.com"));
    found("[team]@example.com", user("team", "[team]@example.com"));
    Identity john = identity("john", "31");
    Identity promo = identity("promo", "32");
    Identity team = identity("team", "33");

    assertEquals(john, attendeeService.getUserIdentityByEmail("john_doe@example.com", CALLER_ID));
    assertEquals(promo, attendeeService.getUserIdentityByEmail("50%off@example.com", CALLER_ID));
    assertEquals(team, attendeeService.getUserIdentityByEmail("[team]@example.com", CALLER_ID));
    Mockito.verify(userHandler, Mockito.never())
           .findUsersByQuery(argThat(query -> query != null && query.getEmail() != null && query.getEmail().contains("*")),
                             any());
  }

  /**
   * A directory that cannot be read leaves the address unchecked: neither a
   * user nor a person from outside the platform, it is refused, never taken
   * for a guest. The directory's message names the address, which the
   * refusal neither says nor carries: every caller logs it.
   */
  @Test
  void anAddressTheDirectoryCannotReadIsRefusedUnchecked() throws Exception {
    when(userHandler.findUsersByQuery(any(), any())).thenThrow(new Exception("Cannot find user by unique attribute: attrValue=jane@example.com"));

    IllegalStateException refusal = assertThrows(IllegalStateException.class,
                                                 () -> attendeeService.getUserIdentityByEmail("jane@example.com", CALLER_ID));
    assertNull(refusal.getCause());
    assertFalse(refusal.getMessage().contains("jane"), refusal.getMessage());
  }

  /**
   * An external user is told no owner, and the directory is not asked, as
   * the platform's people search tells them no user beyond their
   * connections; nor is a caller who is no known identity. An internal user
   * typing the same address is told its owner.
   * Mutant: the caller not read.
   */
  @Test
  void anExternalCallerIsToldNoOwner() throws Exception {
    found("jane@example.com", user("jane", "jane@example.com"));
    Identity jane = identity("jane", "24");
    Identity external = mock(Identity.class);
    when(external.isExternal()).thenReturn(true);
    when(identityManager.getIdentity("98")).thenReturn(external);

    assertNull(attendeeService.getUserIdentityByEmail("jane@example.com", 98L));
    assertNull(attendeeService.getUserIdentityByEmail("jane@example.com", 97L));
    Mockito.verify(userHandler, Mockito.never()).findUsersByQuery(any(), any());
    assertEquals(jane, attendeeService.getUserIdentityByEmail("jane@example.com", CALLER_ID));
  }

  private void found(String queried, User... users) throws Exception {
    @SuppressWarnings("unchecked")
    ListAccess<User> listAccess = mock(ListAccess.class);
    when(listAccess.getSize()).thenReturn(users.length);
    when(listAccess.load(eq(0), anyInt())).thenAnswer(invocation -> Arrays.copyOf(users, (int) invocation.getArgument(1)));
    when(userHandler.findUsersByQuery(argThat(query -> query != null && queried.equals(query.getEmail())),
                                      eq(UserStatus.ENABLED))).thenReturn(listAccess);
  }

  private static User user(String username, String email) {
    User user = mock(User.class);
    when(user.getUserName()).thenReturn(username);
    when(user.getEmail()).thenReturn(email);
    return user;
  }

  private Identity identity(String username, String id) {
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn(id);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
    return identity;
  }

}
