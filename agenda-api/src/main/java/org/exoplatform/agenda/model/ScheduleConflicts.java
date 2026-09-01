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
 * Every clash found on one user's calendar over one window, and whether the
 * search saw the whole window.
 * <p>
 * <strong>{@link #isTruncated()} exists so that "no more conflicts" and "we
 * stopped looking" cannot be told apart by guessing.</strong> The read behind
 * this report is capped, and a capped read that comes back full has almost
 * certainly left events unexamined; the conflicts reported are still real, but
 * the absence of others is no longer evidence. A caller that presents a
 * truncated report as a clean bill of health is stating something this class
 * never said, and the fix a user needs is a shorter window, not a longer
 * answer.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleConflicts {

  private List<ScheduleConflict> conflicts;

  private boolean                truncated;

}
