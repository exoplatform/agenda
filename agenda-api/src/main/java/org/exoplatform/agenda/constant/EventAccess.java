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
   * (a {@code CalendarShare} record). A {@link EventVisibility#PRIVATE} event
   * is then rendered as busy time: its content is masked.
   */
  SHARED;

}
