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
import java.util.HashMap;
import java.util.Map;

import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;

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

  /**
   * Whether the event's content may be read outside eXo. Honoured by the
   * published calendar link only; inside eXo it changes nothing.
   */
  private EventVisibility   visibility;

  private EventStatus       status;

  /**
   * Event parent recurrence details
   */
  private EventRecurrence   recurrence;

  private EventOccurrence   occurrence;

  private EventPermission   acl;

  private boolean           allowAttendeeToUpdate;

  private boolean           allowAttendeeToInvite;

  private Map<String, String> parameters;

  /**
   * Whether the event's content was withheld from the reader (EXO-90357): a
   * {@link EventVisibility#PRIVATE} event read by someone who sees its
   * calendar only through a share is rendered as busy time — summary,
   * description, location, attendees, conferences and reminders cleared —
   * and says so here, so that every render path skips what it would otherwise
   * read again by identifier. Computed per reader, never stored.
   */
  private boolean           masked;

  /**
   * How the reader this event was rendered for may read it (EXO-90357):
   * {@link EventAccess#SHARED} when they see its calendar only through a
   * share, {@link EventAccess#FULL} otherwise, null when read for nobody in
   * particular. Carried so that what is rendered <em>from</em> this event —
   * the parent series of an occurrence — is masked by the same rule as the
   * event itself: {@link #masked} alone cannot say, since an occurrence and
   * its series may differ in visibility. Computed per reader, never stored.
   */
  private EventAccess       access;

  public Event(long id,
               long parentId,
               long calendarId,
               long creatorId,
               long modifierId,
               ZonedDateTime created,
               ZonedDateTime updated,
               String summary,
               String description,
               String location,
               String color,
               ZoneId timeZoneId,
               ZonedDateTime start,
               ZonedDateTime end,
               boolean allDay,
               EventAvailability availability,
               EventVisibility visibility,
               EventStatus status,
               EventRecurrence recurrence,
               EventOccurrence occurrence,
               EventPermission acl,
               boolean allowAttendeeToUpdate,
               boolean allowAttendeeToInvite) {
    this.id = id;
    this.parentId = parentId;
    this.calendarId = calendarId;
    this.creatorId = creatorId;
    this.modifierId = modifierId;
    this.created = created;
    this.updated = updated;
    this.summary = summary;
    this.description = description;
    this.location = location;
    this.color = color;
    this.timeZoneId = timeZoneId;
    this.start = start;
    this.end = end;
    this.allDay = allDay;
    this.availability = availability;
    this.visibility = visibility;
    this.status = status;
    this.recurrence = recurrence;
    this.occurrence = occurrence;
    this.acl = acl;
    this.allowAttendeeToUpdate = allowAttendeeToUpdate;
    this.allowAttendeeToInvite = allowAttendeeToInvite;
  }

  /**
   * Builds an event with its parameters, as read for nobody in particular:
   * nothing masked. The signature every caller used before EXO-90357 added
   * {@link #masked}, kept so that they build the same event.
   *
   * @param id technical identifier
   * @param parentId parent event identifier
   * @param calendarId calendar identifier
   * @param creatorId creator identity identifier
   * @param modifierId last modifier identity identifier
   * @param created creation instant
   * @param updated last update instant
   * @param summary title
   * @param description description
   * @param location location
   * @param color colour
   * @param timeZoneId time zone the event was created in
   * @param start start
   * @param end end
   * @param allDay whether all-day
   * @param availability whether it takes the reader's time
   * @param visibility whether its content may be read outside eXo
   * @param status status
   * @param recurrence recurrence details
   * @param occurrence occurrence details
   * @param acl the reader's permissions
   * @param allowAttendeeToUpdate whether attendees may update it
   * @param allowAttendeeToInvite whether attendees may invite
   * @param parameters free parameters
   */
  public Event(long id, // NOSONAR
               long parentId,
               long calendarId,
               long creatorId,
               long modifierId,
               ZonedDateTime created,
               ZonedDateTime updated,
               String summary,
               String description,
               String location,
               String color,
               ZoneId timeZoneId,
               ZonedDateTime start,
               ZonedDateTime end,
               boolean allDay,
               EventAvailability availability,
               EventVisibility visibility,
               EventStatus status,
               EventRecurrence recurrence,
               EventOccurrence occurrence,
               EventPermission acl,
               boolean allowAttendeeToUpdate,
               boolean allowAttendeeToInvite,
               Map<String, String> parameters) {
    this(id,
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
         visibility,
         status,
         recurrence,
         occurrence,
         acl,
         allowAttendeeToUpdate,
         allowAttendeeToInvite,
         parameters,
         false,
         null);
  }

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
                     visibility,
                     status,
                     recurrence == null ? null : recurrence.clone(),
                     occurrence == null ? null : occurrence.clone(),
                     acl == null ? null : acl.clone(),
                     allowAttendeeToUpdate,
                     allowAttendeeToInvite,
                     parameters == null ? null : new HashMap<>(parameters),
                     masked,
                     access);
  }
}
