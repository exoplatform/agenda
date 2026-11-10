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

  /**
   * Instant of the last stored change of {@link #response}: it orders the
   * waiting list of an event with a capacity limit, earliest first. Null on a
   * row whose answer has not changed since the column was added. An answer
   * to every date that merges several rows of the attendee, all of them
   * already holding the answer stored, keeps the earliest of their instants,
   * a null one first, so that the attendee keeps the earliest place they
   * held. Written by the server only, never taken from a client.
   */
  private ZonedDateTime         responseDate;

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

  /**
   * Builds an attendee row with no response date, the signature every caller
   * used before {@link #responseDate} existed, kept so that they still compile
   * and build the same row.
   *
   * @param id technical identifier
   * @param eventId event identifier
   * @param identityId attendee identity identifier
   * @param fromOccurrenceId first occurrence the row covers
   * @param untilOccurrenceId last occurrence the row covers
   * @param response the attendee's answer
   */
  public EventAttendee(long id,
                       long eventId,
                       long identityId,
                       ZonedDateTime fromOccurrenceId,
                       ZonedDateTime untilOccurrenceId,
                       EventAttendeeResponse response) {
    this(id, eventId, identityId, fromOccurrenceId, untilOccurrenceId, response, null);
  }

  @Override
  public EventAttendee clone() { // NOSONAR
    return new EventAttendee(id,
                             eventId,
                             identityId,
                             fromOccurrenceId,
                             untilOccurrenceId,
                             response,
                             responseDate);
  }
}
