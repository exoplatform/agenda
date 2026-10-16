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
package org.exoplatform.agenda.notification.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.EventCapacityReachedNotificationPlugin;
import org.exoplatform.agenda.notification.provider.MailTemplateProvider;
import org.exoplatform.agenda.notification.provider.MobilePushTemplateProvider;
import org.exoplatform.agenda.notification.provider.WebTemplateProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

public class CapacityReachedTemplateBuilderTest extends BaseAgendaEventTest {

  /**
   * The creator is told on every channel; the mail and the web name the
   * event in their subject, the mobile push carries the event's link as its
   * subject. The bodies are pinned by the templates test: the test container
   * resolves no war: template.
   */
  @Test
  public void testTheCreatorIsToldOnEveryChannel() throws Exception {
    Event event = spaceEvent();
    NotificationInfo notification = capacityReached(event.getId());
    notification.setTo(testuser1Identity.getRemoteId());

    MessageInfo web = messageOf(new WebTemplateProvider(container, channel("WEB_CHANNEL")), notification);
    MessageInfo push = messageOf(new MobilePushTemplateProvider(container, channel("PUSH_CHANNEL")), notification);
    MessageInfo mail = messageOf(new MailTemplateProvider(container, channel("MAIL_CHANNEL")), notification);

    assertNotNull("web", web);
    assertNotNull("push", push);
    assertNotNull("mail", mail);
    assertTrue(web.getSubject(), web.getSubject().endsWith(":" + event.getSummary()));
    assertTrue(mail.getSubject(), mail.getSubject().endsWith(":" + event.getSummary()));
    assertEquals(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL), push.getSubject());
  }

  /**
   * The templates are given the event and its links as stored.
   */
  @Test
  public void testTheTemplatesAreGivenTheEventAndItsLinks() throws Exception {
    Event event = spaceEvent();
    NotificationInfo notification = capacityReached(event.getId());
    notification.setTo(testuser1Identity.getRemoteId());

    TemplateContext templateContext = NotificationUtils.buildTemplateCapacityReachedParameters(new MailTemplateProvider(container,
                                                                                                                        channel("MAIL_CHANNEL")),
                                                                                               notification);

    assertEquals(String.valueOf(event.getId()), templateContext.get("eventId"));
    assertEquals(event.getSummary(), templateContext.get("eventTitle"));
    assertEquals(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL), templateContext.get("eventURL"));
    assertEquals(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL),
                 templateContext.get("webEventURL"));
  }

  private MessageInfo messageOf(TemplateProvider provider, NotificationInfo notification) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.setNotificationInfo(notification);
    return ((CapacityReachedTemplateBuilder) provider.getTemplateBuilder()
                                                     .get(NotificationUtils.CAPACITY_REACHED_KEY)).makeMessage(ctx);
  }

  private Event spaceEvent() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(spaceCalendar.getId());
    return createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity);
  }

  private NotificationInfo capacityReached(long eventId) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance().append(NotificationUtils.CAPACITY_REACHED_EVENT_ID, eventId);
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue(NotificationUtils.AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN);
    initParams.addParam(value);
    NotificationInfo notification = new EventCapacityReachedNotificationPlugin(initParams,
                                                                               identityManager,
                                                                               spaceService,
                                                                               agendaEventService,
                                                                               agendaCalendarService).makeNotification(ctx);
    assertNotNull(notification);
    return notification;
  }

  private static InitParams channel(String channelId) {
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName(TemplateProvider.CHANNEL_ID_KEY);
    value.setValue(channelId);
    initParams.addParam(value);
    return initParams;
  }
}
