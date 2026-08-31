/**
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
package org.exoplatform.agenda.constant;

/**
 * What actually happened when one user's busy time was asked for on behalf of
 * another — the per-user status of a busy-time read.
 * <p>
 * <strong>This enum exists so that an empty list can never stand for anything
 * but "nothing was in the way".</strong> It is the same distinction
 * {@code Optional.empty()} carries inside
 * {@code AgendaAvailabilityServiceImpl.readBusyBlocksIfDisclosed} (EXO-89844)
 * and that {@code {events, failed}} carries between a connector and a view
 * (EXO-89843), made explicit for one user at a time so that a screen can name
 * whom it could not check.
 * <p>
 * Only {@link #DISCLOSED} carries blocks. The other two carry none, and a
 * caller that renders them the way it renders "disclosed, and the list was
 * empty" turns a person who withheld their calendar into a person who is
 * free.
 */
public enum AvailabilityDisclosure {

  /**
   * A calendar was read. The blocks are what it holds over the window, which
   * may legitimately be none: this — and only this — is the answer "they have
   * nothing on".
   */
  DISCLOSED("disclosed"),

  /**
   * Nothing was read, because this user's busy time is not disclosed to the
   * asking user. A choice, not an incident: the calendar ACL did not open it
   * and the target's own sharing setting did not widen it.
   */
  NOT_DISCLOSED("not_disclosed"),

  /**
   * Nothing was read, because the read broke. A failure, not a choice, and
   * not an answer: the same user asked again a minute later may well be
   * disclosed.
   */
  FAILED("failed");

  private final String value;

  /**
   * Builds the constant.
   *
   * @param value the token this constant is exchanged as
   */
  AvailabilityDisclosure(String value) {
    this.value = value;
  }

  /**
   * Returns the token this constant is exchanged as over REST. It is the
   * lower-case, underscored spelling rather than {@link #name()} so that
   * {@code not_disclosed} is spelt the one way it is already spelt in the MCP
   * conflicts payload (EXO-89841) — one vocabulary for one idea.
   *
   * @return the exchanged token, never {@code null}
   */
  public String getValue() {
    return value;
  }

}
