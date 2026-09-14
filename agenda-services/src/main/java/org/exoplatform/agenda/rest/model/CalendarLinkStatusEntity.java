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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the drawer is told about the link of a calendar, and only ever told
 * someone allowed to manage it. Never the token's digest or encrypted copy; the
 * URL while the link answers and can be displayed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarLinkStatusEntity {

  /** Technical identifier of the calendar. */
  private long    calendarId;

  /** Whether the calendar has a link at all. */
  private boolean exists;

  /** Whether the link still answers; false when it does not exist. */
  private boolean active;

  /**
   * Whether the URL of an answering link can be shown: false when its stored
   * copy no longer decrypts, which only a reset repairs.
   */
  private boolean displayable;

  /** Identity identifier of the link's creator, 0 when there is no link. */
  private long    creatorId;

  /** Display name of the link's creator, null when unknown or no link. */
  private String  creatorName;

  /** Creation date in milliseconds since the epoch, 0 when there is no link. */
  private long    createdDate;

  /** The link's URL, set only while it answers and can be displayed. */
  private String  url;

  /**
   * The calendar's own name, else the title agenda derives for it; set by the
   * listing only.
   */
  private String  calendarTitle;

  /**
   * Whether the calendar is its owner's default one with no name of its own —
   * the one a page labels "My calendar"; set by the listing only.
   */
  private boolean systemCalendar;

  /** {@code PERSONAL} or {@code SPACE}; set by the listing only. */
  private String  calendarKind;

  /** Display name of the space owning a space calendar; set by the listing only. */
  private String  spaceDisplayName;

}
