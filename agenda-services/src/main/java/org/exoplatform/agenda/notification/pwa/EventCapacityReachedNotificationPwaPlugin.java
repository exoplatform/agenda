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

import static org.exoplatform.agenda.util.NotificationUtils.AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;

/**
 * The push of "Event capacity reached": "Your event has reached its capacity
 * limit", the event's title, and the link opening the event, on a recurring
 * event at the date that filled up.
 */
public class EventCapacityReachedNotificationPwaPlugin implements PwaNotificationPlugin {

  private static final String         TITLE_LABEL_KEY = "pwa.notification.EventCapacityReachedNotificationPwaPlugin.title";

  private final ResourceBundleService resourceBundleService;

  public EventCapacityReachedNotificationPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public String getId() {
    return AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    notificationMessage.setTitle(resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale()));
    notificationMessage.setBody(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));
    notificationMessage.setUrl(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL)
                                           .replace(CommonsUtils.getCurrentDomain(), ""));
    return notificationMessage;
  }
}
