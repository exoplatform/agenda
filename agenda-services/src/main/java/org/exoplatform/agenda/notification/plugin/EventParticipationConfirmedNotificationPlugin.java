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

import static org.exoplatform.agenda.util.NotificationUtils.EVENT_PROMOTION;
import static org.exoplatform.agenda.util.NotificationUtils.storeParticipationConfirmedParameters;

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
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

/**
 * "Participation confirmation": tells an attendee waiting for a seat that
 * they got one, whatever freed it (EXO-90315). It goes to the promoted
 * attendee only: a user through their account, a guest without one through
 * the address the invitation went to. On a recurring event, it names the
 * first date promoted, which its links open, and counts the other dates
 * promoted.
 */
public class EventParticipationConfirmedNotificationPlugin extends BaseNotificationPlugin {

  private static final Log            LOG                             =
                                          ExoLogger.getLogger(EventParticipationConfirmedNotificationPlugin.class);

  private static final String         AGENDA_NOTIFICATION_PLUGIN_NAME = "agenda.notification.plugin.key";

  private final String                notificationId;

  private final IdentityManager       identityManager;

  private final AgendaEventService    eventService;

  private final AgendaCalendarService calendarService;

  public EventParticipationConfirmedNotificationPlugin(InitParams initParams,
                                                       IdentityManager identityManager,
                                                       AgendaEventService eventService,
                                                       AgendaCalendarService calendarService) {
    super(initParams);
    this.identityManager = identityManager;
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
    EventAttendeePromotion promotion = ctx.value(EVENT_PROMOTION);
    if (promotion == null || promotion.attendee() == null || promotion.attendee().getEventId() <= 0) {
      LOG.warn("Notification type '{}' isn't valid because the promotion wasn't found", getId());
      return false;
    }
    return true;
  }

  @Override
  public NotificationInfo makeNotification(NotificationContext ctx) {
    EventAttendeePromotion promotion = ctx.value(EVENT_PROMOTION);
    EventAttendee attendee = promotion.attendee();
    Event event = eventService.getEventById(attendee.getEventId());
    if (event == null || event.getStatus() != EventStatus.CONFIRMED) {
      LOG.debug("Notification type '{}' isn't sent: event {} is gone or not confirmed", getId(), attendee.getEventId());
      return null;
    }
    Identity identity = Utils.getIdentityById(identityManager, attendee.getIdentityId());
    boolean guest = identity != null && AgendaGuestUserIdentityProvider.NAME.equals(identity.getProviderId());
    if (identity == null || identity.isDeleted()
        || !(guest || OrganizationIdentityProvider.NAME.equals(identity.getProviderId()))) {
      LOG.debug("Notification type '{}' doesn't have a recipient", getId());
      return null;
    }
    ZonedDateTime occurrenceId = dateOf(event, attendee);
    Event date;
    try {
      date = event.getRecurrence() == null || occurrenceId == null ? event
                                                                   : eventService.getEventOccurrence(event.getId(),
                                                                                                     occurrenceId,
                                                                                                     event.getTimeZoneId(),
                                                                                                     attendee.getIdentityId());
    } catch (IllegalAccessException e) {
      LOG.debug("Notification type '{}' isn't sent: the attendee may not see event {}", getId(), event.getId());
      return null;
    }
    if (date == null) {
      return null;
    }
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    NotificationInfo notification = NotificationInfo.instance();
    notification.key(getId());
    notification.to(List.of(identity.getRemoteId()));
    storeParticipationConfirmedParameters(notification, event, date, occurrenceId, calendar, promotion, guest);
    return notification.end();
  }

  /**
   * @return the first date promoted on a recurring event: the date the row of
   *         a series is reported from, or the date of the row of one of its
   *         dates; null on an event that is not recurring
   */
  private static ZonedDateTime dateOf(Event event, EventAttendee attendee) {
    if (event.getRecurrence() != null) {
      return attendee.getFromOccurrenceId();
    }
    return event.getParentId() > 0 && event.getOccurrence() != null ? event.getOccurrence().getId() : null;
  }
}
