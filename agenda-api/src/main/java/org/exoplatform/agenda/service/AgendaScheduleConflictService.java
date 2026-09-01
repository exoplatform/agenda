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
package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;

import org.exoplatform.agenda.model.ScheduleConflicts;

/**
 * Finds the clashes on a user's <em>own</em> calendar over a window.
 * <p>
 * This exists because overlap detection is interval arithmetic, and interval
 * arithmetic belongs in code that can be tested rather than in a caller — an
 * AI prompt, a screen, a report — that has to be talked into getting it right
 * every time. The one error this service exists to make impossible is
 * <strong>adjacency read as a clash</strong>: a 10:00 - 11:00 meeting followed
 * by an 11:00 - 12:00 meeting is a normal morning, and reporting it as a
 * double booking is how a schedule report loses its reader.
 * <h2>What counts as a conflict</h2>
 * Two events conflict when they <strong>strictly</strong> overlap:
 * {@code a.start < b.end && b.start < a.end}. Touching boundaries do not
 * overlap. Groups are transitive — see {@link
 * org.exoplatform.agenda.model.ScheduleConflict}.
 * <h2>What is deliberately not a conflict</h2>
 * <ul>
 * <li><strong>An event the user has declined.</strong> They told the organiser
 * they are not coming, so the time is theirs; reporting it as a clash is how a
 * report earns the habit of being ignored. This matches free/busy, where a
 * decline does not make a user busy.</li>
 * <li><strong>A cancelled event.</strong> It is not happening.</li>
 * <li><strong>An event published as free time</strong>
 * ({@link org.exoplatform.agenda.constant.EventAvailability#FREE}) — a marker
 * its organiser explicitly said does not occupy anyone.</li>
 * <li><strong>An event with no positive duration</strong>, which cannot
 * overlap anything under the strict rule and whose only effect would be to
 * make grouping depend on the order equal keys happen to sort in.</li>
 * </ul>
 * An <strong>all-day</strong> event is none of those and does clash with a
 * meeting inside its day: it occupies the day, and a user with an all-day
 * offsite and a 10:00 call has something real to sort out. It is reported like
 * any other event, and it is the caller's job to narrate it as what it is
 * rather than as a double booking of the same hour.
 * <p>
 * <strong>Recurring series are seen as their occurrences.</strong> The window
 * is read through {@link AgendaEventService#getEvents}, which materialises
 * each occurrence falling inside it, so the Tuesday instance of a weekly
 * stand-up is what conflicts — not the series. Those occurrences carry no
 * identifier of their own ({@code id} is 0 and the series is in
 * {@code parentId}), which is why nothing here keys events by their id.
 * <h2>Whose calendar</h2>
 * The asking user's, and only theirs. {@link AgendaEventService#getEvents}
 * refuses an attendee filter naming anyone else, so the platform's own
 * calendar ACL is what answers here and this service adds no permission rule
 * of its own. Someone else's schedule is a different question with a different
 * answer — {@link AgendaAvailabilityService}, which discloses times and never
 * titles.
 */
public interface AgendaScheduleConflictService {

  /**
   * Reports the clashes on the asking user's own calendar over a window.
   *
   * @param start window start, mandatory
   * @param end window end, mandatory, strictly after {@code start}
   * @param userIdentityId identity id of the user whose calendar is read,
   *          which is also the user the read is performed as
   * @return the conflict groups, each holding at least two events, ordered by
   *         the time they start; never {@code null}, and empty when nothing
   *         overlaps
   * @throws IllegalAccessException when the user may not read that calendar
   * @throws IllegalArgumentException when the window is missing, inverted or
   *           longer than this service will scan in one call
   */
  ScheduleConflicts getScheduleConflicts(ZonedDateTime start,
                                         ZonedDateTime end,
                                         long userIdentityId) throws IllegalAccessException;

}
