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
package org.exoplatform.agenda.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;

/**
 * Free/busy over the platform's own calendars, under the platform's own
 * calendar ACL.
 * <p>
 * <strong>Every method takes the asking user's identity, and every method
 * enforces it.</strong> Availability is calendar content: knowing when someone
 * is busy is knowing that they have a meeting, and over a long enough window
 * it maps their working life. The rule applied here is not a new one — it is
 * {@code Utils.canAccessCalendar}, the same check
 * {@link AgendaEventService#getEvents} applies to a calendar owner. For a
 * personal calendar that check resolves to "its owner, and no one else", so in
 * practice a user may currently ask about their own availability only.
 * <p>
 * That is deliberately the narrow reading. Whether, and to whom, a user should
 * be able to expose their free/busy is a product decision that has not been
 * taken; until it is, this service enforces the guard that already exists
 * rather than inventing a sharing policy of its own. When such a policy
 * arrives it belongs here, in this one place, so that every surface built on
 * this service — REST, MCP, the UI — moves together and none of them can
 * drift.
 */
public interface AgendaAvailabilityService {

  /**
   * Computes the busy and free blocks of each given user over a window.
   *
   * @param targetIdentityIds technical identifiers of the users whose
   *          availability is asked for
   * @param start window start, inclusive
   * @param end window end, exclusive
   * @param userIdentityId technical identifier of the user asking
   * @return one {@link UserAvailability} per requested user, in the order
   *         asked
   * @throws IllegalAccessException when the asking user is not allowed to read
   *           the availability of any one of the requested users
   * @throws IllegalArgumentException when the window or the user list is
   *           missing or empty, or when the window ends before it starts
   */
  List<UserAvailability> getAvailability(List<Long> targetIdentityIds,
                                         ZonedDateTime start,
                                         ZonedDateTime end,
                                         long userIdentityId) throws IllegalAccessException;

  /**
   * Proposes the slots of the given length, inside the given window, over
   * which every one of the given attendees is free.
   * <p>
   * The proposal is only worth as much as the calendars behind it, so this
   * method refuses rather than guesses: if the asking user cannot read one
   * attendee's availability, no slot is proposed at all. Silently proposing
   * slots computed from a subset of the attendees would return a confident
   * answer that is not backed by the data.
   *
   * @param attendeeIdentityIds technical identifiers of the users who must all
   *          be free
   * @param duration the length a slot must have
   * @param windowStart earliest acceptable start
   * @param windowEnd latest acceptable end
   * @param morningsOnly restrict candidate starts to before noon
   * @param afternoonsOnly restrict candidate starts to noon and after
   * @param limit maximum number of slots to propose
   * @param userIdentityId technical identifier of the user asking
   * @return the candidate slots, earliest first, at most {@code limit} of them
   * @throws IllegalAccessException when the asking user is not allowed to read
   *           the availability of any one of the attendees
   * @throws IllegalArgumentException when the attendee list, the duration or
   *           the window is missing or invalid
   */
  List<TimeBlock> suggestMeetingTime(List<Long> attendeeIdentityIds,
                                     Duration duration,
                                     ZonedDateTime windowStart,
                                     ZonedDateTime windowEnd,
                                     boolean morningsOnly,
                                     boolean afternoonsOnly,
                                     int limit,
                                     long userIdentityId) throws IllegalAccessException;

  /**
   * Reports which of the given attendees clash with a proposed window.
   * <p>
   * Unlike {@link #getAvailability} this method does not refuse: it is an
   * enrichment of an operation the user is already allowed to perform —
   * creating or updating an event — and failing it would deny a legitimate
   * write over information the user never asked for. It reports on the
   * attendees it may read and names the ones it may not, so the answer is
   * partial but never misleading. See {@link AvailabilityConflicts}.
   *
   * @param attendeeIdentityIds technical identifiers of the event's attendees;
   *          non-user identities are ignored
   * @param start proposed window start
   * @param end proposed window end
   * @param userIdentityId technical identifier of the user asking
   * @return the clash report, never {@code null}
   */
  AvailabilityConflicts getConflicts(List<Long> attendeeIdentityIds,
                                     ZonedDateTime start,
                                     ZonedDateTime end,
                                     long userIdentityId);

  /**
   * Orders date-poll options so that the ones suiting the most attendees come
   * first.
   * <p>
   * Only the attendees the asking user may read are counted; an attendee whose
   * availability is not readable counts for no option, so they shift no option
   * relative to another. Like {@link #getConflicts} this degrades rather than
   * refusing — an unreadable attendee costs ordering quality, not the poll.
   *
   * @param dateOptions the options to order
   * @param attendeeIdentityIds technical identifiers of the event's attendees
   * @param userIdentityId technical identifier of the user asking
   * @return the options, most-available first, keeping the original order
   *         between equally available ones
   */
  List<EventDateOption> rankDateOptionsByAvailability(List<EventDateOption> dateOptions,
                                                      List<Long> attendeeIdentityIds,
                                                      long userIdentityId);

}
