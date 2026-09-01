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
package org.exoplatform.agenda.service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.ScheduleConflict;
import org.exoplatform.agenda.model.ScheduleConflictEvent;
import org.exoplatform.agenda.model.ScheduleConflicts;

/**
 * The one place a schedule clash is decided.
 * <p>
 * See {@link AgendaScheduleConflictService} for the rule, for everything that
 * is deliberately not a clash, and for why this is arithmetic in code rather
 * than a description of arithmetic handed to a caller.
 */
@Service
public class AgendaScheduleConflictServiceImpl implements AgendaScheduleConflictService {

  /**
   * Clashes are decided on absolute instants, so events are read in UTC — the
   * same convention as free/busy. The asking user's own time zone matters only
   * when a result is rendered, which happens above this service.
   */
  private static final ZoneOffset TIMEZONE             = ZoneOffset.UTC;

  /**
   * The events read to answer one user over one window. A calendar dense
   * enough to reach this holds more clashes than anyone acts on in one sitting,
   * so hitting it is reported ({@link ScheduleConflicts#isTruncated()}) rather
   * than paged: the useful next move is a shorter window, not a second page.
   */
  private static final int        CONFLICT_QUERY_LIMIT = 500;

  /**
   * The longest window this service will scan in one call. A quarter is well
   * past any question a person actually asks of their own calendar ("today",
   * "this week", "before I go on leave"), and the cap keeps a caller from
   * turning one sloppy date into a scan of years.
   */
  private static final int        MAX_WINDOW_DAYS      = 92;

  private final AgendaEventService         agendaEventService;

  private final AgendaEventAttendeeService agendaEventAttendeeService;

