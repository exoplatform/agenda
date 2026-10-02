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
 * What agenda did with an invitation received by mail (EXO-90866).
 *
 * @param eventId the agenda event landed, removed or already held
 * @param link agenda's own page for the event, built from the platform's
 *          domain; null when the event was removed or no link can be built
 * @param removed true when the event was removed: its organiser cancelled it
 * @param alreadyHeld true when nothing was written because the message names
 *          one of this deployment's own events, in agenda already
 */
public record LandedMailInvitation(long eventId, String link, boolean removed, boolean alreadyHeld) {
}
