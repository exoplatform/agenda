// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