  /**
   * Builds the service.
   *
   * @param agendaEventService the event service the window is read from, and
   *          the holder of the calendar ACL that answers for this read
   * @param agendaEventAttendeeService used to find what the asking user
   *          answered to each event, which is what keeps a declined event out
   *          of the report
   */
  @Autowired
  public AgendaScheduleConflictServiceImpl(AgendaEventService agendaEventService,
                                           AgendaEventAttendeeService agendaEventAttendeeService) {
    this.agendaEventService = agendaEventService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ScheduleConflicts getScheduleConflicts(ZonedDateTime start,
                                                ZonedDateTime end,
                                                long userIdentityId) throws IllegalAccessException {
    checkWindow(start, end);
    // A declined event is filtered twice: the query drops it, and so does
    // occupies() below. The duplication is deliberate — the query filter is a
    // performance choice that a later change could reasonably revisit, while
    // "a decline is not a clash" is the rule, and the rule must not be one
    // edit to a filter list away from disappearing.
    EventFilter filter = new EventFilter(userIdentityId,
                                         null,
                                         List.of(EventAttendeeResponse.ACCEPTED,
                                                 EventAttendeeResponse.TENTATIVE,
                                                 EventAttendeeResponse.NEEDS_ACTION),
                                         start,
                                         end,
                                         CONFLICT_QUERY_LIMIT);
    List<Event> events = agendaEventService.getEvents(filter, TIMEZONE, userIdentityId);
    if (events == null) {
      return new ScheduleConflicts(List.of(), false);
    }
    List<ScheduleConflictEvent> candidates = new ArrayList<>();
    for (Event event : events) {
      if (!occupiesTime(event)) {
        continue;
      }
      // Read after the cheap tests, never before: this is one service call per
      // surviving event, and there is no reason to make it for an event that
      // is already out of the report.
      EventAttendeeResponse response = responseOf(event, userIdentityId);
      if (response == EventAttendeeResponse.DECLINED) {
        continue;
      }
      candidates.add(new ScheduleConflictEvent(event, response, event.getCreatorId() == userIdentityId));
    }
    return new ScheduleConflicts(group(candidates), events.size() >= CONFLICT_QUERY_LIMIT);
  }

  /**
   * The strict overlap rule, written once so there is one place to read it and
   * one place to get it wrong.
   * <p>
   * {@code a.start < b.end && b.start < a.end}. Both comparisons are strict,
   * which is exactly what makes 10:00 - 11:00 and 11:00 - 12:00 <em>not</em> a
   * clash: the first ends at the instant the second begins, and no instant
   * belongs to both. Loosening either comparison to "or equal" turns every
   * back-to-back pair on every calendar into a reported conflict.
   *
   * @param aStart start of the first interval
   * @param aEnd end of the first interval
   * @param bStart start of the second interval
   * @param bEnd end of the second interval
   * @return {@code true} when the two intervals share at least one instant
   */
  private static boolean overlaps(ZonedDateTime aStart, ZonedDateTime aEnd, ZonedDateTime bStart, ZonedDateTime bEnd) {
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
  }

  /**
   * Groups the events into clashes, transitively.
   * <p>
   * The events are sorted by start and swept once, carrying the group's
   * envelope. Because the sweep is in start order, the next event's start is
   * never before the group's, so testing it against the envelope with
   * {@link #overlaps} answers the same question as testing it against every
   * member — which is what makes this linear pass a connected-components
   * grouping rather than a chain of pairs.
   * <p>
   * The envelope's end is the <strong>running maximum</strong>, not the last
   * event's end. Carrying the last event's end instead would close a group at
   * the first short event and split a real tangle into pieces: A 09:00 -
   * 13:00, B 10:00 - 10:30, C 12:00 - 13:00 would come back as two clashes
   * that both name A.
   * <p>
   * A group of one is not a clash and is dropped.
   *
   * @param candidates the events that occupy the user's time, in any order
   * @return one entry per clash, ordered by the time the clash starts
   */
  private List<ScheduleConflict> group(List<ScheduleConflictEvent> candidates) {
    List<ScheduleConflictEvent> sorted = candidates.stream()
                                                   .sorted(Comparator.comparing((ScheduleConflictEvent candidate) -> candidate.getEvent()
                                                                                                                             .getStart())
                                                                     .thenComparing(candidate -> candidate.getEvent().getEnd()))
                                                   .toList();
    List<ScheduleConflict> conflicts = new ArrayList<>();
    List<ScheduleConflictEvent> group = new ArrayList<>();
    ZonedDateTime groupStart = null;
    ZonedDateTime groupEnd = null;
    for (ScheduleConflictEvent candidate : sorted) {
      ZonedDateTime candidateStart = candidate.getEvent().getStart();
      ZonedDateTime candidateEnd = candidate.getEvent().getEnd();
      if (groupEnd != null && overlaps(groupStart, groupEnd, candidateStart, candidateEnd)) {
        group.add(candidate);
        groupEnd = candidateEnd.isAfter(groupEnd) ? candidateEnd : groupEnd;
      } else {
        closeGroup(conflicts, group, groupStart, groupEnd);
        group = new ArrayList<>();
        group.add(candidate);
        groupStart = candidateStart;
        groupEnd = candidateEnd;
      }
    }
    closeGroup(conflicts, group, groupStart, groupEnd);
    return conflicts;
  }

  /**
   * Emits the group being swept, if it turned out to be a clash.
   *
   * @param conflicts the report being built
   * @param group the events collected so far
   * @param start the group's envelope start
   * @param end the group's envelope end
   */
  private void closeGroup(List<ScheduleConflict> conflicts,
                          List<ScheduleConflictEvent> group,
                          ZonedDateTime start,
                          ZonedDateTime end) {
    if (group.size() > 1) {
      conflicts.add(new ScheduleConflict(List.copyOf(group), start, end));
    }
  }

  /**
   * Tells whether an event holds a block of the user's time, and so can clash
   * with another.
   * <p>
   * Each exclusion is stated in {@link AgendaScheduleConflictService}; this is
   * where all but one of them are enforced — the decline is decided by the
   * caller, which is where the user's answer is known. The duration test is
   * not defensive tidying: an instantaneous event overlaps nothing under the
   * strict rule, and letting one into the sweep would make the grouping of the
   * events around it depend on which side of the tie the sort happened to put
   * it.
   *
   * @param event the event read from the window
   * @return {@code true} when the event holds the user's time
   */
  private boolean occupiesTime(Event event) {
    return event.getStart() != null
        && event.getEnd() != null
        && event.getStart().isBefore(event.getEnd())
        && event.getStatus() != EventStatus.CANCELLED
        && event.getAvailability() != EventAvailability.FREE;
  }

  /**
   * Finds what the asking user answered to an event.
   * <p>
   * Attendees are held by the series, not by the occurrence, so a materialised
   * occurrence — {@code id} 0, series in {@code parentId} — is looked up under
   * its parent. The occurrence date is passed on, because an attendee can be
   * added to or removed from a series partway through and
   * {@link EventAttendeeList#getEventAttendee} is what knows it.
   * <p>
   * {@code null} means the user is not an attendee at all, which is normal for
   * an event on a calendar of a space they belong to. It is not a decline, and
   * must never be turned into one.
   *
   * @param event the event read from the window
   * @param userIdentityId the asking user
   * @return the user's answer, or {@code null} when they were never asked
   */
  private EventAttendeeResponse responseOf(Event event, long userIdentityId) {
    long eventId = event.getId() > 0 ? event.getId() : event.getParentId();
    if (eventId <= 0) {
      return null;
    }
    EventAttendeeList attendees = agendaEventAttendeeService.getEventAttendees(eventId);
    if (attendees == null) {
      return null;
    }
    EventAttendee attendee = attendees.getEventAttendee(userIdentityId,
                                                        event.getOccurrence() == null ? null : event.getOccurrence().getId());
    return attendee == null ? null : attendee.getResponse();
  }

  /**
   * Refuses a window this service will not answer for.
   *
   * @param start window start
   * @param end window end
   * @throws IllegalArgumentException when the window is missing, inverted or
   *           too long
   */
  private void checkWindow(ZonedDateTime start, ZonedDateTime end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("agenda.scheduleConflicts.windowMandatory");
    }
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("agenda.scheduleConflicts.windowEndsBeforeItStarts");
    }
    if (Duration.between(start, end).toDays() > MAX_WINDOW_DAYS) {
      throw new IllegalArgumentException("agenda.scheduleConflicts.windowTooLong");
    }
  }

}
