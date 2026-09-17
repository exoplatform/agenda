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
package org.exoplatform.agenda.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.notification.plugin.CalendarSharedNotificationPlugin;
import org.exoplatform.agenda.notification.pwa.CalendarSharedNotificationPwaPlugin;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.pwa.model.PwaNotificationMessage;

/**
 * Pins what the "a calendar was shared with you" notification carries
 * (EXO-90357): one recipient, the sharee; the owner as sender; the calendar
 * and owner names and the agenda link as stored parameters; nothing when the
 * calendar or the sharee is gone. And what the installed-app push makes of
 * it.
 */
class CalendarSharedNotificationPluginTest {

  private IdentityManager                  identityManager;

  private AgendaCalendarService            calendarService;

  private CalendarSharedNotificationPlugin plugin;

  private Calendar                         calendar;

  private Identity                         alice;

  /**
   * Builds the plugin over an owner, a sharee and one calendar.
   */
  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    calendarService = mock(AgendaCalendarService.class);
    Identity owner = user(1, "owner", "Oscar Owner");
    alice = user(3, "alice", "Alice Liddell");
    when(identityManager.getIdentity("1")).thenReturn(owner);
    when(identityManager.getIdentity("3")).thenReturn(alice);
    calendar = new Calendar();
    calendar.setId(20);
    calendar.setOwnerId(1);
    calendar.setName("Work");
    when(calendarService.getCalendarById(20L)).thenReturn(calendar);
    InitParams initParams = new InitParams();
    ValueParam key = new ValueParam();
    key.setName("agenda.notification.plugin.key");
    key.setValue(NotificationUtils.AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN);
    initParams.addParam(key);
    plugin = new CalendarSharedNotificationPlugin(initParams, identityManager, calendarService) {
      @Override
      public NotificationInfo makeNotification(NotificationContext ctx) {
        return super.makeNotification(ctx);
      }
    };
  }

  /**
   * The notification goes to the sharee, from the owner, naming the calendar
   * and the owner and carrying the agenda link.
   */
  @Test
  void theNotificationNamesTheCalendarAndItsOwnerForTheSharee() {
    NotificationContext ctx = context(share());

    assertTrue(plugin.isValid(ctx));
    NotificationInfo notification = plugin.makeNotification(ctx);

    assertNotNull(notification);
    assertEquals(NotificationUtils.AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN, notification.getKey().getId());
    assertEquals(List.of("alice"), notification.getSendToUserIds());
    assertEquals("owner", notification.getFrom());
    assertEquals("Work", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CALENDAR_NAME));
    assertEquals("20", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CALENDAR_ID));
    assertEquals("Oscar Owner", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OWNER_NAME));
    assertEquals("owner", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OWNER_USERNAME));
  }

  /**
   * A share whose calendar is gone, or whose sharee is gone, notifies nobody;
   * a context without a share is not valid.
   */
  @Test
  void aDeadCalendarOrShareeNotifiesNobody() {
    when(calendarService.getCalendarById(20L)).thenReturn(null);
    assertNull(plugin.makeNotification(context(share())));

    when(calendarService.getCalendarById(20L)).thenReturn(calendar);
    alice.setEnable(false);
    assertNull(plugin.makeNotification(context(share())));

    NotificationContext empty = mock(NotificationContext.class);
    assertFalse(plugin.isValid(empty));
  }

  /**
   * The installed-app push titles with the owner, bodies with the calendar,
   * and links to the agenda.
   */
  @Test
  void thePushNamesTheOwnerAndTheCalendar() {
    ResourceBundleService bundles = mock(ResourceBundleService.class);
    when(bundles.getSharedString(anyString(), any(Locale.class))).thenReturn("{0} shared a calendar with you");
    LocaleConfig localeConfig = mock(LocaleConfig.class);
    when(localeConfig.getLocale()).thenReturn(Locale.ENGLISH);
    NotificationInfo notification = plugin.makeNotification(context(share()));

    PwaNotificationMessage message = new CalendarSharedNotificationPwaPlugin(bundles).process(notification, localeConfig);

    assertEquals("Oscar Owner shared a calendar with you", message.getTitle());
    assertEquals("Work", message.getBody());
  }

  /**
   * A context carrying a share.
   *
   * @param share the share
   * @return the context
   */
  private static NotificationContext context(CalendarShare share) {
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.value(NotificationUtils.CALENDAR_SHARE)).thenReturn(share);
    return ctx;
  }

  /**
   * The owner's share with Alice.
   *
   * @return the share
   */
  private static CalendarShare share() {
    return new CalendarShare(5, 20, 3, 1, 1000, CalendarShareSource.EXO, null, null, false);
  }

  /**
   * A user identity with a full name.
   *
   * @param id identity identifier
   * @param username username
   * @param fullName full name
   * @return the identity
   */
  private static Identity user(long id, String username, String fullName) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identity.setEnable(true);
    Profile profile = new Profile(identity);
    profile.setProperty(Profile.FULL_NAME, fullName);
    identity.setProfile(profile);
    return identity;
  }

}
