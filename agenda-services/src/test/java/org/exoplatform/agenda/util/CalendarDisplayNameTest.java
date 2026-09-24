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
package org.exoplatform.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ListResourceBundle;
import java.util.Locale;

import org.junit.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The name agenda notifications give the calendar of their event: a space
 * calendar by its space, a personal calendar by its stored name, else by the
 * agenda's own label in the recipient's language — never an absent space.
 */
public class CalendarDisplayNameTest {

  private static final String USER_OWNER_ID  = "1";

  private static final String SPACE_OWNER_ID = "2";

  @Test
  public void namesEachKindOfCalendar() {
    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(USER_OWNER_ID)).thenReturn(new Identity(OrganizationIdentityProvider.NAME, "john"));
    when(identityManager.getIdentity(SPACE_OWNER_ID)).thenReturn(new Identity(SpaceIdentityProvider.NAME, "marketing"));

    Space space = new Space();
    space.setDisplayName("Marketing");
    SpaceService spaceService = mock(SpaceService.class);
    when(spaceService.getSpaceByPrettyName("marketing")).thenReturn(space);

    ResourceBundleService resourceBundleService = mock(ResourceBundleService.class);
    when(resourceBundleService.getSharedResourceBundleNames()).thenReturn(new String[0]);
    when(resourceBundleService.getResourceBundle(any(String[].class), any(Locale.class))).thenAnswer(invocation -> {
      Locale locale = invocation.getArgument(1);
      return new ListResourceBundle() {
        @Override
        protected Object[][] getContents() {
          return new Object[][] { { NotificationUtils.PERSONAL_CALENDAR_LABEL_KEY,
              Locale.FRENCH.getLanguage().equals(locale.getLanguage()) ? "Mon calendrier" : "My calendar" } };
        }
      };
    });

    try (MockedStatic<ExoContainerContext> container = mockStatic(ExoContainerContext.class)) {
      container.when(() -> ExoContainerContext.getService(IdentityManager.class)).thenReturn(identityManager);
      container.when(() -> ExoContainerContext.getService(SpaceService.class)).thenReturn(spaceService);
      container.when(() -> ExoContainerContext.getService(ResourceBundleService.class)).thenReturn(resourceBundleService);

      assertEquals("a space calendar is named by its space",
                   "Marketing",
                   NotificationUtils.getCalendarDisplayName(notification(SPACE_OWNER_ID, null), Locale.ENGLISH));
      assertEquals("a personal calendar is named by the name its owner gave it",
                   "Travel",
                   NotificationUtils.getCalendarDisplayName(notification(USER_OWNER_ID, "Travel"), Locale.FRENCH));
      assertEquals("a personal calendar never named takes the agenda's label, in the recipient's language",
                   "Mon calendrier",
                   NotificationUtils.getCalendarDisplayName(notification(USER_OWNER_ID, null), Locale.FRENCH));
      assertEquals("My calendar",
                   NotificationUtils.getCalendarDisplayName(notification(USER_OWNER_ID, null), Locale.ENGLISH));
      assertEquals("an unknown owner names nothing",
                   "",
                   NotificationUtils.getCalendarDisplayName(notification("3", null), Locale.ENGLISH));
    }
  }

  private static NotificationInfo notification(String ownerId, String calendarName) {
    NotificationInfo notification = NotificationInfo.instance();
    notification.with(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID, ownerId);
    if (calendarName != null) {
      notification.with(NotificationUtils.STORED_PARAMETER_EVENT_CALENDAR_NAME, calendarName);
    }
    return notification;
  }
}
