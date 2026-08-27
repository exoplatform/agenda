/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.exception;

/**
 * A tokenised invitation link that is no longer answerable, because the meeting
 * it answers is over.
 *
 * <p>
 * It is a subclass of {@link IllegalAccessException} rather than a new kind of
 * failure, and that is deliberate: every caller which already refuses a bad
 * token goes on refusing this one without being changed, so the fix cannot
 * accidentally open a path that used to be closed. What the subclass buys is
 * the ability of a caller that cares — the REST layer answering somebody who
 * clicked a link in good faith — to tell "this link is too old" apart from
 * "this token is forged", and to say so.
 *
 * <p>
 * The distinction is safe to expose. A holder learns only that the invitation
 * they already hold has passed its event, which the event's own date in the
 * same mail already told them; it discloses nothing about whether the token
 * was ever valid, because the format, event and answer checks all run before
 * this one.
 */
public class EventInvitationExpiredException extends IllegalAccessException {

  private static final long serialVersionUID = 1L;

  /**
   * Builds the exception with a message meant for the server log, never for the
   * holder of the link.
   *
   * @param message description of which token was refused and when it had
   *          lapsed
   */
  public EventInvitationExpiredException(String message) {
    super(message);
  }
}
