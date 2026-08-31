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
 * One user's availability over a window: the time they are busy, and the time
 * they are not.
 * <p>
 * Only time ranges are carried — never an event, its title or its calendar —
 * so that disclosing availability never discloses event content.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAvailability {

  private long            identityId;

  private List<TimeBlock> busy;

  private List<TimeBlock> free;

}
