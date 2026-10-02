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
 * The event a user's personal calendar holds for a mail invitation, as the mail
 * reader asks about it when the invitation is opened (EXO-90873).
 *
 * @param eventId the agenda event
 * @param link where the event is read in the platform, built from the
 *          platform's domain and the event's id
 * @param response the user's answer to it, as agenda holds it; null when it
 *          cannot be read
 * @param sequence the SEQUENCE landed with it, 0 when none was kept
 */
public record HeldMailInvitation(long eventId, String link, EventAttendeeResponse response, int sequence) {
}
