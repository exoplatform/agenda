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

import java.time.ZonedDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One clash on a user's own calendar: a set of events that overlap in time,
 * with the window they span.
 * <p>
 * <strong>Membership is transitive, and that is a decision, not an
 * accident.</strong> A group holds every event connected to another by an
 * overlap, even when two of its members do not themselves overlap: A 09:00 -
 * 11:00, B 10:00 - 12:00 and C 11:30 - 12:30 are <em>one</em> group of three,
 * not two pairs. A person untangling a morning untangles the whole tangle at
 * once, and splitting it into pairs would report the same event twice and
 * invite a caller to renumber and dedupe — the very work this exists to
 * remove.
 * <p>
 * <strong>{@link #getStart()} and {@link #getEnd()} are the envelope of the
 * group, not a common busy interval.</strong> For a pair they are the same
 * thing; for the transitive chain above the envelope is 09:00 - 12:30 while no
 * instant is inside all three. A caller must present them as "the stretch of
 * the day this tangle covers", never as "the time you are triple-booked".
 * <p>
 * Two events merely touching — one ending exactly when the next begins — are
 * <em>not</em> a conflict and are never in a group together. See
 * {@link org.exoplatform.agenda.service.AgendaScheduleConflictService} for the
 * rule and for everything else that is deliberately left out of a group.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleConflict {

  private List<ScheduleConflictEvent> events;

  private ZonedDateTime               start;

  private ZonedDateTime               end;

}
