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

/**
 * By what right a user may write an event (EXO-90378) — the one predicate
 * behind every write in this service, and therefore behind REST, drag and
 * drop, the ACL plugin and the MCP write tools, none of which check anything
 * of their own.
 * <p>
 * The rights are asked in the order in which they were added, and the share
 * is asked <b>last</b>: a user who could already write the event by any
 * older right is never answered {@link EventWriteRight#SHARE_EDITOR}. The one
 * restriction a share adds is on moving: a user holding an edit share on the
 * calendar an event is in moves it out only as the calendar's owner or the
 * event's creator, whatever other right answers
 * ({@code AgendaEventServiceImpl#checkCanMoveEvent}).
 */
enum EventWriteRight {

  /** No right at all: the write is refused. */
  NONE,

  /** The user owns the calendar, or manages the space that does. */
  CALENDAR,

  /** The user created the event and may still see its calendar. */
  CREATOR,

  /** The user attends the event, which lets its attendees update it. */
  ATTENDEE,

  /**
   * The user's only right is a calendar share granted for editing
   * (EXO-90378): they write events in the owner's personal calendar as the
   * owner would, and may not move one out of it.
   */
  SHARE_EDITOR
}
