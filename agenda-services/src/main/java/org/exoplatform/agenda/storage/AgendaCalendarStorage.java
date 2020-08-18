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

import java.util.List;

import org.exoplatform.agenda.model.Calendar;

public class AgendaCalendarStorage {

  public AgendaCalendarStorage() {
    // TODO Auto-generated constructor stub
  }

  public List<Calendar> getCalendarsByOwners(int offset, int limit, Long... ownerIds) {
    // TODO Auto-generated method stub
    return null;
  }

  public int countCalendarsByOwners(Long... ownerIds) {
    // TODO Auto-generated method stub
    return 0;
  }

  public Calendar getCalendarById(long calendarId) {
    // TODO Auto-generated method stub
    return null;
  }

}
