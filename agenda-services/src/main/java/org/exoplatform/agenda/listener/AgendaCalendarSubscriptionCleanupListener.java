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
 * Removes the subscription of a calendar deleted by any other path than
 * unsubscribing (EXO-90278), so that no feed keeps being read into a calendar
 * that is gone. Glue only: the removal is the service's.
 */
@Component
public class AgendaCalendarSubscriptionCleanupListener implements ListenerBase<Calendar, Object> {

  /** Broadcast by {@code AgendaCalendarStorage} once a calendar row is deleted. */
  public static final String                      CALENDAR_DELETED_EVENT = "exo.agenda.calendar.deleted";

  private final ListenerService                   listenerService;

  private final AgendaCalendarSubscriptionService subscriptionService;

  /**
   * Builds the listener.
   *
   * @param listenerService the platform event bus
   * @param subscriptionService the owner of calendar subscriptions
   */
  @Autowired
  public AgendaCalendarSubscriptionCleanupListener(ListenerService listenerService,
                                                   AgendaCalendarSubscriptionService subscriptionService) {
    this.listenerService = listenerService;
    this.subscriptionService = subscriptionService;
  }

  /**
   * Registers the listener on the calendar deletion event.
   */
  @PostConstruct
  public void init() {
    listenerService.addListener(CALENDAR_DELETED_EVENT, this);
  }

  /**
   * Removes the subscription of the deleted calendar, when it was a subscribed
   * one.
   *
   * @param event the deletion event, whose source is the deleted calendar
   */
  @Override
  public void onEvent(Event<Calendar, Object> event) {
    Calendar calendar = event == null ? null : event.getSource();
    if (calendar != null && calendar.getId() > 0 && calendar.isSubscription()) {
      subscriptionService.deleteCalendarSubscription(calendar.getId());
    }
  }

}
