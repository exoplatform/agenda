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

import java.util.List;

import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.RemoteEvent;

/**
 * What a full save of an event writes besides the row itself.
 *
 * @param attendees the attendee list saved
 * @param conferences the conferences saved
 * @param reminders the reminders saved
 * @param dateOptions the date options saved
 * @param remoteEvent the remote event saved
 * @param sendInvitation whether the attendees are notified
 * @param confirmClose whether the user confirmed a closing that confirms a
 *          waiting list
 * @param decidesCapacity whether the save runs as a decide step, the event or
 *          a date of it being open or holding a limit, before or after the
 *          save
 */
record EventSave(List<EventAttendee> attendees,
                 List<EventConference> conferences,
                 List<EventReminder> reminders,
                 List<EventDateOption> dateOptions,
                 RemoteEvent remoteEvent,
                 boolean sendInvitation,
                 boolean confirmClose,
                 boolean decidesCapacity) {
}
