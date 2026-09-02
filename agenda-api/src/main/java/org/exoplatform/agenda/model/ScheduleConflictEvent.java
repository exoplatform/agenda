/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.model;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One event of a {@link ScheduleConflict}, with the two things a caller needs
 * in order to say something useful about it and that the {@link Event} alone
 * does not carry: what the asking user answered to it, and whether it is
 * theirs to move.
 * <p>
 * <strong>{@code isCreatedByUser()} is the closest thing Agenda has to "I am
 * the organiser".</strong> There is no organiser field on an event: the only
 * person the model records is {@code Event.getCreatorId()}, and the creator is
 * also the one {@link org.exoplatform.agenda.service.AgendaEventService} lets
 * update the event unconditionally. So "yours to move" resolves through the
 * creator, and a caller proposing a reschedule must not promise it for an
 * event where this is {@code false} — there, the only honest proposal is to
 * ask the organiser or to decline.
 * <p>
 * {@code getResponse()} is {@code null} when the user is not an attendee of
 * the event at all, which happens for an event sitting on a calendar of a
 * space they belong to. Null is "never asked", not "has not answered yet" —
 * the latter is {@link EventAttendeeResponse#NEEDS_ACTION}. Neither is a
 * decline: a declined event never reaches this class.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleConflictEvent {

  private Event                 event;

  private EventAttendeeResponse response;

  private boolean               createdByUser;

}
