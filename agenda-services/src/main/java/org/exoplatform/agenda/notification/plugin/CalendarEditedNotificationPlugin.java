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

import static org.exoplatform.agenda.util.NotificationUtils.CALENDAR_EDITOR_CHANGE;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_ID;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CALENDAR_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CHANGED_EVENT_SUMMARY;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_CHANGE_KIND;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_URL;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_MODIFIER_NAME;
import static org.exoplatform.agenda.util.NotificationUtils.getAgendaURL;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarEditorChange;
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
 * Tells the owner of a personal calendar that a colleague they shared it with
 * for editing wrote in it (EXO-90378), through the platform's notification
 * framework: on site, by mail and as a push, following the owner's own
 * notification settings like every other agenda notification.
 * <p>
 * One recipient, the owner; the sender is the colleague. The change is decided
 * by the event service, which is the one place that knows by what right a
 * writer wrote — this plugin only renders what it was handed, and reads
 * nothing back: a deleted event has nothing left to read, and the three kinds
 * of change must read alike.
 * <p>
 * Not {@code AgendaNotificationPlugin}, whose recipients are the event's
 * attendees and whose templates speak to invitees: the owner of a shared
 * calendar is neither.
 */
public class CalendarEditedNotificationPlugin extends BaseNotificationPlugin {

  private static final String         AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private static final Log            LOG                             =
                                          ExoLogger.getLogger(CalendarEditedNotificationPlugin.class);

  private final String                notificationId;

  private final IdentityManager       identityManager;

  private final AgendaCalendarService calendarService;

  /**
   * Builds the plugin.
   *
   * @param initParams the Kernel parameters, carrying the plugin key
   * @param identityManager resolves the owner and the colleague
   * @param calendarService names the calendar
   */
  public CalendarEditedNotificationPlugin(InitParams initParams,
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
    CalendarEditorChange change = ctx.value(CALENDAR_EDITOR_CHANGE);
    if (change == null || change.getCalendarId() <= 0 || change.getOwnerIdentityId() <= 0
        || change.getModifierIdentityId() <= 0) {
      LOG.warn("Notification type '{}' isn't valid because the change wasn't found", getId());
      return false;
    }
    // A change the owner made in their own calendar is not news to them; the
    // service does not raise one, and this is the second guard
    return change.getOwnerIdentityId() != change.getModifierIdentityId();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    CalendarEditorChange change = ctx.value(CALENDAR_EDITOR_CHANGE);
    Calendar calendar = calendarService.getCalendarById(change.getCalendarId());
    Identity owner = identityManager.getIdentity(String.valueOf(change.getOwnerIdentityId()));
    if (calendar == null || calendar.isDeleted() || !isUser(owner) || !owner.isEnable()) {
      LOG.debug("Notification type '{}' has no recipient: calendar {} or owner {} is gone",
                getId(),
                change.getCalendarId(),
                change.getOwnerIdentityId());
      return null;
    }
    Identity modifier = identityManager.getIdentity(String.valueOf(change.getModifierIdentityId()));
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    notification.to(Collections.singletonList(owner.getRemoteId()));
    if (isUser(modifier)) {
      notification.setFrom(modifier.getRemoteId());
      notification.with(STORED_PARAMETER_MODIFIER_NAME, fullName(modifier));
    }
    notification.with(STORED_PARAMETER_CALENDAR_ID, String.valueOf(calendar.getId()));
    notification.with(STORED_PARAMETER_CALENDAR_NAME,
                      StringUtils.isNotBlank(calendar.getName()) ? calendar.getName() : calendar.getTitle());
    notification.with(STORED_PARAMETER_CHANGE_KIND,
                      (change.getKind() == null ? CalendarEditorChange.Kind.CHANGED : change.getKind()).name());
    notification.with(STORED_PARAMETER_CHANGED_EVENT_SUMMARY, StringUtils.defaultString(change.getEventSummary()));
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
      LOG.debug("The agenda link could not be built for the calendar-edited notification", e);
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
