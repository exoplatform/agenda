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
 * Where a calendar share record comes from (EXO-90357).
 * <p>
 * Stored by name, so the constants may be reordered but never renamed.
 */
public enum CalendarShareSource {

  /** The owner shared the calendar from eXo. */
  EXO,

  /**
   * A share that already existed on a remote server (a CalDAV collection
   * granted outside eXo) and that the owner recorded in eXo from the drawer,
   * without touching the server.
   */
  ADOPTED;

}
