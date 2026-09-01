/**
 * Copyright (C) 2025 eXo Platform SAS.
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
package org.exoplatform.agenda.mcp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One clash: the events that overlap, and the stretch of the day the tangle covers.
// Membership is transitive, so a group of three may contain two events that do not themselves overlap; start/end are
// the envelope of the group, NOT a window in which every event is present. Say "this stretch of your morning is
// tangled", never "you are triple-booked from X to Y". Events that merely touch are never in a group together.
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(value = Include.NON_EMPTY)
public class ScheduleConflictGroupModel {

  private String                           start;

  private String                           end;

  private List<ScheduleConflictEventModel> events;

}
