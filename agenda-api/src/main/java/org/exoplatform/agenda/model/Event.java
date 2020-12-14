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
package org.exoplatform.agenda.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event implements Cloneable {

  private long              id;

  /**
   * Parent event object, when :
   * <ul>
   * <li>this is an occurrence (exceptional or ordinal) of a recurring event
   * </li>
   * <li>this is a duplication of an event that was modified by an attendee
   * having Readonly ACL on event. In that case, the event is modified and
   * duplicated in user calendar only.</li>
   * </ul>
   */
  private long              parentId;

  private long              calendarId;

  private long              creatorId;

  private long              modifierId;

  private ZonedDateTime     created;

  private ZonedDateTime     updated;

  private String            summary;

  private String            description;

  /**
   * Geopgraphic location of the event, content is free text and no predefined
   * format is used.
   */
  private String            location;

  /**
   * CSS color HEX value of the event
   */
  private String            color;

  private ZoneId            timeZoneId;

  private ZonedDateTime     start;

  private ZonedDateTime     end;

  /**
   * Whether the event happens all-day or at dedicated period of a day
   */
  private boolean           allDay;

  private EventAvailability availability;

  private EventStatus       status;

  /**
   * Event parent recurrence details
   */
  private EventRecurrence   recurrence;

  private EventOccurrence   occurrence;

  private Permission        acl;

  private boolean           allowAttendeeToUpdate;

  private boolean           allowAttendeeToInvite;

  @Override
  public Event clone() { // NOSONAR
    return new Event(id,
                     parentId,
                     calendarId,
                     creatorId,
                     modifierId,
                     created,
                     updated,
                     summary,
                     description,
                     location,
                     color,
                     timeZoneId,
                     start,
                     end,
                     allDay,
                     availability,
                     status,
                     recurrence == null ? null : recurrence.clone(),
                     occurrence == null ? null : occurrence.clone(),
                     acl == null ? null : acl.clone(),
                     allowAttendeeToUpdate,
                     allowAttendeeToInvite);
  }
}
