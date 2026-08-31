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

  private int                         offset;

  private int                         limit;

  /**
   * Technical identifiers of the calendars whose events must be left out of
   * the result, whatever their owner brings in.
   * <p>
   * Owner-level selection alone cannot express a selection inside one owner:
   * every personal calendar of a user shares the user identity as owner, so
   * asking for that owner asks for all of them at once. This is the
   * calendar-level counterpart, and it is a subtraction on purpose — it
   * composes with any owner selection, including the implicit "every calendar
   * I can see" one, which no inclusion list could enumerate.
   */
  private List<Long>                  excludedCalendarIds;

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

  public EventFilter(List<Long> ownerIds,
                     ZonedDateTime start,
                     ZonedDateTime end) {
    this.ownerIds = ownerIds;
    this.start = start;
    this.end = end;
  }

  public EventFilter(List<Long> ownerIds,
                     int offset,
                     int limit) {
    this.ownerIds = ownerIds;
    this.offset = offset;
    this.limit = limit;
  }

  /**
   * @return {@code true} when the filter carries both a start and an end date,
   *         hence designates a bounded period
   */
  public boolean isUseDates() {
    return start != null && end != null;
  }

  /**
   * Copies this filter, so a caller can narrow a copy (dates, owners) without
   * mutating the filter it received.
   *
   * @return a new {@link EventFilter} carrying the same criteria
   */
  @Override
  public EventFilter clone() { // NOSONAR
    return new EventFilter(attendeeId,
                           attendeeWithSpacesIds,
                           ownerIds,
                           responseTypes,
                           start,
                           end,
                           offset,
                           limit,
                           excludedCalendarIds);
  }

}
