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
package org.exoplatform.agenda.model;

import java.util.List;

import org.exoplatform.agenda.constant.AvailabilityDisclosure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One user's busy time over a window, <strong>and what happened when it was
 * asked for</strong>.
 * <p>
 * The status is not decoration. Three outcomes reach a screen that has to
 * decide whether a slot is free, and two of them look identical without it:
 * <ul>
 * <li>{@link AvailabilityDisclosure#DISCLOSED} with an empty {@link #getBusy()}
 * — a calendar was read and holds nothing over the window. <em>They are
 * free.</em></li>
 * <li>{@link AvailabilityDisclosure#NOT_DISCLOSED} — no calendar was read,
 * because this user does not disclose their busy time to the asking user.
 * <em>Nothing is known.</em></li>
 * <li>{@link AvailabilityDisclosure#FAILED} — no calendar was read, because
 * the read broke. <em>Nothing is known, and it is not their doing.</em></li>
 * </ul>
 * <p>
 * {@link #getBusy()} is {@code null} for the last two, deliberately. An empty
 * list there would be a second spelling of "they are free", one field away
 * from the status that says otherwise, and every incident this delivery has
 * had came from exactly that collapse.
 * <p>
 * Only time ranges are carried — never an event, its title, its location, its
 * guests or its calendar. Disclosing when someone is busy must never disclose
 * what they are doing.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBusyTime {

  private long                   identityId;

  private AvailabilityDisclosure disclosure;

  private List<TimeBlock>        busy;

}
