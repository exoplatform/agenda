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
package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.storage.AgendaEventStorage;

public class AgendaEventReminderServiceImpl implements AgendaEventReminderService {

  private AgendaEventStorage agendaEventStorage;

  public AgendaEventReminderServiceImpl(AgendaEventStorage agendaEventStorage) {
    this.agendaEventStorage = agendaEventStorage;
  }

  @Override
  public List<EventReminder> getEventReminders(long eventId, long userIdentityId) {
    // TODO compute default event reminder for users
    return this.agendaEventStorage.getEventReminders(eventId, userIdentityId);
  }

  @Override
  public void saveEventReminders(long eventId, List<EventReminder> reminders, long userIdentityId) throws IllegalAccessException {
    // TODO Auto-generated method stub
  }

}
