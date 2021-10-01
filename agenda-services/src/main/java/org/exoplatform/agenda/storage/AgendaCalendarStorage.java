// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
