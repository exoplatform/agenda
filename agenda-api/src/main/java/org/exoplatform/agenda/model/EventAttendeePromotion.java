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

/**
 * The seats one decision gave an attendee waiting for them, on one event: on
 * a recurring event, every date the attendee got a seat for, the dates its
 * series carries and the dates stored on a row of their own alike.
 *
 * @param attendee the attendee's row holding the first date promoted, as
 *          stored after the promotion: on the row of a series, its
 *          {@link EventAttendee#getFromOccurrenceId()} is that date; on the
 *          row of a date, the date is the row's
 * @param otherDates the number of dates promoted after the first one, 0 on an
 *          event that is not recurring
 * @param openEnded true when the dates promoted have no last one: the seat
 *          was given from the first date on, on a series with no end, and
 *          {@code otherDates} counts none of the dates after it
 */
public record EventAttendeePromotion(EventAttendee attendee, int otherDates, boolean openEnded) {
}
