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
package org.exoplatform.agenda.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import jakarta.annotation.PostConstruct;

/**
 * Keeps a space's subscribed calendars in the colour of the space's calendar
 * (EXO-90373): a manager changing the colour in the space settings, or the
 * space's calendar being created with one, recolours the events the space's
 * subscriptions import. Glue only: the rule is the subscription service's.
 */
@Component
public class AgendaSpaceSubscriptionColorListener implements ListenerBase<Calendar, Object> {

  /** Broadcast by {@code AgendaCalendarStorage} once a calendar row is created. */
  public static final String                      CALENDAR_CREATED_EVENT = "exo.agenda.calendar.created";

  /** Broadcast by {@code AgendaCalendarStorage} once a calendar row is updated. */
  public static final String                      CALENDAR_UPDATED_EVENT = "exo.agenda.calendar.updated";

  private final ListenerService                   listenerService;

  private final AgendaCalendarSubscriptionService subscriptionService;

  /**
   * Builds the listener.
   *
   * @param listenerService the platform event bus
   * @param subscriptionService the owner of calendar subscriptions
   */
  @Autowired
  public AgendaSpaceSubscriptionColorListener(ListenerService listenerService,
                                              AgendaCalendarSubscriptionService subscriptionService) {
    this.listenerService = listenerService;
    this.subscriptionService = subscriptionService;
  }

  /**
   * Registers the listener on the calendar creation and update events.
   */
  @PostConstruct
  public void init() {
    listenerService.addListener(CALENDAR_CREATED_EVENT, this);
    listenerService.addListener(CALENDAR_UPDATED_EVENT, this);
  }

  /**
   * Hands the saved calendar to the subscription service, unless it is itself
   * a subscribed calendar: recolouring one broadcasts its update too.
   *
   * @param event the event, whose source is the saved calendar
   */
  @Override
  public void onEvent(Event<Calendar, Object> event) {
    Calendar calendar = event == null ? null : event.getSource();
    if (calendar != null && calendar.getId() > 0 && !calendar.isSubscription()) {
      subscriptionService.followSpaceColor(calendar);
    }
  }

}
