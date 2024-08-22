/*
 * Copyright (C) 2024 eXo Platform SAS.
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
package org.exoplatform.agenda.notification.pwa;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.space.spi.SpaceService;

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;

public class EventReminderNotificationPwaPlugin implements PwaNotificationPlugin {
  private static final Log      LOG                             = ExoLogger.getLogger(EventReminderNotificationPwaPlugin.class);

  private String                notificationId = "EventReminderNotificationPlugin";
  private static final String   TITLE_LABEL_KEY = "pwa.notification.EventReminderNotificationPwaPlugin.title";

  private ResourceBundleService resourceBundleService;
  private SpaceService spaceService;

  public EventReminderNotificationPwaPlugin(ResourceBundleService resourceBundleService, SpaceService spaceService) {
    this.spaceService = spaceService;
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public SpaceService getSpaceService() {
    return this.spaceService;
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String key = TITLE_LABEL_KEY;
    String spaceId = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_OWNER_ID);
    String title = resourceBundleService.getSharedString(key, localeConfig.getLocale())
                                        .replace("{0}",getSpaceName(spaceId));

    notificationMessage.setTitle(title);
    notificationMessage.setBody(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));

    String url = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL).replace(CommonsUtils.getCurrentDomain(), "");
    notificationMessage.setUrl(url);
    return notificationMessage;
  }

}
