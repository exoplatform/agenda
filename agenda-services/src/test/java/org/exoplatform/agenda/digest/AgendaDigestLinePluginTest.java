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
package org.exoplatform.agenda.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.commons.digest.model.DigestItem;
import io.meeds.commons.digest.model.DigestLine;
import io.meeds.commons.digest.plugin.DigestLineContext;

@ExtendWith(MockitoExtension.class)
class AgendaDigestLinePluginTest {

  /** A recipient in Tokyo reading English */
  private static final DigestLineContext CONTEXT = new DigestLineContext("ayoub", Locale.ENGLISH, ZoneId.of("Asia/Tokyo"));

  @Mock
  private AgendaEventService              agendaEventService;

  @Mock
  private IdentityManager                identityManager;

  private AgendaDigestLinePlugin         plugin;

  @BeforeEach
  void setUp() {
    InitParams params = new InitParams();
    ValuesParam pluginIds = new ValuesParam();
    pluginIds.setName("pluginIds");
    pluginIds.setValues(new ArrayList<>(List.of(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN, AgendaDigestLinePlugin.DATE_POLL_PLUGIN)));
    params.addParameter(pluginIds);
    // The event link needs the running platform: a plain marker here
    plugin = new AgendaDigestLinePlugin(params, agendaEventService, identityManager) {
      @Override
      protected String eventUrl(Event event) {
        return "event:" + event.getId();
      }
    };
    Identity john = new Identity(OrganizationIdentityProvider.NAME, "john");
    john.setId("15");
    Profile profile = new Profile(john);
    profile.setProperty(Profile.FULL_NAME, "John Smith");
    john.setProfile(profile);
    lenient().when(identityManager.getIdentity("15")).thenReturn(john);
  }

  @Test
  void testInvitationLineIsWrittenInTheRecipientTimezone() {
    Event event = new Event();
    event.setId(7);
    event.setSummary("Sprint review");
    // 10:00 in Paris is 17:00 in Tokyo
    event.setStart(ZonedDateTime.parse("2026-09-10T10:00:00+02:00[Europe/Paris]"));
    when(agendaEventService.getEventById(7)).thenReturn(event);

    DigestLine line = plugin.buildLine(item(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN, "eventId", "7", "modifierIdentityId", "15"),
                                       CONTEXT);
    assertNotNull(line);
    assertEquals("digest.line.EventAddedNotificationPlugin", line.getLabelKey());
    assertEquals(List.of("John Smith", "Sprint review", "Sep 10, 2026"), line.getArgs().subList(0, 3));
    // The JDK puts a narrow no-break space before PM
    assertEquals("5:00 PM", line.getArgs().get(3).replace(' ', ' '));
    assertEquals("event:7", line.getUrl());
  }

  @Test
  void testAllDayInvitationHasNoTime() {
    Event event = new Event();
    event.setId(7);
    event.setSummary("Company day");
    event.setAllDay(true);
    event.setStart(ZonedDateTime.parse("2026-09-10T00:00:00+02:00[Europe/Paris]"));
    when(agendaEventService.getEventById(7)).thenReturn(event);

    DigestLine line = plugin.buildLine(item(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN, "eventId", "7", "modifierIdentityId", "15"),
                                       CONTEXT);
    assertNotNull(line);
    assertEquals("digest.line.EventAddedNotificationPlugin.allDay", line.getLabelKey());
    assertEquals(List.of("John Smith", "Company day", "Sep 10, 2026"), line.getArgs());
  }

  @Test
  void testDatePollLine() {
    Event event = new Event();
    event.setId(8);
    event.setSummary("Team lunch");
    when(agendaEventService.getEventById(8)).thenReturn(event);

    DigestLine line = plugin.buildLine(item(AgendaDigestLinePlugin.DATE_POLL_PLUGIN, "eventId", "8", "modifierIdentityId", "15"), CONTEXT);
    assertNotNull(line);
    assertEquals("digest.line.DatePollNotificationPlugin", line.getLabelKey());
    assertEquals(List.of("Team lunch"), line.getArgs());
  }

  @Test
  void testDeletedEventGivesNoLine() {
    assertNull(plugin.buildLine(item(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN, "eventId", "404"), CONTEXT));
    assertNull(plugin.buildLine(item(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN, "eventId", "x"), CONTEXT));
    assertNull(plugin.buildLine(item(AgendaDigestLinePlugin.EVENT_ADDED_PLUGIN), CONTEXT));
  }

  @Test
  void testUnknownTypeGivesNoLine() {
    Event event = new Event();
    event.setId(7);
    when(agendaEventService.getEventById(7)).thenReturn(event);
    assertNull(plugin.buildLine(item("EventReminderNotificationPlugin", "eventId", "7"), CONTEXT));
  }

  private static DigestItem item(String pluginId, String... params) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < params.length; i += 2) {
      map.put(params[i], params[i + 1]);
    }
    return new DigestItem(1, "ayoub", pluginId, "agenda", Instant.now(), map);
  }

}
