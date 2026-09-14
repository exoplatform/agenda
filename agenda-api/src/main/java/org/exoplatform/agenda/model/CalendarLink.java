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
import lombok.ToString;

/**
 * The private, read-only link through which a calendar application subscribes
 * to one eXo calendar (an iCal feed).
 * <p>
 * <b>A calendar has at most one link</b>, whoever created it: the link belongs
 * to the calendar, not to the person who clicked. A space calendar's managers
 * all see the same link, and any of them may replace or delete it.
 * <p>
 * <b>The secret is not here.</b> The token a calendar application presents is
 * shown once, when the link is created, and only its SHA-256 digest is kept.
 * Nothing that holds this object can give the link back.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarLink {

  /** Technical identifier of the calendar the link publishes. */
  private long    calendarId;

  /** Identity identifier of the user who created (or last reset) the link. */
  private long    creatorId;

  /** When the link was created or last reset, in milliseconds since the epoch. */
  private long    createdDate;

  /**
   * Lowercase hexadecimal SHA-256 digest of the token. Kept out of
   * {@code toString()} so that logging this object never writes it.
   */
  @ToString.Exclude
  private String  tokenHash;

  /**
   * Whether the link still answers: false once its creator has lost the right
   * that let them create it. Computed on read, never stored.
   */
  private boolean active;

}
