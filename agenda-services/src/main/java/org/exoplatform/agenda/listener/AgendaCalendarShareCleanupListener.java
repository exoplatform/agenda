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
import org.exoplatform.agenda.service.AgendaCalendarShareService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import jakarta.annotation.PostConstruct;

/**
 * Deletes the shares of a calendar once the calendar is deleted (EXO-90357).
 * Glue only: the deletion is the service's.
 * <p>
 * The share table carries no foreign key to the calendar table, for the
 * reason the link table carries none: a key would make every deletion of a
 * shared calendar fail, whichever addon deletes it. A share left behind grants
 * nothing — every read checks the calendar first — so this listener keeps the
 * table tidy rather than keeping anything safe.
 */
@Component
public class AgendaCalendarShareCleanupListener implements ListenerBase<Calendar, Object> {

  private final ListenerService            listenerService;

  private final AgendaCalendarShareService calendarShareService;

  /**
   * Builds the listener.
   *
   * @param listenerService the platform event bus
   * @param calendarShareService the owner of calendar shares
   */
  @Autowired
  public AgendaCalendarShareCleanupListener(ListenerService listenerService, AgendaCalendarShareService calendarShareService) {
    this.listenerService = listenerService;
    this.calendarShareService = calendarShareService;
  }

  /**
   * Registers the listener on the calendar deletion event.
   */
  @PostConstruct
  public void init() {
    listenerService.addListener(AgendaCalendarLinkCleanupListener.CALENDAR_DELETED_EVENT, this);
  }

  /**
   * Deletes the shares of the deleted calendar.
   *
   * @param event the deletion event, whose source is the deleted calendar
   */
  @Override
  public void onEvent(Event<Calendar, Object> event) {
    Calendar calendar = event == null ? null : event.getSource();
    if (calendar != null && calendar.getId() > 0) {
      calendarShareService.deleteShares(calendar.getId());
    }
  }

}
