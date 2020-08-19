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
package org.exoplatform.agenda.util;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.model.Calendar;

public class EntityMapper {

  private EntityMapper() {
  }

  public static Calendar fromEntity(CalendarEntity calendarEntity) {
    if (calendarEntity == null) {
      return null;
    }
    return new Calendar(calendarEntity.getId(),
                        calendarEntity.getOwnerId(),
                        calendarEntity.isSystem(),
                        null,
                        calendarEntity.getDescription(),
                        calendarEntity.getCreatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getCreatedDate()),
                        calendarEntity.getUpdatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getUpdatedDate()),
                        calendarEntity.getColor(),
                        null);
  }

  public static CalendarEntity toEntity(Calendar calendar) {
    if (calendar == null) {
      return null;
    }
    CalendarEntity calendarEntity = new CalendarEntity();
    if (calendar.getId() != 0) {
      calendarEntity.setId(calendar.getId());
    }
    if (calendar.getOwnerId() != 0) {
      calendarEntity.setOwnerId(calendar.getOwnerId());
    }
    calendarEntity.setColor(calendar.getColor());
    if (calendar.getCreated() != null) {
      calendarEntity.setCreatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getCreated()));
    }
    if (calendar.getUpdated() != null) {
      calendarEntity.setUpdatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getUpdated()));
    }
    calendarEntity.setDescription(calendar.getDescription());
    calendarEntity.setSystem(calendar.isSystem());
    return calendarEntity;
  }

}
