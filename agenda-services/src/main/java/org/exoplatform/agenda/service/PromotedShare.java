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
package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;

import org.exoplatform.agenda.model.EventAttendee;

/**
 * The seats a decision gave an attendee on one pool.
 *
 * @param before the attendee's waiting row, before the promotion
 * @param promoted the attendee's row after it, from the first date promoted on
 *          the pool of a series
 * @param date when the first date promoted starts, a date moved at its own
 *          start, which orders the pools of one promotion; null on an event
 *          that is not recurring
 * @param dates the number of dates promoted, -1 when they have no last one
 */
record PromotedShare(EventAttendee before, EventAttendee promoted, ZonedDateTime date, int dates) {
}
