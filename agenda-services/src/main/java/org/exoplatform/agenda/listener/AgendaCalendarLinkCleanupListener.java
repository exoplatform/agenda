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
import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import jakarta.annotation.PostConstruct;

/**
 * Deletes the link of a calendar once the calendar is deleted. Glue only: the
 * deletion is the service's.
 * <p>
 * The link table carries no foreign key to the calendar table on purpose: a key
 * would make every deletion of a calendar that has a link fail, whichever addon
 * deletes it. A link left behind would open nothing anyway — the feed answers
 * 404 for a calendar that no longer exists — so this listener keeps the table
 * tidy rather than keeping anything safe.
 */
@Component
public class AgendaCalendarLinkCleanupListener implements ListenerBase<Calendar, Object> {

  /** Broadcast by {@code AgendaCalendarStorage} once a calendar row is deleted. */
  public static final String              CALENDAR_DELETED_EVENT = "exo.agenda.calendar.deleted";

  private final ListenerService           listenerService;

  private final AgendaCalendarLinkService calendarLinkService;

  /**
   * Builds the listener.
   *
   * @param listenerService the platform event bus
   * @param calendarLinkService the owner of calendar links
   */
  @Autowired
  public AgendaCalendarLinkCleanupListener(ListenerService listenerService, AgendaCalendarLinkService calendarLinkService) {
    this.listenerService = listenerService;
    this.calendarLinkService = calendarLinkService;
  }

  /**
   * Registers the listener on the calendar deletion event.
   */
  @PostConstruct
  public void init() {
    listenerService.addListener(CALENDAR_DELETED_EVENT, this);
  }

  /**
   * Deletes the link of the deleted calendar.
   *
   * @param event the deletion event, whose source is the deleted calendar
   */
  @Override
  public void onEvent(Event<Calendar, Object> event) {
    Calendar calendar = event == null ? null : event.getSource();
    if (calendar != null && calendar.getId() > 0) {
      calendarLinkService.deleteCalendarLinks(calendar.getId());
    }
  }

}
