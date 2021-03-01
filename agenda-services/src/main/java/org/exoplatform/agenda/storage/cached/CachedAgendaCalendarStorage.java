/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.storage.cached;

import org.exoplatform.agenda.dao.CalendarDAO;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.commons.cache.future.FutureExoCache;
import org.exoplatform.commons.cache.future.Loader;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.listener.ListenerService;

public class CachedAgendaCalendarStorage extends AgendaCalendarStorage {

  public static final String                     CALENDAR_CACHE_NAME = "agenda.calendar";

  private FutureExoCache<Long, Calendar, Object> calendarFutureCache = null;

  public CachedAgendaCalendarStorage(CacheService cacheService,
                                     AgendaEventStorage agendaEventStorage,
                                     CalendarDAO calendarDAO,
                                     ListenerService listenerService) {
    super(agendaEventStorage, calendarDAO, listenerService);

    ExoCache<Long, Calendar> calendarCache = cacheService.getCacheInstance(CALENDAR_CACHE_NAME);
    // Future cache is used for clustered environment improvements (usage of
    // putLocal VS put)
    this.calendarFutureCache = new FutureExoCache<>(new Loader<Long, Calendar, Object>() {
      @Override
      public Calendar retrieve(Object context, Long calendarId) throws Exception {
        return CachedAgendaCalendarStorage.super.getCalendarById(calendarId);
      }
    }, calendarCache);
  }

  @Override
  public Calendar getCalendarById(long calendarId) {
    Calendar calendar = this.calendarFutureCache.get(null, calendarId);
    return calendar == null ? null : calendar.clone();
  }

  @Override
  public void updateCalendar(Calendar calendar) {
    super.updateCalendar(calendar);
    this.calendarFutureCache.remove(calendar.getId());
  }

  @Override
  public void deleteCalendarById(long calendarId) {
    super.deleteCalendarById(calendarId);
    this.calendarFutureCache.remove(calendarId);
  }

  public void clearCache() {
    this.calendarFutureCache.clear();
  }
}
