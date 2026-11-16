/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.notification.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.AgendaNotificationPlugin;
import org.exoplatform.agenda.notification.provider.MailTemplateProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.ConferenceLinkSchemeTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

/**
 * Pins the web conference link the event mail template receives: the mail
 * writes it into the <code>href</code> of its join link as it receives it, so
 * a link running a script is not given to it, and any other link is given
 * escaped for an HTML attribute. The value is the one the notification
 * stored, which may predate the refusal of such links at save.
 */
public class ConferenceLinkInMailTest extends BaseAgendaEventTest {

  private static final ArgumentLiteral<String> EVENT_TITLE         = new ArgumentLiteral<>(String.class, "eventTitle");

  private static final String                  CONFERENCE_VARIABLE = "conference";

  @Test
  public void testScriptConferenceLinkIsNotGivenToTheMail() throws Exception {
    NotificationInfo notification = createdEventNotification();
    for (String url : ConferenceLinkSchemeTest.SCRIPT_URLS) {
      notification.with(NotificationUtils.STORED_PARAMETER_EVENT_CONFERENCE, url);

      TemplateContext templateContext = buildTemplateParameters(notification);

      assertFalse(url, templateContext.containsKey(CONFERENCE_VARIABLE));
    }
  }

  @Test
  public void testConferenceLinkIsEscapedForTheMailAttribute() throws Exception {
    NotificationInfo notification = createdEventNotification();
    notification.with(NotificationUtils.STORED_PARAMETER_EVENT_CONFERENCE, "https://meet.example.com/room?a=1&b=\"2\"<x>'");

    TemplateContext templateContext = buildTemplateParameters(notification);

    assertEquals("https://meet.example.com/room?a=1&amp;b=&quot;2&quot;&lt;x&gt;&#39;", templateContext.get(CONFERENCE_VARIABLE));
  }

  private TemplateContext buildTemplateParameters(NotificationInfo notification) {
    notification.setTo("testuser2");
    return NotificationUtils.buildTemplateParameters("testuser2",
                                                     spaceService,
                                                     agendaEventAttendeeService,
                                                     new MailTemplateProvider(container, channelInitParams()),
                                                     notification,
                                                     ZoneOffset.UTC);
  }

  private NotificationInfo createdEventNotification() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start, false);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser1Identity,
                                     testuser2Identity);

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

  private InitParams channelInitParams() {
    InitParams initParams = new InitParams();
    ValueParam channelId = new ValueParam();
    channelId.setName("channel-id");
    channelId.setValue("MAIL_CHANNEL");
    initParams.addParam(channelId);
    return initParams;
  }

}
