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
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.notification.plugin.EventParticipationConfirmedNotificationPlugin;
import org.exoplatform.agenda.notification.provider.MailTemplateProvider;
import org.exoplatform.agenda.notification.provider.MobilePushTemplateProvider;
import org.exoplatform.agenda.notification.provider.WebTemplateProvider;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;

public class ParticipationConfirmedTemplateBuilderTest extends BaseAgendaEventTest {

  /**
   * A guest without an account is told by mail, the channel the invitation
   * reached them through, and on no other channel.
   */
  @Test
  public void testAGuestIsToldByMailOnly() throws Exception {
    String guestEmail = "promoted.guest.channels@example.com";
    Identity guest = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, guestEmail);
    NotificationInfo notification = confirmation(singleEvent(guest).getId(), guest);
    notification.setTo(guestEmail);

    assertNull("web", messageOf(new WebTemplateProvider(container, channel("WEB_CHANNEL")), notification));
    assertNull("push", messageOf(new MobilePushTemplateProvider(container, channel("PUSH_CHANNEL")), notification));
    assertNotNull("mail", messageOf(new MailTemplateProvider(container, channel("MAIL_CHANNEL")), notification));
  }

  /**
   * A user is told on every channel.
   */
  @Test
  public void testAUserIsToldOnEveryChannel() throws Exception {
    NotificationInfo notification = confirmation(singleEvent(testuser2Identity).getId(), testuser2Identity);
    notification.setTo(testuser2Identity.getRemoteId());

    assertNotNull("web", messageOf(new WebTemplateProvider(container, channel("WEB_CHANNEL")), notification));
    assertNotNull("push", messageOf(new MobilePushTemplateProvider(container, channel("PUSH_CHANNEL")), notification));
    assertNotNull("mail", messageOf(new MailTemplateProvider(container, channel("MAIL_CHANNEL")), notification));
  }

  /**
   * The first date promoted is given in the recipient's zone: 09:00 UTC is
   * the day before in Honolulu.
   */
  @Test
  public void testATimedDateIsGivenInTheRecipientsZone() {
    NotificationInfo notification = storedDate("2030-01-14T09:00:00Z", false);

    TemplateContext templateContext = templateContextOf(notification, ZoneId.of("Pacific/Honolulu"));

    assertEquals(dayFormat(notification).format(LocalDate.of(2030, 1, 13)), templateContext.get("promotedDay"));
    assertEquals(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                  .withLocale(localeOf(notification))
                                  .format(ZonedDateTime.parse("2030-01-13T23:00:00-10:00")),
                 templateContext.get("promotedTime"));
  }

  /**
   * An all-day date is a calendar day, given as stored whatever the
   * recipient's zone, with no time.
   */
  @Test
  public void testAnAllDayDateIsGivenAsItsDay() {
    NotificationInfo notification = storedDate("2030-01-14", true);

    TemplateContext templateContext = templateContextOf(notification, ZoneId.of("Pacific/Honolulu"));

    assertEquals(dayFormat(notification).format(LocalDate.of(2030, 1, 14)), templateContext.get("promotedDay"));
    assertEquals("", templateContext.get("promotedTime"));
  }

  /**
   * A guest without an account is given no link to the event.
   */
  @Test
  public void testAGuestIsGivenNoLinkToTheEvent() {
    NotificationInfo notification = storedDate("2030-01-14", true);
    notification.with(NotificationUtils.STORED_PARAMETER_IS_GUEST, "true");

    TemplateContext templateContext = templateContextOf(notification, ZoneId.of("UTC"));

    assertEquals("", templateContext.get("eventURL"));
    assertEquals("", templateContext.get("webEventURL"));
    assertEquals(true, templateContext.get("isGuest"));
  }

  private NotificationInfo storedDate(String startDate, boolean allDay) {
    NotificationInfo notification = NotificationInfo.instance()
                                                    .key(NotificationUtils.PARTICIPATION_CONFIRMED_KEY)
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_ID, "1")
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_TITLE, "Weekly sync")
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_URL, "http://host/url")
                                                    .with(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL, "/url")
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID,
                                                          "2030-01-14T09:00:00Z")
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_START_DATE, startDate)
                                                    .with(NotificationUtils.STORED_PARAMETER_EVENT_ALL_DAY, String.valueOf(allDay))
                                                    .with(NotificationUtils.STORED_PARAMETER_OTHER_DATES, "2")
                                                    .with(NotificationUtils.STORED_PARAMETER_OPEN_ENDED, "false")
                                                    .with(NotificationUtils.STORED_PARAMETER_IS_GUEST, "false");
    notification.setTo(testuser2Identity.getRemoteId());
    return notification;
  }

  private TemplateContext templateContextOf(NotificationInfo notification, ZoneId zone) {
    return NotificationUtils.buildTemplateParticipationConfirmedParameters(new MailTemplateProvider(container,
                                                                                                    channel("MAIL_CHANNEL")),
                                                                           notification,
                                                                           zone);
  }

  private static Locale localeOf(NotificationInfo notification) {
    String language = NotificationPluginUtils.getLanguage(notification.getTo());
    return StringUtils.isBlank(language) ? Locale.ENGLISH : LocaleUtils.toLocale(language);
  }

  private static DateTimeFormatter dayFormat(NotificationInfo notification) {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(localeOf(notification));
  }

  private MessageInfo messageOf(TemplateProvider provider, NotificationInfo notification) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.setNotificationInfo(notification);
    return ((ParticipationConfirmedTemplateBuilder) provider.getTemplateBuilder()
                                                            .get(NotificationUtils.PARTICIPATION_CONFIRMED_KEY)).makeMessage(ctx);
  }

  private Event singleEvent(Identity attendee) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    return createEvent(event, Long.parseLong(testuser1Identity.getId()), attendee);
  }

  private NotificationInfo confirmation(long eventId, Identity identity) {
    EventAttendee promoted = new EventAttendee(0, eventId, Long.parseLong(identity.getId()), EventAttendeeResponse.ACCEPTED);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_PROMOTION,
                                                             new EventAttendeePromotion(promoted, 0, false));
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue(NotificationUtils.AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN);
    initParams.addParam(value);
    NotificationInfo notification = new EventParticipationConfirmedNotificationPlugin(initParams,
                                                                                      identityManager,
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
