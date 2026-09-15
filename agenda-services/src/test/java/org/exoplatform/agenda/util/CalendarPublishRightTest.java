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
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins who may publish a calendar (EXO-90252, PO decision (b)): the owner of a
 * personal calendar; for a space calendar a real manager — a member holding the
 * manager role — and never a super-manager who is not one, whom social's
 * {@code canManageSpace} admits.
 */
class CalendarPublishRightTest {

  private static final long OWNER         = 1;

  private static final long MANAGER       = 3;

  private static final long MEMBER        = 5;

  private static final long SUPER_MANAGER = 6;

  private static final long ROLE_ONLY     = 7;

  private static final long SPACE         = 100;

  private IdentityManager   identityManager;

  private SpaceService      spaceService;

  /**
   * Builds a user owner, a space and its people.
   */
  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    user(OWNER, "owner");
    user(MANAGER, "manager");
    user(MEMBER, "member");
    user(SUPER_MANAGER, "admin");
    user(ROLE_ONLY, "roleonly");
    Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId(String.valueOf(SPACE));
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(spaceIdentity);
    Space space = new Space();
    space.setPrettyName("team");
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    Set<String> members = Set.of("manager", "member");
    Set<String> managers = Set.of("manager", "roleonly");
    when(spaceService.isMember(eq(space), anyString())).thenAnswer(invocation -> members.contains(invocation.getArgument(1)));
    when(spaceService.isManager(eq(space), anyString())).thenAnswer(invocation -> managers.contains(invocation.getArgument(1)));
    when(spaceService.canManageSpace(eq(space), anyString())).thenAnswer(invocation -> {
      String username = invocation.getArgument(1);
      return members.contains(username) && managers.contains(username) || "admin".equals(username);
    });
    when(spaceService.isSuperManager(eq(space), anyString())).thenAnswer(invocation -> "admin".equals(invocation.getArgument(1)));
  }

  /**
   * A personal calendar: its owner only.
   */
  @Test
  void aPersonalCalendarIsPublishedByItsOwnerOnly() {
    assertTrue(Utils.canPublishCalendar(identityManager, spaceService, OWNER, OWNER));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, OWNER, MANAGER));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, OWNER, SUPER_MANAGER));
  }

  /**
   * A space calendar: a real manager, not a member, not someone holding the role
   * without membership, and not a super-manager — although social lets that one
   * manage the space.
   */
  @Test
  void aSpaceCalendarIsPublishedByARealManagerOnly() {
    assertTrue(Utils.canPublishCalendar(identityManager, spaceService, SPACE, MANAGER));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, SPACE, MEMBER));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, SPACE, ROLE_ONLY));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, SPACE, SUPER_MANAGER));
    assertTrue(Utils.canEditCalendar(identityManager, spaceService, SPACE, SUPER_MANAGER),
               "the super-manager still edits the calendar: publishing is the narrower right");
  }

  /**
   * An owner or a user that does not resolve publishes nothing, and does not
   * throw.
   */
  @Test
  void unknownIdentitiesPublishNothing() {
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, 999, MANAGER));
    assertFalse(Utils.canPublishCalendar(identityManager, spaceService, SPACE, 999));
  }

  /**
   * Registers a user identity.
   *
   * @param id identity identifier
   * @param username user name
   */
  private void user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
  }

}
