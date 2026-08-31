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

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

/**
 * How widely a user lets the platform disclose <em>when they are busy</em> —
 * time ranges and nothing else.
 * <p>
 * Whatever the value, what is ever disclosed is the same: busy and free
 * blocks. Never a title, a location, an attendee or a calendar name. This
 * enum only widens or narrows <em>who</em> may ask, never <em>what</em> comes
 * back, which is what makes counting events materialised from a connected
 * account defensible: their content is never shown.
 * <p>
 * The values are ordered from the widest to the narrowest so that the
 * declaration order matches how the choice reads to a user.
 */
public enum AvailabilitySharing {

  /**
   * Anyone on the platform may see when this user is busy.
   */
  EVERYONE("everyone"),

  /**
   * Only the people who are members of at least one of the same spaces may
   * see when this user is busy. This is the default: it matches how the rest
   * of the product decides who sees a user's activity, and a default of
   * {@link #NOBODY} would leave a setting nobody has a reason to open turning
   * agent-assisted scheduling off for everybody.
   */
  SHARED_SPACES("shared-spaces"),

  /**
   * Nobody may see when this user is busy. Their calendar remains readable by
   * the rules that apply anyway — chiefly, by themselves.
   */
  NOBODY("nobody");

  /**
   * What a user gets when they have never touched the setting.
   */
  public static final AvailabilitySharing DEFAULT = SHARED_SPACES;

  private final String                    value;

  /**
   * Builds the constant.
   *
   * @param value the token this constant is stored and exchanged as
   */
  AvailabilitySharing(String value) {
    this.value = value;
  }

  /**
   * Returns the token this constant is stored and exchanged as. It is the
   * lower-case, hyphenated spelling rather than {@link #name()} so that the
   * stored value, the REST payload and the setting the user reads about are
   * one and the same string.
   *
   * @return the stored token, never {@code null}
   */
  public String getValue() {
    return value;
  }

  /**
   * Resolves a stored or submitted token.
   * <p>
   * An unrecognised token is deliberately <strong>not</strong> mapped onto
   * {@link #DEFAULT}: absence of a value means "this user never chose", which
   * is what the default answers, whereas a value that exists but cannot be
   * read is a broken store, and answering a broken store with the sharing
   * default would widen a disclosure on the strength of a typo. The caller
   * decides, and this method only says whether it knew the token.
   *
   * @param value the token to resolve, may be {@code null} or blank
   * @return the matching constant, or {@link Optional#empty()} when the token
   *         is missing or unknown
   */
  public static Optional<AvailabilitySharing> parse(String value) {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    return Stream.of(values()).filter(sharing -> StringUtils.equalsIgnoreCase(sharing.value, value.trim())).findFirst();
  }

}
