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
 * A calendar subscription as whoever manages it sees it (EXO-90278): its user,
 * or a space's managers (EXO-90373). It carries the URL, which may embed a
 * secret: responses holding one are never cached.
 */
@Data
@NoArgsConstructor
public class CalendarSubscriptionStatusEntity {

  /** Technical identifier of the subscription. */
  private long    id;

  /** The calendar its events are imported into. */
  private long    calendarId;

  /** Identity identifier of the owner: the user, or the space. */
  private long    ownerId;

  /** Identity identifier of the user who added it. */
  private long    creatorId;

  /** Username of the user who added it. */
  private String  creatorUsername;

  /** Full name of the user who added it. */
  private String  creatorFullName;

  /** The calendar name. */
  private String  name;

  /** The calendar colour. */
  private String  color;

  /** The URL, null when it can no longer be decrypted. */
  private String  url;

  /** When the feed was last imported, epoch milliseconds, 0 for never. */
  private long    lastSuccessDate;

  /** When a refresh was last attempted, epoch milliseconds, 0 for never. */
  private long    lastAttemptDate;

  /** When the next refresh is due, epoch milliseconds. */
  private long    nextRefreshDate;

  /** Message code of the last failure, null when the last refresh succeeded. */
  private String  lastError;

  /** Whether the last import left out occurrences beyond the limit. */
  private boolean truncated;

  /** When the user subscribed, epoch milliseconds. */
  private long    createdDate;

}
