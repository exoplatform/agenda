package org.exoplatform.agenda.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.exoplatform.agenda.constant.EventAttendeeResponse;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFilter {
  private Long                        attendeeIdentityId;

  private List<Long>                  ownerIds;

  private List<EventAttendeeResponse> responseTypes;

  private ZonedDateTime               startDateTime;

  private ZonedDateTime               endDateTime;
}
