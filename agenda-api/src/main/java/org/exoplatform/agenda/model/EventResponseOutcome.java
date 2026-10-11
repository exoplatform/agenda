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

import java.time.ZonedDateTime;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * What an answer stored, as decided under the lock of its event: on an event
 * with a capacity limit a Yes is stored as
 * {@link EventAttendeeResponse#WAITING} on a date with no seat left.
 *
 * @param response the answer stored at the date answered, or at the date the
 *          series answer is read at when no date was answered
 * @param firstWaitingOccurrenceId the occurrence id of the first date of a
 *          series the answer left waiting, null when it left none or when the
 *          event is not a series nor a date of one
 */
public record EventResponseOutcome(EventAttendeeResponse response, ZonedDateTime firstWaitingOccurrenceId) {
}
