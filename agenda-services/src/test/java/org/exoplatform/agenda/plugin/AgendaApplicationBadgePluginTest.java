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
package org.exoplatform.agenda.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.appcenter.service.ApplicationBadgePluginRegistry;

/**
 * The plugin must report exactly what the in-app pending-invitation reminder
 * shows, and must never keep Agenda from starting when App Center is absent.
 */
@ExtendWith(MockitoExtension.class)
class AgendaApplicationBadgePluginTest {

  private static final String                  USERNAME = "testuser";

  @Mock
  private ApplicationBadgePluginRegistry       registry;

  @Mock
  private AgendaEventService                   agendaEventService;

  @Mock
  private IdentityManager                      identityManager;

  @InjectMocks
  private AgendaApplicationBadgePlugin         plugin;

  @Test
  void countSumsPendingEventsAndDatePolls() throws Exception {
    mockIdentity();
    when(agendaEventService.countPendingEvents(any(), anyLong())).thenReturn(2L);
    when(agendaEventService.countEventDatePolls(any(), anyLong())).thenReturn(3L);

    // Counting only pending events would silently drop date polls, which the
    // functional specification requires ("event or datepoll")
    assertEquals(5L, plugin.countBadge(USERNAME));
  }

  @Test
  void countReturnsZeroForAnUnknownUser() {
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(null);

    assertEquals(0L, plugin.countBadge(USERNAME));
  }

  @Test
  void isNotSelfCachedSoAppCenterOwnsTheCaching() {
    assertFalse(plugin.isSelfCached());
  }

  @Test
  void registersItselfWhenTheRegistryIsPresent() {
    plugin.init();

    verify(registry).addPlugin(plugin);
  }

  @Test
  void startsWithoutTheApplicationCenterRegistry() {
    ReflectionTestUtils.setField(plugin, "applicationBadgePluginRegistry", null);

    // The badge is a nicety: a missing registry must not fail Agenda's context
    assertDoesNotThrow(() -> plugin.init());
  }

  @Test
  void declaresItsPortletBindings() {
    ReflectionTestUtils.setField(plugin, "portletNames", List.of("agenda/Agenda", "agenda/AgendaTimeline"));

    assertEquals(List.of("agenda/Agenda", "agenda/AgendaTimeline"), plugin.getPortletNames());
  }

  private void mockIdentity() {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, USERNAME);
    identity.setId("1");
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(identity);
  }

}
