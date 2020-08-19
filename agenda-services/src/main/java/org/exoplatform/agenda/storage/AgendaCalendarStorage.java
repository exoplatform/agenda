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
package org.exoplatform.agenda.storage;

import static org.exoplatform.agenda.util.EntityMapper.fromEntity;
import static org.exoplatform.agenda.util.EntityMapper.toEntity;

import java.util.List;

import org.exoplatform.agenda.dao.CalendarDAO;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaCalendarStorage {

  private static final Log LOG = ExoLogger.getLogger(AgendaCalendarStorage.class);

  private ListenerService  listenerService;

  private CalendarDAO      calendarDAO;

  public AgendaCalendarStorage(CalendarDAO calendarDAO, ListenerService listenerService) {
    this.listenerService = listenerService;
    this.calendarDAO = calendarDAO;
  }

  public List<Long> getCalendarIdsByOwnerIds(int offset, int limit, Long... ownerIds) {
    return this.calendarDAO.getCalendarIdsByOwnerIds(offset, limit, ownerIds);
  }

  public int countCalendarsByOwners(Long... ownerIds) {
    return this.calendarDAO.countCalendarsByOwners(ownerIds);
  }

  public Calendar getCalendarById(long calendarId) {
    CalendarEntity calendarEntity = this.calendarDAO.find(calendarId);
    return fromEntity(calendarEntity);
  }

  public Calendar createCalendar(Calendar calendar) {
    CalendarEntity calendarEntity = toEntity(calendar);
    calendarEntity = calendarDAO.create(calendarEntity);
    Calendar createdCalendar = fromEntity(calendarEntity);
    broadcastEvent("exo.agenda.calendar.created", createdCalendar);
    return createdCalendar;
  }

  public void updateCalendar(Calendar calendar) {
    CalendarEntity calendarEntity = toEntity(calendar);
    calendarEntity = calendarDAO.update(calendarEntity);
    broadcastEvent("exo.agenda.calendar.updated", fromEntity(calendarEntity));
  }

  public void deleteCalendarById(long calendarId) {
    CalendarEntity calendarEntity = this.calendarDAO.find(calendarId);
    if (calendarEntity == null) {
      return;
    }
    calendarDAO.delete(calendarEntity);
    broadcastEvent("exo.agenda.calendar.deleted", fromEntity(calendarEntity));
  }

  private void broadcastEvent(String eventName, Calendar calendar) {
    try {
      listenerService.broadcast(eventName, null, calendar);
    } catch (Exception e) {
      LOG.warn("Error broadcasting event '" + eventName + "' on agenda calendar with id '" + calendar.getId() + "'", e);
    }
  }

}
