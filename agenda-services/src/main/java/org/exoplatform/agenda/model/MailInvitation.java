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
 * A calendar invitation a user received by mail and wants in their eXo
 * calendar — answered, added as it is, or removed because its organiser
 * cancelled it (EXO-90866). Agenda's own words for what the mail reader hands
 * over, so that only the plugin bridging the two add-ons names the reader's
 * types.
 * <p>
 * Every field but {@code username} is the sender's content or was read from
 * it: a claim, not a fact.
 *
 * @param username the user who asked, the only person the landing acts for
 * @param attendeeAddress the address of the user's own mailbox, the one the
 *          invitation names them by; may be null
 * @param method the iTIP method as the reader read it, upper-cased, null when
 *          the object names none
 * @param uid the event's UID, as the reader read it
 * @param recurrenceId the RECURRENCE-ID as written when the mail is about one
 *          occurrence, null for a series or a single event
 * @param sequence the SEQUENCE as the reader read it
 * @param response the answer given, null when the user only asked to add or
 *          remove the event
 * @param icalendar the iCalendar object, as text
 */
public record MailInvitation(String username,
                             String attendeeAddress,
                             String method,
                             String uid,
                             String recurrenceId,
                             int sequence,
                             EventAttendeeResponse response,
                             String icalendar) {

  /** The iTIP method by which an organiser calls an event off. */
  public static final String CANCEL = "CANCEL";

  /**
   * Whether the message is its organiser's cancellation.
   *
   * @return true for a CANCEL
   */
  public boolean isCancellation() {
    return CANCEL.equals(method);
  }
}
