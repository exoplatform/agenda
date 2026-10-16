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

import static org.exoplatform.agenda.util.NotificationUtils.CAPACITY_REACHED_EVENT_ID;
import static org.exoplatform.agenda.util.NotificationUtils.EVENT_OCCURRENCE_ID;
import static org.exoplatform.agenda.util.NotificationUtils.getSpaceManagersByOwnerId;
import static org.exoplatform.agenda.util.NotificationUtils.storeCapacityReachedParameters;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * "Event capacity reached": tells the creator of an event and the managers of
 * its space that an answer filled its last seat (EXO-90320). It is sent once
 * per pool of seats: a series for the dates it carries, the row of a date for
 * that date, an event that is not recurring for itself. On a recurring event,
 * its links open the date that filled up, which the report names: a stored
 * date's own date, or the first date the series carries to fill.
 */
public class EventCapacityReachedNotificationPlugin extends BaseNotificationPlugin {

  private static final Log            LOG                             =
                                          ExoLogger.getLogger(EventCapacityReachedNotificationPlugin.class);

  private static final String         AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private final String                notificationId;

  private final IdentityManager       identityManager;

  private final SpaceService          spaceService;

  private final AgendaEventService    eventService;

  private final AgendaCalendarService calendarService;

  public EventCapacityReachedNotificationPlugin(InitParams initParams,
                                                IdentityManager identityManager,
                                                SpaceService spaceService,
                                                AgendaEventService eventService,
                                                AgendaCalendarService calendarService) {
    super(initParams);
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.eventService = eventService;
    this.calendarService = calendarService;
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
  public boolean isValid(NotificationContext ctx) {
    Long eventId = ctx.value(CAPACITY_REACHED_EVENT_ID);
    if (eventId == null || eventId <= 0) {
      LOG.warn("Notification type '{}' isn't valid because the event wasn't found", getId());
      return false;
    }
    return true;
  }

  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    long eventId = ctx.value(CAPACITY_REACHED_EVENT_ID);
    Event event = eventService.getEventById(eventId);
    if (event == null || event.getStatus() != EventStatus.CONFIRMED) {
      LOG.debug("Notification type '{}' isn't sent: event {} is gone or not confirmed", getId(), eventId);
      return null;
    }
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    if (calendar == null) {
      LOG.debug("Notification type '{}' isn't sent: the calendar of event {} is gone", getId(), eventId);
      return null;
    }
    Set<String> recipients = capacityReachedRecipients(event, calendar);
    if (recipients.isEmpty()) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    }
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    notification.to(new ArrayList<>(recipients));
    storeCapacityReachedParameters(notification, event, ctx.value(EVENT_OCCURRENCE_ID), calendar);
    return notification.end();
  }

  /**
   * Who hears that an event filled up: its creator, while their account
   * exists, and the managers of the space the calendar belongs to, each once.
   * Whoever filled the last seat is not left out: the notification is about
   * the event's seats, not about an answer.
   *
   * @param event the event whose pool of seats filled up
   * @param calendar its calendar
   * @return the recipients' usernames, the creator first
   */
  Set<String> capacityReachedRecipients(Event event, Calendar calendar) {
    Set<String> recipients = new LinkedHashSet<>();
    Identity creator = Utils.getIdentityById(identityManager, event.getCreatorId());
    if (creator != null && !creator.isDeleted() && OrganizationIdentityProvider.NAME.equals(creator.getProviderId())) {
      recipients.add(creator.getRemoteId());
    }
    recipients.addAll(getSpaceManagersByOwnerId(identityManager, spaceService, calendar.getOwnerId()));
    return recipients;
  }
}
