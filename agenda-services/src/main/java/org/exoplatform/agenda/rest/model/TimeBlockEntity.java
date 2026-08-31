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
package org.exoplatform.agenda.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A busy range as it leaves the server: two instants, in ISO-8601 with an
 * offset, and nothing else.
 * <p>
 * <strong>Two fields is the whole security property of this feature.</strong>
 * Somebody else's calendar is being painted on this user's screen, and the
 * only reason that is defensible is that a block says <em>when</em> and can
 * never say <em>what</em>. Any field added here — a title, a location, a
 * calendar name, an event id that could be dereferenced — would break that,
 * so the class stays deliberately unable to carry one, and a test asserts its
 * serialised key set.
 * <p>
 * The bounds are strings rather than {@code ZonedDateTime} so that what the
 * browser receives does not depend on whichever Jackson time module happens to
 * be registered in the container.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeBlockEntity {

  private String start;

  private String end;

}
