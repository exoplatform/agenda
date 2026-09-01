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

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One event inside a clash: what it is, when it is, where to open it, and whether it is the caller's to move.
// created_by_me is the closest thing Agenda has to "I am the organiser" - there is no organiser field, and the
// creator is the person who may always update the event. Propose a reschedule only where it is true; elsewhere the
// honest proposals are to decline or to ask the organiser. my_response is absent when the user was never invited
// (an event on a space calendar of theirs) - that is not the same as not having answered yet (NEEDS_ACTION).
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(value = Include.NON_EMPTY)
public class ScheduleConflictEventModel {

  @JsonProperty("event_id")
  private long                  eventId;

  @JsonProperty("occurrence_id")
  private String                occurrenceId;

  private String                summary;

  private String                start;

  private String                end;

  @JsonProperty("all_day")
  private boolean               allDay;

  private String                url;

  @JsonProperty("space_id")
  private long                  spaceId;

  @JsonProperty("created_by_me")
  private boolean               createdByMe;

  @JsonProperty("my_response")
  private EventAttendeeResponse myResponse;

}
