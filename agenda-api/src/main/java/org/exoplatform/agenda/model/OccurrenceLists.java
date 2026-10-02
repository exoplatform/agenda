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

import java.util.List;

/**
 * The three lists an exceptional occurrence is cloned with, read for a row and
 * for the series it came from, so that a series save can tell a row a user
 * touched from one the reminder job materialised.
 *
 * @param attendees the attendees, with their answers
 * @param conferences the web conferences
 * @param reminders the reminders of every receiver
 */
public record OccurrenceLists(List<EventAttendee> attendees, List<EventConference> conferences, List<EventReminder> reminders) {
}
