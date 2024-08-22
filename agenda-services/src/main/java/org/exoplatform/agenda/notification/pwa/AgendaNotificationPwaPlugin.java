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
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.space.spi.SpaceService;

import static org.exoplatform.agenda.util.NotificationUtils.STORED_EVENT_MODIFICATION_TYPE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_STATUS;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;


public class AgendaNotificationPwaPlugin implements PwaNotificationPlugin {
  private static final String   AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";
  private static final String   TITLE_LABEL_KEY = "pwa.notification.AgendaNotificationPwaPlugin.title";

  private static final Log      LOG                             = ExoLogger.getLogger(AgendaNotificationPwaPlugin.class);

  private String                notificationId;

  private ResourceBundleService resourceBundleService;

  public AgendaNotificationPwaPlugin(InitParams initParams,
                                  ResourceBundleService resourceBundleService, SpaceService spaceService) {
    this.resourceBundleService = resourceBundleService;
    ValueParam notificationIdParam = initParams.getValueParam(AGENDA_NOTIFICATION_PLUGIN_NAME);
    if (notificationIdParam == null || StringUtils.isBlank(notificationIdParam.getValue())) {
      throw new IllegalStateException("'agenda.notification.plugin.key' parameter is mandatory");
    }
    this.notificationId = notificationIdParam.getValue();
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String key = TITLE_LABEL_KEY;
    String type = notification.getValueOwnerParameter(STORED_EVENT_MODIFICATION_TYPE);
    String eventStatus = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_STATUS);
    switch (type) {
      case "ADDED":
        key += ".created";
        break;
      case "DATES_UPDATED":
        if (eventStatus.equals("CONFIRMED")) {
          key += ".dates.updated";
        } else if (eventStatus.equals("TENTATIVE")){
          key += ".datePoll.dates.updated";
        } else {
          key += ".canceled";
        }
        break;
      case "UPDATED":
        if (eventStatus.equals("TENTATIVE")) {
          key += ".datePoll.updated";
        } else {
          key += ".updated";
        }
        break;
      case "SWITCHED_EVENT_TO_DATE_POLL":
        key += ".switchedToDatePoll";
        break;
      case "SWITCHED_DATE_POLL_TO_EVENT":
        key += ".switchedToEvent";
        break;
      case "DELETED":
        if (eventStatus.equals("TENTATIVE")) {
          key += ".datePoll.updated";
        } else {
          key += ".canceled";
        }
        break;
      default:
        key += ".canceled";
        break;
    }
    String title = resourceBundleService.getSharedString(key, localeConfig.getLocale())
                                        .replace("{0}",notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));

    notificationMessage.setTitle(title);
    notificationMessage.setBody(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));

    String url = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL).replace(CommonsUtils.getCurrentDomain(), "");
    notificationMessage.setUrl(url);
    return notificationMessage;
  }
}
