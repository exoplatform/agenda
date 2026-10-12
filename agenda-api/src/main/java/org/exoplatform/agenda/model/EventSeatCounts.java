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

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * The seats of one date of an event, as its stored answers hold them.
 *
 * @param acceptedCount the attendees answering
 *          {@link EventAttendeeResponse#ACCEPTED} at the date
 * @param waitingCount the attendees answering
 *          {@link EventAttendeeResponse#WAITING} at the date
 * @param waitingListPosition the rank of the user read on the waiting list of
 *          the date, from 1, in promotion order; null when the user is not
 *          waiting there
 */
public record EventSeatCounts(int acceptedCount, int waitingCount, Integer waitingListPosition) {
}
