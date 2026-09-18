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

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A subscription to a calendar link (EXO-90278) of a user, or of a space
 * (EXO-90373): the remote iCal feed, the calendar of its owner its events are
 * imported into, and the state of its refreshes.
 * <p>
 * The storage fills every field but {@code url}, {@code name},
 * {@code color} and the creator's names; the service decrypts the URL for
 * whoever manages it, reads the name and colour from the calendar, names the
 * creator, and clears the stored secrets
 * ({@code urlEncrypted}, {@code urlKey}) and the validators before a
 * subscription leaves it. Dates are epoch milliseconds, 0 for never.
 */
@Data
@NoArgsConstructor
public class CalendarSubscription {

  /** Technical identifier of the subscription. */
  private long    id;

  /** The personal calendar the feed's events are imported into. */
  private long    calendarId;

  /**
   * Identity identifier of the user who subscribed: for a personal
   * subscription its owner, for a space's the manager who added it, shown and
   * never used to decide who manages it (EXO-90373).
   */
  private long    userIdentityId;

  /**
   * Identity identifier of the owner of the calendar the feed fills: the user
   * for a personal subscription, the space for a space's. Who may manage the
   * subscription is decided from it (EXO-90373).
   */
  private long    ownerIdentityId;

  /** Username of the user who subscribed, filled for the managers' listing. */
  private String  creatorUsername;

  /** Full name of the user who subscribed, filled for the managers' listing. */
  private String  creatorFullName;

  /** The feed URL in clear, for its owner only; null when it cannot be decrypted. */
  private String  url;

  /** The feed URL encrypted by the platform codec, as stored. */
  private String  urlEncrypted;

  /** SHA-256 of the owner and the normalized URL, unique per owner. */
  private String  urlKey;

  /** Name of the calendar the events are imported into. */
  private String  name;

  /** Colour of the calendar the events are imported into. */
  private String  color;

  /** The entity tag of the last feed read, for a conditional request. */
  private String  etag;

  /** The Last-Modified header of the last feed read, for a conditional request. */
  private String  lastModified;

  /** SHA-256 of the last feed body imported. */
  private String  contentHash;

  /** Refresh interval the feed advertised, in minutes, 0 when it advertised none. */
  private int     refreshMinutes;

  /** When the feed was last read and imported successfully. */
  private long    lastSuccessDate;

  /** When a refresh was last attempted, successful or not. */
  private long    lastAttemptDate;

  /** Message code of the last refresh failure, null when the last refresh succeeded. */
  private String  lastError;

  /** Whether the last import kept only the first occurrences of a feed holding more. */
  private boolean truncated;

  /** When the next refresh is due. */
  private long    nextRefreshDate;

  /** The node refreshing the subscription right now, null when none. */
  private String  claimedBy;

  /** When the current refresh claim was taken, 0 when none. */
  private long    claimedDate;

  /** When the user subscribed. */
  private long    createdDate;

}
