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
package org.exoplatform.agenda.rest.util;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.ConferenceEventEntity;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;

/**
 * Builds the link to the event a call was held for.
 */
public class ConferenceEventEntityBuilder {

  /**
   * Utility class.
   */
  private ConferenceEventEntityBuilder() {
  }

  /**
   * Builds the entity of an event or an occurrence. An occurrence, computed
   * or stored, opens through its series and its date, the address the agenda
   * notifications use; any other event through its own identifier.
   *
   * @param event the event or the occurrence
   * @return the entity, or <code>null</code> when there is no event
   */
  public static ConferenceEventEntity toEntity(Event event) {
    if (event == null) {
      return null;
    }
    ZonedDateTime occurrenceId = event.getParentId() > 0 && event.getOccurrence() != null ? event.getOccurrence().getId()
                                                                                          : null;
    Event linked = event;
    if (occurrenceId != null) {
      linked = new Event();
      linked.setId(event.getParentId());
    }
    return new ConferenceEventEntity(event.getId(),
                                     event.getParentId(),
                                     occurrenceId == null ? null : AgendaDateUtils.toRFC3339Date(occurrenceId, ZoneOffset.UTC),
                                     event.getSummary(),
                                     AgendaDateUtils.toRFC3339Date(event.getStart(), ZoneOffset.UTC),
                                     AgendaDateUtils.toRFC3339Date(event.getEnd(), ZoneOffset.UTC),
                                     event.isAllDay(),
                                     NotificationUtils.getWebEventURL(linked, occurrenceId));
  }

}
