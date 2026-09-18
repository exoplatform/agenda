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
package org.exoplatform.agenda.constant;

/**
 * How a user may read one event (EXO-90357): computed once per read, so that
 * every path rendering an event — REST, search, ICS, MCP — masks a private
 * event the same way for the same reader.
 */
public enum EventAccess {

  /** The user may not read the event at all. */
  NONE,

  /**
   * The user reads the event as before this feature: they own or may see its
   * calendar (a personal calendar of their own, a space they belong to), or
   * they are invited to it. Nothing is masked.
   */
  FULL,

  /**
   * The user reads the event only because its calendar was shared with them
   * (a {@code CalendarShare} record) <b>for viewing</b>. A
   * {@link EventVisibility#PRIVATE} event is then rendered as busy time: its
   * content is masked.
   */
  SHARED,

  /**
   * The user reads the event only because its calendar was shared with them
   * (EXO-90378) <b>for editing</b>. Nothing is masked: an editor acts in the
   * owner's calendar as the owner would, so they see a
   * {@link EventVisibility#PRIVATE} event in full — a delegate who cannot read
   * the private meeting they are asked to move is not a delegate. They may set
   * their own reminders on it, which a {@link #SHARED} reader may not.
   * <p>
   * This is a <b>reading</b> access, and it grants no write on its own: what an
   * editor may write is decided by {@code AgendaEventService.canUpdateEvent}
   * and {@code canCreateEvent}, which read the share level themselves.
   */
  SHARED_EDIT;

}
