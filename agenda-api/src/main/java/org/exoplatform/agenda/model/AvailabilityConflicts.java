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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The clash report over a proposed window, for the attendees whose
 * availability the asking user is allowed to read.
 * <p>
 * The report is deliberately partial and says so. Availability is readable
 * only where the platform calendar ACL allows it, so an attendee the asker
 * cannot read is neither reported busy nor assumed free: their identifier is
 * listed in {@code notDisclosedIdentityIds} and left out of
 * {@code conflicts}. A caller that presents this to a user must say that
 * those attendees were not checked, rather than imply they are available.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityConflicts {

  private List<AvailabilityConflict> conflicts;

  private List<Long>                 notDisclosedIdentityIds;

  /**
   * Tells whether every attendee that could actually be checked is free over
   * the window.
   * <p>
   * This says nothing about the attendees in
   * {@code notDisclosedIdentityIds}: it is "no clash was found", not
   * "everyone is free".
   *
   * @return {@code true} when no clash was found among the readable attendees
   */
  public boolean isAllAvailable() {
    return conflicts == null || conflicts.isEmpty();
  }

}
