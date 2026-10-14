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

import java.io.Serializable;
import java.util.List;

import lombok.*;

/**
 * The body of a "this and upcoming" change of a recurring event.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSplitEntity implements Serializable {

  private static final long         serialVersionUID = 6370285148612095703L;

  /**
   * The continuing series as the user edited it. Only its title, description,
   * location, dates and times, time zone, all-day flag, recurrence, open flag,
   * limit, participant permissions, conferences and reminders are read; its
   * attendees are given by the two lists below.
   */
  private EventEntity               event;

  /** The participants the change invites. */
  private List<EventAttendeeEntity> addedAttendees;

  /**
   * The participants the change removes, each by its identity's provider and
   * remote ids, or by its identity id.
   */
  private List<EventAttendeeEntity> removedAttendees;

  /** The occurrence id of the continuation's first date. */
  private String                    splitFrom;

  /** Whether the attendees are notified. */
  private boolean                   sendInvitation;
}
