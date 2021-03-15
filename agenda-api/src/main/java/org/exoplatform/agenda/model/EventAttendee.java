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

import java.time.ZonedDateTime;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventAttendee implements Cloneable {

  private long                  id;

  private long                  eventId;

  private long                  identityId;

  private ZonedDateTime         fromOccurrenceId;

  private ZonedDateTime         untilOccurrenceId;

  private EventAttendeeResponse response;

  public EventAttendee(long id, long identityId, EventAttendeeResponse response) {
    this.id = id;
    this.identityId = identityId;
    this.response = response;
  }

  public EventAttendee(long id, long eventId, long identityId, EventAttendeeResponse response) {
    this.id = id;
    this.eventId = eventId;
    this.identityId = identityId;
    this.response = response;
  }

  @Override
  public EventAttendee clone() { // NOSONAR
    return new EventAttendee(id,
                             eventId,
                             identityId,
                             fromOccurrenceId,
                             untilOccurrenceId,
                             response);
  }
}
