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
package org.exoplatform.agenda.notification.builder;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.MockedStatic;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.AgendaNotificationPlugin;
import org.exoplatform.agenda.notification.provider.MailTemplateProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.branding.BrandingService;

/**
 * The description of an event is user content printed in the invitation mail:
 * its black and white text follows the display mode of the recipient, whose
 * palette the template parameters bind.
 */
public class EventDescriptionContentColorsTest extends BaseAgendaEventTest {

  private static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");

  private static final String                  DESCRIPTION = "<p style=\"color:#000000\">agenda items</p>";

  @Test
  public void testTheBlackTextOfTheDescriptionIsLeftToTheRecipientsDarkMode() throws Exception {
    assertEquals("<p>agenda items</p>", descriptionFor("dark"));
  }

  @Test
  public void testTheDescriptionIsUntouchedForALightRecipient() throws Exception {
    assertEquals(DESCRIPTION, descriptionFor("light"));
  }

  @Test
  public void testTheBlackTextOfTheDescriptionInTheReplyMailFollowsTheRecipientsMode() throws Exception {
    assertEquals("<p>agenda items</p>", replyDescriptionFor("dark"));
    assertEquals(DESCRIPTION, replyDescriptionFor("light"));
  }

  private String replyDescriptionFor(String mode) throws Exception {
    NotificationInfo notification = createdEventNotification();
    notification.setTo("testuser2");
    BrandingService brandingService = mock(BrandingService.class);
    when(brandingService.getDisplayMode("testuser2")).thenReturn(mode);
    when(brandingService.getMailPalette("dark".equals(mode))).thenReturn(new HashMap<>(Map.of("surface", "#1E2328")));
    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class, CALLS_REAL_METHODS)) {
      commonsUtils.when(() -> CommonsUtils.getService(BrandingService.class)).thenReturn(brandingService);
      TemplateContext templateContext = NotificationUtils.buildTemplateReplyParameters(new MailTemplateProvider(container,
                                                                                                                mailChannel()),
                                                                                       notification,
                                                                                       ZoneOffset.UTC,
                                                                                       true);
      return (String) templateContext.get("eventDescription");
    }
  }

  private String descriptionFor(String mode) throws Exception {
    NotificationInfo notification = createdEventNotification();
    notification.setTo("testuser2");
    BrandingService brandingService = mock(BrandingService.class);
    when(brandingService.getDisplayMode("testuser2")).thenReturn(mode);
    when(brandingService.getMailPalette("dark".equals(mode))).thenReturn(new HashMap<>(Map.of("surface", "#1E2328")));
    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class, CALLS_REAL_METHODS)) {
      commonsUtils.when(() -> CommonsUtils.getService(BrandingService.class)).thenReturn(brandingService);
      TemplateContext templateContext = NotificationUtils.buildTemplateParameters("testuser2",
                                                                                 spaceService,
                                                                                 agendaEventAttendeeService,
                                                                                 new MailTemplateProvider(container,
                                                                                                          mailChannel()),
                                                                                 notification,
                                                                                 ZoneOffset.UTC);
      return (String) templateContext.get("eventDescription");
    }
  }

  private NotificationInfo createdEventNotification() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start, false);
    event.setDescription(DESCRIPTION);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser1Identity,
                                     testuser2Identity,
                                     spaceIdentity);
    InitParams initParams = new InitParams();
    ValueParam pluginKey = new ValueParam();
    pluginKey.setName("agenda.notification.plugin.key");
    pluginKey.setValue(NotificationUtils.AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    initParams.addParam(pluginKey);
    AgendaNotificationPlugin plugin = new AgendaNotificationPlugin(initParams,
                                                                  identityManager,
                                                                  agendaCalendarService,
                                                                  spaceService);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, createdEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId())
                                                                                       .getEventAttendees())
                                                     .append(EVENT_TITLE, createdEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             AgendaEventModificationType.ADDED.name());
    return plugin.makeNotification(ctx);
  }

  private InitParams mailChannel() {
    InitParams initParams = new InitParams();
    ValueParam channelId = new ValueParam();
    channelId.setName("channel-id");
    channelId.setValue("MAIL_CHANNEL");
    initParams.addParam(channelId);
    return initParams;
  }

}
