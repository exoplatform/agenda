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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the flag the calendar menus follow (EXO-90252, PO decision (b)): a
 * calendar read for a user tells whether they may publish it, apart from whether
 * they may edit it — a super-manager edits a space calendar but does not publish
 * it. It also checks that {@code CalendarPermission.clone()} keeps the flag:
 * that is a guard, not a production path — the REST entity serialises the
 * permissions as read, and nothing clones them today.
 */
class AgendaCalendarPublishAclTest {

  private static final long         SPACE    = 100;

  private static final long         CALENDAR = 20;

  private AgendaCalendarServiceImpl service;

  /**
   * Builds the calendar service over a space calendar, a real manager and a
   * super-manager.
   */
  @BeforeEach
  void setUp() {
    IdentityManager identityManager = mock(IdentityManager.class);
    SpaceService spaceService = mock(SpaceService.class);
    AgendaCalendarStorage storage = mock(AgendaCalendarStorage.class);
    InitParams initParams = mock(InitParams.class);
    ValuesParam colors = new ValuesParam();
    colors.setValues(new ArrayList<>(List.of("#1f77b4")));
    when(initParams.getValuesParam("defaultColors")).thenReturn(colors);

    user(identityManager, 3, "manager");
    user(identityManager, 6, "admin");
    Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId(String.valueOf(SPACE));
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(spaceIdentity);
    Space space = new Space();
    space.setPrettyName("team");
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    Set<String> managers = Set.of("manager");
    when(spaceService.canViewSpace(eq(space), anyString())).thenReturn(true);
    when(spaceService.isMember(eq(space), anyString())).thenAnswer(invocation -> managers.contains(invocation.getArgument(1)));
    when(spaceService.isManager(eq(space), anyString())).thenAnswer(invocation -> managers.contains(invocation.getArgument(1)));
    when(spaceService.canManageSpace(eq(space), anyString())).thenReturn(true);

    when(storage.getCalendarById(CALENDAR)).thenAnswer(invocation -> {
      Calendar calendar = new Calendar();
      calendar.setId(CALENDAR);
      calendar.setOwnerId(SPACE);
      calendar.setName("Team");
      return calendar;
    });
    service = new AgendaCalendarServiceImpl(storage, identityManager, spaceService, initParams);
  }

  /**
   * A real manager may edit and publish the space calendar.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void aRealManagerMayPublish() throws Exception {
    CalendarPermission acl = service.getCalendarById(CALENDAR, "manager").getAcl();

    assertTrue(acl.isCanEdit());
    assertTrue(acl.isCanPublish());
    assertTrue(acl.clone().isCanPublish(), "a copy keeps the flag (a guard: no production path clones the permissions today)");
  }

  /**
   * A super-manager who is not a manager of the space edits the calendar but may
   * not publish it.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void aSuperManagerEditsButMayNotPublish() throws Exception {
    CalendarPermission acl = service.getCalendarById(CALENDAR, "admin").getAcl();

    assertTrue(acl.isCanEdit());
    assertFalse(acl.isCanPublish());
    assertFalse(acl.clone().isCanPublish());
  }

  /**
   * An unsaved calendar instance carries the same flag.
   *
   * @throws Exception when the instance is refused
   */
  @Test
  void anUnsavedCalendarInstanceCarriesTheFlag() throws Exception {
    assertTrue(service.createCalendarInstance(SPACE, 3).getAcl().isCanPublish());
    assertFalse(service.createCalendarInstance(SPACE, 6).getAcl().isCanPublish());
  }

  /**
   * Registers a user identity.
   *
   * @param identityManager the mocked identity manager
   * @param id identity identifier
   * @param username user name
   */
  private void user(IdentityManager identityManager, long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
  }

}
