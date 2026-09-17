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
package org.exoplatform.agenda.notification.plugin;

import static org.exoplatform.agenda.util.NotificationUtils.CALENDAR_SHARE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_ID;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_OWNER_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_OWNER_USERNAME;
import static org.exoplatform.agenda.util.NotificationUtils.getAgendaURL;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Tells a colleague that a calendar was shared with them (EXO-90357), through
 * the platform's notification framework: on site, by mail and as a push,
 * following the colleague's own notification settings like every other agenda
 * notification.
 * <p>
 * One recipient, the sharee; the sender is the owner. The stored parameters
 * name the calendar and its owner and carry the link to the agenda, where the
 * calendar now sits under "Shared with me"; the templates read nothing else.
 */
public class CalendarSharedNotificationPlugin extends BaseNotificationPlugin {

  private static final String   AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private static final Log      LOG                             = ExoLogger.getLogger(CalendarSharedNotificationPlugin.class);

  private final String          notificationId;

  private final IdentityManager identityManager;

  private final AgendaCalendarService calendarService;

  /**
   * Builds the plugin.
   *
   * @param initParams the Kernel parameters, carrying the plugin key
   * @param identityManager resolves the owner and the sharee
   * @param calendarService names the shared calendar
   */
  public CalendarSharedNotificationPlugin(InitParams initParams,
                                          IdentityManager identityManager,
                                          AgendaCalendarService calendarService) {
    super(initParams);
    this.identityManager = identityManager;
    this.calendarService = calendarService;
    ValueParam notificationIdParam = initParams.getValueParam(AGENDA_NOTIFICATION_PLUGIN_NAME);
    if (notificationIdParam == null || StringUtils.isBlank(notificationIdParam.getValue())) {
      throw new IllegalStateException("'agenda.notification.plugin.key' parameter is mandatory");
    }
    this.notificationId = notificationIdParam.getValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getId() {
    return notificationId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isValid(NotificationContext ctx) {
    CalendarShare share = ctx.value(CALENDAR_SHARE);
    if (share == null || share.getCalendarId() <= 0 || share.getShareeIdentityId() <= 0) {
      LOG.warn("Notification type '{}' isn't valid because the share wasn't found", getId());
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    CalendarShare share = ctx.value(CALENDAR_SHARE);
    Calendar calendar = calendarService.getCalendarById(share.getCalendarId());
    Identity sharee = identityManager.getIdentity(String.valueOf(share.getShareeIdentityId()));
    if (calendar == null || calendar.isDeleted() || !isUser(sharee) || !sharee.isEnable()) {
      LOG.debug("Notification type '{}' has no recipient: calendar {} or sharee {} is gone",
                getId(),
                share.getCalendarId(),
                share.getShareeIdentityId());
      return null;
    }
    Identity owner = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    notification.to(Collections.singletonList(sharee.getRemoteId()));
    if (isUser(owner)) {
      notification.setFrom(owner.getRemoteId());
      notification.with(STORED_PARAMETER_OWNER_USERNAME, owner.getRemoteId());
      notification.with(STORED_PARAMETER_OWNER_NAME, fullName(owner));
    }
    notification.with(STORED_PARAMETER_CALENDAR_ID, String.valueOf(calendar.getId()));
    notification.with(STORED_PARAMETER_CALENDAR_NAME, StringUtils.isNotBlank(calendar.getName()) ? calendar.getName() : calendar.getTitle());
    notification.with(STORED_PARAMETER_EVENT_URL, agendaUrl());
    return notification.end();
  }

  /**
   * The link to the agenda, empty when the portal cannot say — a notification
   * without its link is still worth sending.
   *
   * @return the link, or an empty string
   */
  private String agendaUrl() {
    try {
      return StringUtils.defaultString(getAgendaURL());
    } catch (RuntimeException e) {
      LOG.debug("The agenda link could not be built for the calendar-shared notification", e);
      return "";
    }
  }

  /**
   * Whether an identity is a user of this deployment.
   *
   * @param identity the identity, may be null
   * @return true for a user
   */
  private static boolean isUser(Identity identity) {
    return identity != null && !identity.isDeleted() && OrganizationIdentityProvider.NAME.equals(identity.getProviderId());
  }

  /**
   * A user's full name, else their username.
   *
   * @param identity the user
   * @return the name
   */
  private static String fullName(Identity identity) {
    Profile profile = identity.getProfile();
    String fullName = profile == null ? null : profile.getFullName();
    return StringUtils.isNotBlank(fullName) ? fullName : identity.getRemoteId();
  }

}
