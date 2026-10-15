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
package org.exoplatform.agenda.notification.pwa;

import static org.exoplatform.agenda.util.NotificationUtils.AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationMessage;

@ExtendWith(MockitoExtension.class)
class EventParticipationConfirmedNotificationPwaPluginTest {

  @Mock
  private ResourceBundleService                            resourceBundleService;

  @Mock
  private LocaleConfig                                     localeConfig;

  private EventParticipationConfirmedNotificationPwaPlugin plugin;

  @BeforeEach
  void setUp() {
    when(localeConfig.getLocale()).thenReturn(Locale.ENGLISH);
    // The key itself stands for its wording, so the title names the key read
    when(resourceBundleService.getSharedString(anyString(), any(Locale.class))).thenAnswer(invocation -> invocation.getArgument(0));
    plugin = new EventParticipationConfirmedNotificationPwaPlugin(resourceBundleService);
  }

  /**
   * The push says the participation is confirmed, names the event, and opens
   * the date promoted first, on the current site.
   */
  @Test
  void testThePushNamesTheEventAndOpensTheDatePromotedFirst() {
    NotificationInfo notification = NotificationInfo.instance()
                                                    .with(STORED_PARAMETER_EVENT_TITLE, "Weekly sync")
                                                    .with(STORED_PARAMETER_EVENT_URL,
                                                          CommonsUtils.getCurrentDomain()
                                                              + "/portal/dw/agenda?parentId=12&occurrenceId=2030-01-14T09:00:00Z");

    PwaNotificationMessage message = plugin.process(notification, localeConfig);

    assertEquals(AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN, plugin.getId());
    assertEquals("pwa.notification.EventParticipationConfirmedNotificationPwaPlugin.title", message.getTitle());
    assertEquals("Weekly sync", message.getBody());
    assertEquals("/portal/dw/agenda?parentId=12&occurrenceId=2030-01-14T09:00:00Z", message.getUrl());
  }
}
