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
package org.exoplatform.agenda.rest.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A calendar share as the owner's drawer lists it (EXO-90357): the colleague,
 * how the share came to be, which channel also carries it, and the warning a
 * failed delivery left.
 */
@Data
@NoArgsConstructor
public class CalendarShareeEntity {

  /** Technical identifier of the calendar. */
  private long    calendarId;

  /** Identity identifier of the colleague. */
  private long    shareeIdentityId;

  /** Username of the colleague. */
  private String  username;

  /** Full name of the colleague. */
  private String  displayName;

  /** Avatar of the colleague. */
  private String  avatarUrl;

  /** Whether the colleague's account is disabled or deleted: the owner may still revoke. */
  private boolean disabled;

  /** When the share was recorded, in milliseconds since the epoch. */
  private long    createdDate;

  /** {@code EXO} or {@code ADOPTED}. */
  private String  source;

  /** The channel that also carries the share, null when eXo only. */
  private String  deliveredTo;

  /** Why the last delivery failed, null when it did not. */
  private String  deliveryWarning;

}
