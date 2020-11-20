package org.exoplatform.agenda.model;

import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFilter implements Cloneable {

  private long                        attendeeId;

  private List<Long>                  attendeeWithSpacesIds;

  private List<Long>                  ownerIds;

  private List<EventAttendeeResponse> responseTypes;

  private ZonedDateTime               start;

  private ZonedDateTime               end;

  private int                         limit;

  public EventFilter(long attendeeId,
                     List<Long> ownerIds,
                     List<EventAttendeeResponse> responseTypes,
                     ZonedDateTime start,
                     ZonedDateTime end,
                     int limit) {
    this.attendeeId = attendeeId;
    this.ownerIds = ownerIds;
    this.responseTypes = responseTypes;
    this.start = start;
    this.end = end;
    this.limit = limit;
  }

  @Override
  public EventFilter clone() { // NOSONAR
    return new EventFilter(attendeeId, attendeeWithSpacesIds, ownerIds, responseTypes, start, end, limit);
  }

}
