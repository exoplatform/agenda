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

  /**
   * Technical identifiers of calendars whose events are wanted on top of what
   * the owner and attendee criteria select (EXO-90357): the calendars other
   * users shared with the reader, which no owner list of the reader's can name
   * and which the reader does not attend.
   * <p>
   * An inclusion, composed with an <em>or</em>: an event is kept when it
   * matches the owner and attendee criteria, or when its calendar is listed
   * here. <b>Validated by the service against the reader</b>: every listed
   * calendar must be one the reader may access or one shared with them, else
   * the whole request is refused.
   */
  private List<Long>                  calendarIds;

  /**
   * Whether an attendee-keyed listing also reads the calendars filled by the
   * subscriptions of the owners it reads (EXO-90373).
   * <p>
   * A subscribed calendar is a read-only copy of somebody else's calendar and
   * its events carry no attendee row, so an attendee-keyed listing reaches
   * them only when the service adds those calendars to {@link #calendarIds},
   * and then whatever response the listing asked for. That is what a member's
   * own agenda wants, and only that view asks for it. Every other caller is
   * left out by default: a reader computing when somebody is busy, a seeding
   * of "the meetings they attend", a listing named after one attendee
   * response.
   * <p>
   * It is a display scope and never an authorisation: the calendars it adds
   * belong to owners the reader was already checked against, so setting it
   * widens nothing the reader may not read.
   */
  private boolean                     subscribedCalendarsIncluded;

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
                           excludedCalendarIds,
                           calendarIds,
                           subscribedCalendarsIncluded);
  }

}
