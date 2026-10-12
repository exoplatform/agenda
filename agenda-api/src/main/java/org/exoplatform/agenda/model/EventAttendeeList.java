package org.exoplatform.agenda.model;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import lombok.AllArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
public class EventAttendeeList {

  public static final EventAttendeeList EMPTY_ATTENDEE_LIST = new EventAttendeeList(Collections.emptyList());

  @Setter
  private List<EventAttendee>           attendees;

  public List<EventAttendee> getEventAttendees() {
    return getEventAttendees(null);
  }

  /**
   * @param occurrenceId the occurrence id of a date, or null
   * @return the rows valid at the date, from inclusive to until exclusive,
   *         whatever their number; every row when no date is given
   */
  public List<EventAttendee> getEventAttendees(ZonedDateTime occurrenceId) {
    if (isEmpty()) {
      return Collections.emptyList();
    } else if (occurrenceId == null) {
      return attendees;
    } else {
      return attendees.stream()
                      .filter(eventAttendee -> isValidAt(eventAttendee, occurrenceId))
                      .collect(Collectors.toList());
    }
  }

  private static boolean isValidAt(EventAttendee eventAttendee, ZonedDateTime occurrenceId) {
    if (eventAttendee.getFromOccurrenceId() == null && eventAttendee.getUntilOccurrenceId() == null) {
      return true;
    } else if (eventAttendee.getFromOccurrenceId() == null && eventAttendee.getUntilOccurrenceId() != null) {
      return eventAttendee.getUntilOccurrenceId().isAfter(occurrenceId);
    } else if (eventAttendee.getFromOccurrenceId() != null && eventAttendee.getUntilOccurrenceId() == null) {
      return eventAttendee.getFromOccurrenceId().isBefore(occurrenceId)
          || eventAttendee.getFromOccurrenceId().isEqual(occurrenceId);
    } else {
      return (eventAttendee.getFromOccurrenceId().isBefore(occurrenceId)
          || eventAttendee.getFromOccurrenceId().isEqual(occurrenceId))
          && eventAttendee.getUntilOccurrenceId().isAfter(occurrenceId);
    }
  }

  public List<EventAttendee> getEventAttendees(long identityId) {
    return attendees.stream()
                    .filter(eventAttendee -> eventAttendee.getIdentityId() == identityId)
                    .collect(Collectors.toList());
  }

  public EventAttendee getEventAttendee(long identityId, ZonedDateTime occurrenceId) {
    if (isEmpty()) {
      return null;
    } else if (occurrenceId == null) {
      return attendees.stream()
                      .filter(eventAttendee -> eventAttendee.getIdentityId() == identityId)
                      .findFirst()
                      .orElse(null);
    } else {
      return attendees.stream()
                      .filter(eventAttendee -> eventAttendee.getIdentityId() == identityId
                          && isValidAt(eventAttendee, occurrenceId))
                      .findFirst()
                      .orElse(null);
    }
  }

  public boolean isEmpty() {
    return CollectionUtils.isEmpty(attendees);
  }

  public int size() {
    return isEmpty() ? 0 : attendees.size();
  }
}
