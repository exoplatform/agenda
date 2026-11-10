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

import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * What a "this and upcoming" change asks of the series that continues a
 * recurring event from one of its dates on. The rest of the continuation is
 * the series': its creator, its calendar, its attendees and their answers.
 *
 * @param event the fields edited on the continuation: its title,
 *          description, location, dates and times, time zone, all-day flag,
 *          recurrence, open flag, limit and participant permissions; any
 *          other field is ignored. Null dates continue the series' dates
 *          from the split date, at its times. A null time zone keeps the
 *          series' one. A null recurrence keeps the series' rule. A count
 *          equal to the series' is shared between the two series; any other
 *          count is the number of dates of the continuation alone. When the
 *          first date moves to another day, the split date's weekday moves
 *          with it in a weekly rule, and its day of the month in a monthly
 *          rule, or in a yearly rule naming that one day, when the rule names
 *          no weekday and counts its days from the first of the month; any
 *          other rule keeps the days it names. A rule with an interval of 2
 *          or more then counts it from the period of its frequency the moved
 *          date falls in, such as its week or its month
 * @param conferences the continuation's conferences, null to keep the
 *          series'
 * @param reminders the reminders of the user splitting, null to keep the ones
 *          they hold on the series
 * @param addedIdentityIds the identities the change invites
 * @param removedIdentityIds the identities the change removes
 * @param readZone the zone the series was read in, which the change was
 *          written in; null when unknown. A change in that zone, or in none,
 *          keeps the series' zone, and a rule naming the one weekday or day
 *          its first date falls on in that zone, as the event form derives it
 *          from the date, names the one it falls on in the series' zone. A
 *          list of weekdays names the event's own, in the series' zone
 */
public record EventSplit(Event event,
                         List<EventConference> conferences,
                         List<EventReminder> reminders,
                         List<Long> addedIdentityIds,
                         Set<Long> removedIdentityIds,
                         ZoneId readZone) {
}
