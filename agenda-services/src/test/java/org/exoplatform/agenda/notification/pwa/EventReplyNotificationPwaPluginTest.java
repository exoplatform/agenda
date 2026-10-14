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

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_IS_CREATOR;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_RESPONSE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationMessage;

@ExtendWith(MockitoExtension.class)
class EventReplyNotificationPwaPluginTest {

  @Mock
  private ResourceBundleService           resourceBundleService;

  @Mock
  private LocaleConfig                    localeConfig;

  private EventReplyNotificationPwaPlugin plugin;

  @BeforeEach
  void setUp() {
    when(localeConfig.getLocale()).thenReturn(Locale.ENGLISH);
    // The key itself stands for its wording, so the title names the key read
    when(resourceBundleService.getSharedString(anyString(), any(Locale.class))).thenAnswer(invocation -> invocation.getArgument(0)
        + " | {0}");
    plugin = new EventReplyNotificationPwaPlugin(resourceBundleService);
  }

  /**
   * Each answer has its own sentence, a place on the waiting list included,
   * which read as "May be" before it had one. The sentence no longer says
   * whose invitation it was, so the creator and any other recipient read the
   * same one.
   */
  @ParameterizedTest
  @CsvSource({
      "ACCEPTED, true, accepted",
      "ACCEPTED, false, accepted",
      "DECLINED, true, declined",
      "TENTATIVE, false, mayBe",
      "WAITING, true, waiting",
      "WAITING, false, waiting",
  })
  void testEachAnswerIsToldWithItsOwnSentence(String response, String isCreator, String sentence) {
    NotificationInfo notification = NotificationInfo.instance()
                                                    .with(STORED_PARAMETER_EVENT_RESPONSE, response)
                                                    .with(STORED_PARAMETER_EVENT_IS_CREATOR, isCreator)
                                                    .with(STORED_PARAMETER_EVENT_PARTICIPANT_NAME, "Bob Smith")
                                                    .with(STORED_PARAMETER_EVENT_TITLE, "Team lunch")
                                                    .with(STORED_PARAMETER_EVENT_URL,
                                                          CommonsUtils.getCurrentDomain() + "/portal/dw/agenda?eventId=12");

    PwaNotificationMessage message = plugin.process(notification, localeConfig);

    assertEquals("pwa.notification.EventReplyNotificationPwaPlugin.title.reply." + sentence + " | Bob Smith", message.getTitle());
    assertEquals("Team lunch", message.getBody());
    assertEquals("/portal/dw/agenda?eventId=12", message.getUrl());
  }
}
