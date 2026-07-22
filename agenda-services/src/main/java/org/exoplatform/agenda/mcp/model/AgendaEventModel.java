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
import com.fasterxml.jackson.annotation.JsonProperty;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.meeds.mcp.server.tool.model.UserModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(value = Include.NON_EMPTY)
public class AgendaEventModel {

  @JsonProperty("event_id")
  private long                           id;

  @JsonProperty("parent_event_id")
  private long                           parentEventId;

  private long                           spaceId;

  private String                         summary;

  private String                         description;

  private String                         start;

  private String                         end;

  private String                         location;

  private String                         url;

  @JsonProperty("conference_url")
  private String                         conferenceUrl;

  @JsonProperty("is_all_day")
  private boolean                        allDay;

  private List<AgendaEventAttendeeModel> attendees;

  @JsonProperty("user_answer")
  private EventAttendeeResponse          userAnswer;

  private UserModel                      creator;

  // Optional conflict report populated on conflict-aware create/update; omitted (null) on plain reads
  private ConflictsModel                 conflicts;

}
