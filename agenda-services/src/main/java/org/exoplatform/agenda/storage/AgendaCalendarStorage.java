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

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.dao.CalendarDAO;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.container.component.RequestLifeCycle;
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

  /**
   * Retrieves the technical identifier of the system (default) calendar of a
   * given owner.
   *
   * @param ownerId technical identifier of the calendar owner identity
   * @return technical identifier of the owner's system calendar, or
   *         {@code null} when the owner has no system calendar
   */
  public Long getSystemCalendarIdByOwnerId(long ownerId) {
    return this.calendarDAO.getSystemCalendarIdByOwnerId(ownerId);
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

  /**
   * Moves all events of a calendar to another calendar, then broadcasts one
   * {@code exo.agenda.event.moved} event per moved event so that listeners
   * (Elasticsearch indexing, connectors) converge on the new calendar
   * membership. No business logic here: the decision of what to move and
   * where belongs to the service layer.
   *
   * @param fromCalendarId technical identifier of the calendar the events are
   *          moved away from
   * @param toCalendarId technical identifier of the calendar receiving the
   *          events
   * @param modifierId {@link org.exoplatform.social.core.identity.model.Identity}
   *          technical identifier of the user who triggered the move
   * @return {@link List} of technical identifiers of the moved events, empty
   *         when the source calendar had no events
   */
  public List<Long> moveCalendarEvents(long fromCalendarId, long toCalendarId, long modifierId) {
    List<Long> movedEventIds = this.agendaEventStorage.moveCalendarEvents(fromCalendarId, toCalendarId);
    for (Long eventId : movedEventIds) {
      AgendaEventModification eventModification = new AgendaEventModification(eventId, toCalendarId, modifierId);
      eventModification.addModificationType(AgendaEventModificationType.UPDATED);
      Utils.broadcastEvent(listenerService, Utils.POST_MOVE_AGENDA_EVENT_EVENT, eventModification, null);
    }
    return movedEventIds;
  }

  public void deleteCalendarById(long calendarId) {
    CalendarEntity calendarEntity = this.calendarDAO.find(calendarId);
    if (calendarEntity == null) {
      return;
    }
    this.agendaEventStorage.deleteCalendarEvents(calendarId);
    RequestLifeCycle.restartTransaction();
    calendarDAO.delete(calendarEntity);
    Utils.broadcastEvent(listenerService, "exo.agenda.calendar.deleted", fromEntity(calendarEntity), null);
  }

}
