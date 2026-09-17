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

import static org.exoplatform.agenda.util.NotificationUtils.AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_OWNER_NAME;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;

/**
 * The installed-app push of "a calendar was shared with you" (EXO-90357):
 * the owner's name in the title, the calendar's in the body, the agenda as
 * the link.
 */
public class CalendarSharedNotificationPwaPlugin implements PwaNotificationPlugin {

  private static final String         TITLE_LABEL_KEY = "pwa.notification.CalendarSharedNotificationPwaPlugin.title";

  private final ResourceBundleService resourceBundleService;

  /**
   * Builds the plugin.
   *
   * @param resourceBundleService reads the title in the recipient's language
   */
  public CalendarSharedNotificationPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getId() {
    return AGENDA_CALENDAR_SHARED_NOTIFICATION_PLUGIN;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    String ownerName = StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_OWNER_NAME));
    String title = resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale());
    notificationMessage.setTitle(StringUtils.defaultString(title, TITLE_LABEL_KEY).replace("{0}", ownerName));
    notificationMessage.setBody(StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_CALENDAR_NAME)));
    String url = StringUtils.defaultString(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL));
    notificationMessage.setUrl(url.replace(StringUtils.defaultString(CommonsUtils.getCurrentDomain()), ""));
    return notificationMessage;
  }

}
