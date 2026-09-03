/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.model.AvailabilityConflict;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The one place free/busy is computed, and the one place it is guarded.
 * <p>
 * See {@link AgendaAvailabilityService} for the rule and why it is the narrow
 * one.
 */
@Service
public class AgendaAvailabilityServiceImpl implements AgendaAvailabilityService {

  /**
   * The events read to answer one user over one window. Free/busy windows are
   * short (a few days at most), so this is a safety stop, not a page.
   */
  private static final int        BUSY_QUERY_LIMIT  = 500;

  /**
   * The granularity candidate meeting slots are stepped on.
   */
  private static final int        SLOT_STEP_MINUTES = 30;

  /**
   * The hour that separates "mornings" from "afternoons".
   */
  private static final int        NOON              = 12;

  /**
   * Free/busy is reasoned on absolute instants, so events are read in UTC. The
   * asking user's own time zone only matters when a result is rendered, which
   * happens above this service.
   */
  private static final ZoneOffset TIMEZONE          = ZoneOffset.UTC;

  private final AgendaEventService agendaEventService;

  private final IdentityManager    identityManager;

  private final SpaceService       spaceService;

  /**
   * Builds the service.
   *
   * @param agendaEventService the event service the busy times are read from
   * @param identityManager used to resolve the asking and target identities
   * @param spaceService used by the calendar ACL to resolve space membership
   */
  @Autowired
  public AgendaAvailabilityServiceImpl(AgendaEventService agendaEventService,
                                       IdentityManager identityManager,
                                       SpaceService spaceService) {
    this.agendaEventService = agendaEventService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<UserAvailability> getAvailability(List<Long> targetIdentityIds,
                                                ZonedDateTime start,
                                                ZonedDateTime end,
                                                long userIdentityId) throws IllegalAccessException {
    if (CollectionUtils.isEmpty(targetIdentityIds)) {
      throw new IllegalArgumentException("agenda.availability.usersMandatory");
    }
    checkWindow(start, end);
    List<UserAvailability> result = new ArrayList<>();
    for (Long targetIdentityId : targetIdentityIds) {
      checkCanReadAvailability(targetIdentityId, userIdentityId);
      List<TimeBlock> busy = mergeBlocks(getBusyBlocks(targetIdentityId, start, end, userIdentityId));
      result.add(new UserAvailability(targetIdentityId, busy, complement(busy, start, end)));
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<TimeBlock> suggestMeetingTime(List<Long> attendeeIdentityIds,
                                            Duration duration,
                                            ZonedDateTime windowStart,
                                            ZonedDateTime windowEnd,
                                            boolean morningsOnly,
                                            boolean afternoonsOnly,
                                            int limit,
                                            long userIdentityId) throws IllegalAccessException {
    if (CollectionUtils.isEmpty(attendeeIdentityIds)) {
      throw new IllegalArgumentException("agenda.availability.attendeesMandatory");
    }
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("agenda.availability.durationMustBePositive");
    }
    checkWindow(windowStart, windowEnd);
    // Every attendee is checked before any calendar is read: a partial answer
    // would look like a whole one.
    List<TimeBlock> allBusy = new ArrayList<>();
    for (Long attendeeIdentityId : attendeeIdentityIds) {
      checkCanReadAvailability(attendeeIdentityId, userIdentityId);
      allBusy.addAll(getBusyBlocks(attendeeIdentityId, windowStart, windowEnd, userIdentityId));
    }
    return stepCandidateSlots(complement(mergeBlocks(allBusy), windowStart, windowEnd),
                              duration,
                              morningsOnly,
                              afternoonsOnly,
                              limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AvailabilityConflicts getConflicts(List<Long> attendeeIdentityIds,
                                            ZonedDateTime start,
                                            ZonedDateTime end,
                                            long userIdentityId) {
    List<AvailabilityConflict> conflicts = new ArrayList<>();
    List<Long> notDisclosed = new ArrayList<>();
    if (attendeeIdentityIds != null && start != null && end != null) {
      // The same user can be named twice (an organiser who is also listed as
      // an attendee); they are one person and get one line in the report.
      for (Long attendeeIdentityId : attendeeIdentityIds.stream().distinct().toList()) {
        Identity identity = identityManager.getIdentity(String.valueOf(attendeeIdentityId));
        if (identity == null || !identity.isUser()) {
          continue;
        }
        if (!canReadAvailability(attendeeIdentityId, userIdentityId)) {
          notDisclosed.add(attendeeIdentityId);
          continue;
        }
        List<TimeBlock> busy = readBusyBlocksQuietly(attendeeIdentityId, start, end, userIdentityId);
        if (busy.isEmpty()) {
          continue;
        }
        ZonedDateTime overlapStart = busy.stream().map(TimeBlock::getStart).min(Comparator.naturalOrder()).orElse(start);
        ZonedDateTime overlapEnd = busy.stream().map(TimeBlock::getEnd).max(Comparator.naturalOrder()).orElse(end);
        conflicts.add(new AvailabilityConflict(attendeeIdentityId, new TimeBlock(overlapStart, overlapEnd)));
      }
    }
    return new AvailabilityConflicts(conflicts, notDisclosed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventDateOption> rankDateOptionsByAvailability(List<EventDateOption> dateOptions,
                                                             List<Long> attendeeIdentityIds,
                                                             long userIdentityId) {
    if (CollectionUtils.isEmpty(dateOptions) || CollectionUtils.isEmpty(attendeeIdentityIds)) {
      return dateOptions;
    }
    Map<EventDateOption, Long> freeAttendees = new LinkedHashMap<>();
    for (EventDateOption dateOption : dateOptions) {
      long freeCount = 0;
      for (Long attendeeIdentityId : attendeeIdentityIds.stream().distinct().toList()) {
        Identity identity = identityManager.getIdentity(String.valueOf(attendeeIdentityId));
        if (identity == null || !identity.isUser() || !canReadAvailability(attendeeIdentityId, userIdentityId)) {
          continue;
        }
        if (readBusyBlocksQuietly(attendeeIdentityId, dateOption.getStart(), dateOption.getEnd(), userIdentityId).isEmpty()) {
          freeCount++;
        }
      }
      freeAttendees.put(dateOption, freeCount);
    }
    return dateOptions.stream().sorted(Comparator.comparingLong((EventDateOption o) -> freeAttendees.get(o)).reversed()).toList();
  }

  /**
   * Decides whether one user may read another's availability, by asking the
   * platform's own calendar ACL rather than by holding a rule of its own.
   * <p>
   * {@code Utils.canAccessCalendar} answers for a calendar owner: a space
   * calendar is readable by whoever may view the space, and a personal
   * calendar by its owner alone. Availability is derived from a user's own
   * calendar, so the personal-calendar arm is the one that applies and it
   * resolves to identity equality.
   *
   * @param targetIdentityId the user whose availability is wanted
   * @param userIdentityId the user asking
   * @return {@code true} when the asking user may read that availability
   */
  private boolean canReadAvailability(long targetIdentityId, long userIdentityId) {
    return Utils.canAccessCalendar(identityManager, spaceService, targetIdentityId, userIdentityId);
  }

  /**
   * Refuses unless the asking user may read the target's availability.
   *
   * @param targetIdentityId the user whose availability is wanted
   * @param userIdentityId the user asking
   * @throws IllegalAccessException when the read is not allowed
   */
  private void checkCanReadAvailability(long targetIdentityId, long userIdentityId) throws IllegalAccessException {
    if (identityManager.getIdentity(String.valueOf(userIdentityId)) == null) {
      throw new IllegalAccessException("User with identity id " + userIdentityId + " doesn't exist");
    }
    if (!canReadAvailability(targetIdentityId, userIdentityId)) {
      throw new IllegalAccessException("User with identity id " + userIdentityId
          + " is not allowed to read the availability of identity " + targetIdentityId);
    }
  }

  /**
   * Reads one user's busy blocks over a window, clipped to it.
   * <p>
   * Busy means an event this user has ACCEPTED — the creator is auto-accepted,
   * so their own events count — and that is not marked
   * {@link EventAvailability#FREE}. Merely being invited, or having declined,
   * does not make a user busy, and neither does an event its organiser
   * explicitly published as free time.
   * <p>
   * The asking user, not the target, is passed to
   * {@link AgendaEventService#getEvents} as the acting user. That is what
   * makes the event service's own attendee check a real second gate instead of
   * a comparison of the target with itself.
   * <p>
   * Events materialised from a connected remote or CalDAV calendar <em>are</em>
   * included: they are stored as ordinary events carrying their owner as an
   * ACCEPTED attendee, which is exactly what is read here.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @param userIdentityId the user asking, passed through as the acting user
   * @return the busy blocks, unmerged and in no particular order
   * @throws IllegalAccessException when the event service refuses the read
   */
  private List<TimeBlock> getBusyBlocks(long targetIdentityId,
                                        ZonedDateTime start,
                                        ZonedDateTime end,
                                        long userIdentityId) throws IllegalAccessException {
    EventFilter filter = new EventFilter(targetIdentityId,
                                         null,
                                         List.of(EventAttendeeResponse.ACCEPTED),
                                         start,
                                         end,
                                         BUSY_QUERY_LIMIT);
    List<Event> events = agendaEventService.getEvents(filter, TIMEZONE, userIdentityId);
    List<TimeBlock> blocks = new ArrayList<>();
    for (Event event : events) {
      if (event.getAvailability() == EventAvailability.FREE || event.getStart() == null || event.getEnd() == null) {
        continue;
      }
      ZonedDateTime clippedStart = event.getStart().isBefore(start) ? start : event.getStart();
      ZonedDateTime clippedEnd = event.getEnd().isAfter(end) ? end : event.getEnd();
      if (!clippedStart.isBefore(clippedEnd)) {
        continue;
      }
      blocks.add(new TimeBlock(clippedStart, clippedEnd));
    }
    return blocks;
  }

  /**
   * Reads busy blocks for a user already known to be readable, turning a
   * refusal from the event service into an empty result.
   * <p>
   * The ACL was checked by the caller, so a refusal here is not the normal
   * flow; it is swallowed because the enrichment paths that use it must not
   * fail the write they decorate.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @param userIdentityId the user asking
   * @return the busy blocks, empty when the read was refused
   */
  private List<TimeBlock> readBusyBlocksQuietly(long targetIdentityId,
                                                ZonedDateTime start,
                                                ZonedDateTime end,
                                                long userIdentityId) {
    try {
      return getBusyBlocks(targetIdentityId, start, end, userIdentityId);
    } catch (IllegalAccessException e) { // NOSONAR the ACL was already checked; this path must not fail its caller
      return List.of();
    }
  }

  /**
   * Merges overlapping and touching blocks into a sorted, disjoint list.
   *
   * @param blocks the blocks to merge, in any order
   * @return the merged blocks, earliest first
   */
  private List<TimeBlock> mergeBlocks(List<TimeBlock> blocks) {
    List<TimeBlock> sorted = new ArrayList<>(blocks);
    sorted.sort(Comparator.comparing(TimeBlock::getStart));
    List<TimeBlock> merged = new ArrayList<>();
    ZonedDateTime currentStart = null;
    ZonedDateTime currentEnd = null;
    for (TimeBlock block : sorted) {
      if (currentStart == null) {
        currentStart = block.getStart();
        currentEnd = block.getEnd();
      } else if (!block.getStart().isAfter(currentEnd)) {
        if (block.getEnd().isAfter(currentEnd)) {
          currentEnd = block.getEnd();
        }
      } else {
        merged.add(new TimeBlock(currentStart, currentEnd));
        currentStart = block.getStart();
        currentEnd = block.getEnd();
      }
    }
    if (currentStart != null) {
      merged.add(new TimeBlock(currentStart, currentEnd));
    }
    return merged;
  }

  /**
   * Returns what is left of a window once the given busy blocks are removed.
   *
   * @param busyBlocks merged busy blocks, earliest first
   * @param windowStart window start
   * @param windowEnd window end
   * @return the free blocks, earliest first
   */
  private List<TimeBlock> complement(List<TimeBlock> busyBlocks, ZonedDateTime windowStart, ZonedDateTime windowEnd) {
    List<TimeBlock> free = new ArrayList<>();
    ZonedDateTime cursor = windowStart;
    for (TimeBlock busy : busyBlocks) {
      if (busy.getStart().isAfter(cursor)) {
        free.add(new TimeBlock(cursor, busy.getStart()));
      }
      if (busy.getEnd().isAfter(cursor)) {
        cursor = busy.getEnd();
      }
    }
    if (cursor.isBefore(windowEnd)) {
      free.add(new TimeBlock(cursor, windowEnd));
    }
    return free;
  }

  /**
   * Walks the free blocks on a fixed step and collects every candidate slot of
   * the wanted length that satisfies the time-of-day constraint.
   *
   * @param freeBlocks the shared free time, earliest first
   * @param duration the length a slot must have
   * @param morningsOnly keep only slots starting before noon
   * @param afternoonsOnly keep only slots starting at noon or later
   * @param limit maximum number of slots to return
   * @return the candidate slots, earliest first
   */
  private List<TimeBlock> stepCandidateSlots(List<TimeBlock> freeBlocks,
                                             Duration duration,
                                             boolean morningsOnly,
                                             boolean afternoonsOnly,
                                             int limit) {
    List<TimeBlock> slots = new ArrayList<>();
    for (TimeBlock block : freeBlocks) {
      ZonedDateTime candidate = block.getStart();
      while (!candidate.plus(duration).isAfter(block.getEnd())) {
        int hour = candidate.getHour();
        if ((!morningsOnly || hour < NOON) && (!afternoonsOnly || hour >= NOON)) {
          slots.add(new TimeBlock(candidate, candidate.plus(duration)));
        }
        candidate = candidate.plusMinutes(SLOT_STEP_MINUTES);
        if (slots.size() >= limit) {
          return slots;
        }
      }
    }
    return slots;
  }

  /**
   * Rejects a window that is missing or ends before it starts.
   *
   * @param start window start
   * @param end window end
   */
  private void checkWindow(ZonedDateTime start, ZonedDateTime end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("agenda.availability.windowMandatory");
    }
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("agenda.availability.windowEndsBeforeItStarts");
    }
  }

}
