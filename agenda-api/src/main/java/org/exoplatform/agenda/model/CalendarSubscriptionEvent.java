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

/**
 * One occurrence a subscription imported (EXO-90278): which agenda event holds
 * it, its stable identity in the feed, and the digest of what was imported, so
 * that a refresh updates the event rather than duplicating it, rewrites it only
 * when it changed, and removes it when the feed no longer carries it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSubscriptionEvent {

  /** Technical identifier of the row. */
  private long   id;

  /** The subscription the occurrence was imported by. */
  private long   subscriptionId;

  /** The agenda event holding the occurrence. */
  private long   eventId;

  /** SHA-256 of the subscription, the UID and the recurrence instant. */
  private String eventKey;

  /** SHA-256 of the imported fields. */
  private String contentHash;

}
