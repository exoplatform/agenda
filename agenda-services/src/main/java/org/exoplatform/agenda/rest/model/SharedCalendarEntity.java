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
 * A calendar someone shared with the user, as the "Shared with me" section of
 * the left panel lists it (EXO-90357): the calendar, its owner, and what the
 * sharee chose to do with it.
 */
@Data
@NoArgsConstructor
public class SharedCalendarEntity {

  /** Technical identifier of the calendar. */
  private long    calendarId;

  /** The calendar's name, as its owner's list shows it. */
  private String  name;

  /** The calendar's description. */
  private String  description;

  /** The calendar's colour. */
  private String  color;

  /** Identity identifier of the owner. */
  private long    ownerId;

  /** Username of the owner. */
  private String  ownerUsername;

  /** Full name of the owner. */
  private String  ownerDisplayName;

  /** Avatar of the owner. */
  private String  ownerAvatarUrl;

  /** When the calendar was shared, in milliseconds since the epoch. */
  private long    sharedDate;

  /** The channel that also carries the share, null when eXo only. */
  private String  deliveredTo;

  /** What the channel handed back at delivery — the collection the sharee sees on a CalDAV server. */
  private String  deliveryRef;

  /** Whether the sharee hid the calendar from their agenda. */
  private boolean hidden;

}
