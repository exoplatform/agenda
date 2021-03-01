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
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaCalendarStorage {

  private AgendaEventStorage agendaEventStorage;

  private ListenerService    listenerService;

  private CalendarDAO        calendarDAO;

  public AgendaCalendarStorage(AgendaEventStorage agendaEventStorage, CalendarDAO calendarDAO, ListenerService listenerService) {
    this.agendaEventStorage = agendaEventStorage;
    this.calendarDAO = calendarDAO;
    this.listenerService = listenerService;
  }

  public List<Long> getCalendarIdsByOwnerIds(int offset, int limit, Long... ownerIds) {
    return this.calendarDAO.getCalendarIdsByOwnerIds(offset, limit, ownerIds);
  }

  public int countCalendarsByOwners(Long... ownerIds) {
    return this.calendarDAO.countCalendarsByOwnerIds(ownerIds);
  }

  public Calendar getCalendarById(long calendarId) {
    CalendarEntity calendarEntity = this.calendarDAO.find(calendarId);
    return fromEntity(calendarEntity);
  }

  public Calendar createCalendar(Calendar calendar) {
    CalendarEntity calendarEntity = toEntity(calendar);
    calendarEntity = calendarDAO.create(calendarEntity);
    Calendar createdCalendar = fromEntity(calendarEntity);
    Utils.broadcastEvent(listenerService, "exo.agenda.calendar.created", createdCalendar, null);
    return createdCalendar;
  }

  public void updateCalendar(Calendar calendar) {
    CalendarEntity calendarEntity = toEntity(calendar);
    calendarEntity = calendarDAO.update(calendarEntity);
    Utils.broadcastEvent(listenerService, "exo.agenda.calendar.updated", fromEntity(calendarEntity), null);
  }

  public void deleteCalendarById(long calendarId) {
    CalendarEntity calendarEntity = this.calendarDAO.find(calendarId);
    if (calendarEntity == null) {
      return;
    }
    this.agendaEventStorage.deleteCalendarEvents(calendarId);
    calendarDAO.delete(calendarEntity);
    Utils.broadcastEvent(listenerService, "exo.agenda.calendar.deleted", fromEntity(calendarEntity), null);
  }

}
