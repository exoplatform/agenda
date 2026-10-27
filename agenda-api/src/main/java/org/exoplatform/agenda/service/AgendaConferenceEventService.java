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

import java.time.ZonedDateTime;

import org.exoplatform.agenda.model.Event;

/**
 * Finds the agenda event a web conferencing call was held for, from the call
 * alone: an event stores its meeting as a link, and a call is known by the
 * identifier its provider puts in that link.
 */
public interface AgendaConferenceEventService {

  /**
   * Finds the event, or the occurrence of a recurring event, a call was held
   * for at a given moment, as a user may read it.
   * <p>
   * An event refers to the call when one of its own meeting links of the
   * given type holds the call identifier as a whole path segment; only an
   * event the user can read in full counts, one they cannot read, or read
   * only as busy time, being left out. The candidates are the single events
   * and the occurrences referring to the call, a stored occurrence possibly
   * holding another call than its series. An occurrence is looked at when it
   * started at most the grace and a day and a half before the moment, and
   * among the 400 earliest such occurrences of its series, a bound only a
   * series recurring more often than hourly reaches.
   * <p>
   * The candidate whose span, extended by the grace, holds the moment is
   * answered, the one started last when several do. When none does, the
   * single event or series referring to the call that started last before
   * the moment is answered, or the first one found when all start after it:
   * a single event is so answered whatever the moment when it is the only
   * event of the call.
   *
   * @param providerType the web conferencing provider type, which is the type
   *          of the event's conference
   * @param callId the identifier of the call
   * @param moment when the call was held, such as the date a recording was
   *          saved
   * @param graceHours how many hours after an occurrence's end the call still
   *          belongs to it, from 0 to 168
   * @param identityId the technical identifier of the reading user's identity
   * @return the event or the occurrence, or <code>null</code> when that rule
   *         answers none the user can read in full
   * @throws IllegalArgumentException when the type, the call identifier or
   *           the moment is missing, or the grace is negative or longer than
   *           a week
   */
  Event getConferenceEvent(String providerType, String callId, ZonedDateTime moment, int graceHours, long identityId);

}
