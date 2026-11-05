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
package org.exoplatform.agenda.storage.cached;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.services.cache.ClusterCachedObjectSelector;
import org.exoplatform.services.cache.ObjectCacheInfo;

/**
 * Selects the cached events of a calendar, which are removed from every node of a cluster.
 */
public class CalendarEventsSelector extends ClusterCachedObjectSelector<Long, Event> {

  private static final long serialVersionUID = -2954018373648209571L;

  private final long        calendarId;

  public CalendarEventsSelector(long calendarId) {
    this.calendarId = calendarId;
  }

  @Override
  public boolean select(Long eventId, ObjectCacheInfo<? extends Event> ocinfo) {
    Event event = ocinfo.get();
    return event != null && event.getCalendarId() == calendarId;
  }

  @Override
  protected Object[] criteria() {
    return new Object[] { calendarId };
  }
}
