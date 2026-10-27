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
 * The event a call was held for, as much of it as a link to it needs: its
 * title, its dates, and the address that opens it, or opens the occurrence of
 * a recurring event.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConferenceEventEntity {

  /** The event, or the stored occurrence; 0 for a computed occurrence. */
  private long    id;

  /** The series of an occurrence; 0 for a single event or a series. */
  private long    parentId;

  /** The date identifying the occurrence, RFC 3339 in UTC; null otherwise. */
  private String  occurrenceId;

  private String  title;

  /** Start, RFC 3339 in UTC. */
  private String  start;

  /** End, RFC 3339 in UTC. */
  private String  end;

  private boolean allDay;

  /** The address opening the event or the occurrence, relative to the server. */
  private String  url;

}
