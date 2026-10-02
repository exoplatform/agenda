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
package org.exoplatform.agenda.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.constant.EventAvailability;

/**
 * The event an invitation received by mail describes, as agenda reads it from
 * the iCalendar object (EXO-90866): the master component, the occurrences its
 * series excludes and the occurrences it overrides. Read from the sender's
 * content, capped and narrowed to plain text; nothing in it names a person of
 * this platform.
 *
 * @param uid the event's UID
 * @param method the object's METHOD, upper-cased, null when it names none
 * @param sequence the SEQUENCE, 0 when absent
 * @param cancelled whether the component's STATUS is CANCELLED
 * @param organizer the organiser's bare address, lower-cased, null when the
 *          component names none
 * @param summary the title, plain text, may be null
 * @param description the description, plain text, may be null
 * @param location the location, plain text, may be null
 * @param url the URL property as written, may be null
 * @param allDay whether the event spans whole days
 * @param start the start; for an all-day event, its first day at UTC midnight
 * @param end the end; for an all-day event, its last day at UTC midnight
 * @param timeZone the zone the event is expressed in
 * @param availability FREE for a transparent event, BUSY for an opaque one,
 *          DEFAULT when the object says nothing
 * @param recurrence the series' rule, null for a single event or an
 *          occurrence
 * @param occurrenceId the occurrence an override replaces, in the event's zone;
 *          null for the master
 * @param excludedOccurrences the occurrences the series' EXDATEs remove, in
 *          the event's zone; empty for an occurrence
 * @param overrides the occurrences the object replaces, each with its
 *          {@code occurrenceId}; empty for an occurrence
 */
public record MailInvitationEvent(String uid,
                                  String method,
                                  int sequence,
                                  boolean cancelled,
                                  String organizer,
                                  String summary,
                                  String description,
                                  String location,
                                  String url,
                                  boolean allDay,
                                  ZonedDateTime start,
                                  ZonedDateTime end,
                                  ZoneId timeZone,
                                  EventAvailability availability,
                                  EventRecurrence recurrence,
                                  ZonedDateTime occurrenceId,
                                  List<ZonedDateTime> excludedOccurrences,
                                  List<MailInvitationEvent> overrides) {
}
