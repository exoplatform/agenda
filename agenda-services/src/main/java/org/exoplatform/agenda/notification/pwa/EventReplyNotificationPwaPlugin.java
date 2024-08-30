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
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_IS_CREATOR;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_RESPONSE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_TITLE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;

public class EventReplyNotificationPwaPlugin implements PwaNotificationPlugin {
  private static final Log           LOG                             = ExoLogger.getLogger(EventReplyNotificationPwaPlugin.class);


  private String                notificationId = "EventReplyNotificationPlugin";
  private static final String   TITLE_LABEL_KEY = "pwa.notification.EventReplyNotificationPwaPlugin.title";
  private ResourceBundleService resourceBundleService;

  public EventReplyNotificationPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String key = TITLE_LABEL_KEY;

    if ("true".equals(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_IS_CREATOR))) {
      key += ".creator";
    } else {
      key += ".participant";
    }
    key += ".response";

    String status = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_RESPONSE);
    switch (status) {
    case "ACCEPTED":
      key += ".accepted";
      break;
    case "DECLINED":
      key += ".declined";
      break;
    default:
      key+= ".mayBe";
    }

    String title = resourceBundleService.getSharedString(key, localeConfig.getLocale())
                                        .replace("{0}",notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_PARTICIPANT_NAME));

    notificationMessage.setTitle(title);
    notificationMessage.setBody(notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_TITLE));

    String url = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_URL).replace(CommonsUtils.getCurrentDomain(), "");
    notificationMessage.setUrl(url);
    return notificationMessage;
  }
}
