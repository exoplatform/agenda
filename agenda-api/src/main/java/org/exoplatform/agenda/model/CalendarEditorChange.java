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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a colleague did in a calendar its owner shared with them for editing
 * (EXO-90378) — the whole of what the owner's notification needs.
 * <p>
 * Everything is captured <b>when the change happens</b>, the event's summary
 * included, and nothing is read again afterwards: a deletion has no event left
 * to read, and the three kinds of change must read alike.
 * <p>
 * The event service builds and broadcasts this, and only for a modifier whose
 * one right over the event is the share
 * ({@code AgendaEventService.CALENDAR_EDITED_BY_SHAREE_EVENT}): the decision
 * is the service's, the listener behind it is glue.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEditorChange {

  /** What the colleague did. */
  public enum Kind {
    /** They created an event in the calendar. */
    ADDED,
    /** They changed one of its events. */
    CHANGED,
    /** They deleted one of its events. */
    REMOVED
  }

  /** Technical identifier of the calendar the change happened in. */
  private long   calendarId;

  /** Identity identifier of the calendar's owner: the one recipient. */
  private long   ownerIdentityId;

  /** Identity identifier of the colleague who made the change: the sender. */
  private long   modifierIdentityId;

  /** Technical identifier of the event. */
  private long   eventId;

  /**
   * The event's summary as it stood when the change was made, empty for an
   * event that carries none. Captured here because a deletion leaves nothing
   * to read afterwards.
   */
  private String eventSummary;

  /** What the colleague did. */
  private Kind   kind;

}
