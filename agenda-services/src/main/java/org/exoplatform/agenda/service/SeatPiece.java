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

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * A piece of an answer on the pool of a series: a run of stretches with the
 * same outcome, stored as one attendee row over its range.
 *
 * @param from the occurrence id the piece starts at, inclusive, or null from
 *          the first date of the series
 * @param until the occurrence id the piece ends before, exclusive, or null
 *          when it has no end
 * @param response the answer stored over the range
 * @param firstCountedDate the first date of the range a seat was decided at,
 *          or null when the range has none
 */
record SeatPiece(ZonedDateTime from, ZonedDateTime until, EventAttendeeResponse response, ZonedDateTime firstCountedDate) {
}
