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

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarEditorChange;
import org.exoplatform.agenda.notification.plugin.CalendarEditedNotificationPlugin;
import org.exoplatform.agenda.notification.pwa.CalendarEditedNotificationPwaPlugin;
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
 * Pins what the "changes in your calendar" notification carries (EXO-90378):
 * one recipient, the calendar's owner; the colleague as sender; the calendar's
 * name, the colleague's, what they did and the event's summary as stored
 * parameters — captured at the change, so a deletion reads like the other two;
 * nothing when the calendar or the owner is gone, and nothing at all when the
 * owner is the one who changed it. And what the installed-app push makes of it.
 */
class CalendarEditedNotificationPluginTest {

  private IdentityManager                  identityManager;

  private AgendaCalendarService            calendarService;

  private CalendarEditedNotificationPlugin plugin;

  private Calendar                         calendar;

  /**
   * Builds the plugin over an owner, an editor and one calendar.
   */
  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    calendarService = mock(AgendaCalendarService.class);
    when(identityManager.getIdentity("1")).thenReturn(user(1, "owner", "Oscar Owner"));
    when(identityManager.getIdentity("3")).thenReturn(user(3, "alice", "Alice Liddell"));
    calendar = new Calendar();
    calendar.setId(20);
    calendar.setOwnerId(1);
    calendar.setName("Work");
    when(calendarService.getCalendarById(20L)).thenReturn(calendar);
    InitParams params = new InitParams();
    ValueParam key = new ValueParam();
    key.setName("agenda.notification.plugin.key");
    key.setValue(NotificationUtils.AGENDA_CALENDAR_EDITED_NOTIFICATION_PLUGIN);
    params.addParameter(key);
    plugin = new CalendarEditedNotificationPlugin(params, identityManager, calendarService);
  }

  /**
   * The notification goes to the owner, from the colleague, and carries what
   * the three templates read.
   */
  @Test
  void theOwnerIsToldWhatTheColleagueDid() {
    NotificationInfo notification = plugin.makeNotification(context(change(CalendarEditorChange.Kind.ADDED, "Planned by alice")));

    assertNotNull(notification);
    assertEquals(List.of("owner"), notification.getSendToUserIds());
    assertEquals("alice", notification.getFrom());
    assertEquals("Alice Liddell", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_MODIFIER_NAME));
    assertEquals("Work", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CALENDAR_NAME));
    assertEquals("20", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CALENDAR_ID));
    assertEquals("ADDED", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CHANGE_KIND));
    assertEquals("Planned by alice",
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CHANGED_EVENT_SUMMARY));
  }

  /**
   * A deletion carries the summary the event had when it was deleted: it was
   * captured at the change, because there is nothing left to read afterwards.
   */
  @Test
  void aDeletionStillNamesTheEvent() {
    NotificationInfo notification = plugin.makeNotification(context(change(CalendarEditorChange.Kind.REMOVED, "Dentist")));

    assertEquals("REMOVED", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CHANGE_KIND));
    assertEquals("Dentist", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_CHANGED_EVENT_SUMMARY));
  }

  /**
   * Nothing is valid without a change, without a calendar, without a
   * colleague — or when the owner is the one who changed it, which is not news
   * to them.
   */
  @Test
  void theGuardsRefuseWhatCannotBeSent() {
    assertFalse(plugin.isValid(context(null)));
    assertFalse(plugin.isValid(context(new CalendarEditorChange(0, 1, 3, 9, "x", CalendarEditorChange.Kind.ADDED))));
    assertFalse(plugin.isValid(context(new CalendarEditorChange(20, 0, 3, 9, "x", CalendarEditorChange.Kind.ADDED))));
    assertFalse(plugin.isValid(context(new CalendarEditorChange(20, 1, 0, 9, "x", CalendarEditorChange.Kind.ADDED))));
    assertFalse(plugin.isValid(context(new CalendarEditorChange(20, 1, 1, 9, "x", CalendarEditorChange.Kind.ADDED))),
                "a change the owner made in their own calendar is not news to them");
    assertTrue(plugin.isValid(context(change(CalendarEditorChange.Kind.CHANGED, "x"))));
  }

  /**
   * A calendar that is gone, or an owner who is, has no notification.
   */
  @Test
  void aGoneCalendarOrOwnerHasNoNotification() {
    calendar.setDeleted(true);
    assertNull(plugin.makeNotification(context(change(CalendarEditorChange.Kind.CHANGED, "x"))));

    calendar.setDeleted(false);
    when(identityManager.getIdentity("1")).thenReturn(null);
    assertNull(plugin.makeNotification(context(change(CalendarEditorChange.Kind.CHANGED, "x"))));
  }

  /**
   * The installed-app push names the colleague in its title and the calendar
   * in its body.
   */
  @Test
  void theInstalledAppPushNamesTheColleagueAndTheCalendar() {
    ResourceBundleService bundles = mock(ResourceBundleService.class);
    when(bundles.getSharedString(anyString(), any())).thenReturn("{0} changed something in your calendar");
    LocaleConfig localeConfig = mock(LocaleConfig.class);
    when(localeConfig.getLocale()).thenReturn(Locale.ENGLISH);
    CalendarEditedNotificationPwaPlugin pwa = new CalendarEditedNotificationPwaPlugin(bundles);
    NotificationInfo notification = plugin.makeNotification(context(change(CalendarEditorChange.Kind.CHANGED, "Dentist")));

    PwaNotificationMessage message = pwa.process(notification, localeConfig);

    assertEquals(NotificationUtils.AGENDA_CALENDAR_EDITED_NOTIFICATION_PLUGIN, pwa.getId());
    assertEquals("Alice Liddell changed something in your calendar", message.getTitle());
    assertEquals("Work", message.getBody());
  }

  /**
   * A change of the fixture's shape.
   *
   * @param kind what the colleague did
   * @param summary the event's summary at the change
   * @return the change
   */
  private static CalendarEditorChange change(CalendarEditorChange.Kind kind, String summary) {
    return new CalendarEditorChange(20, 1, 3, 9, summary, kind);
  }

  /**
   * A notification context carrying a change.
   *
   * @param change the change, may be null
   * @return the context
   */
  private static NotificationContext context(CalendarEditorChange change) {
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.value(NotificationUtils.CALENDAR_EDITOR_CHANGE)).thenReturn(change);
    return ctx;
  }

  /**
   * An enabled user identity.
   *
   * @param id identity identifier
   * @param username the login
   * @param fullName the full name
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
