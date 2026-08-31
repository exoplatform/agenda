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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.AvailabilityDisclosure;
import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.model.AvailabilityConflict;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;
import org.exoplatform.agenda.model.UserBusyTime;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The one place free/busy is computed, and the one place it is guarded.
 * <p>
 * See {@link AgendaAvailabilityService} for the two rules — the calendar ACL
 * floor and the target's own sharing setting that may widen it — and why they
 * live here rather than in each surface.
 */
@Service
public class AgendaAvailabilityServiceImpl implements AgendaAvailabilityService {

  private static final Log        LOG                        =
                                      ExoLogger.getLogger(AgendaAvailabilityServiceImpl.class);

  /**
   * How many spaces two users may have in common before this service stops
   * looking for one they are both really members of. Overshooting the cap
   * makes the answer "not disclosed", never "disclosed", so the failure
   * direction is the safe one.
   */
  private static final int        COMMON_SPACES_PROBE_LIMIT  = 200;

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

  private final AgendaUserSettingsService agendaUserSettingsService;

  /**
   * Builds the service.
   *
   * @param agendaEventService the event service the busy times are read from
   * @param identityManager used to resolve the asking and target identities
   * @param spaceService used by the calendar ACL to resolve space membership,
   *          and to decide whether two users share a space
   * @param agendaUserSettingsService owner of each user's sharing choice
   */
  @Autowired
  public AgendaAvailabilityServiceImpl(AgendaEventService agendaEventService,
                                       IdentityManager identityManager,
                                       SpaceService spaceService,
                                       AgendaUserSettingsService agendaUserSettingsService) {
    this.agendaEventService = agendaEventService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.agendaUserSettingsService = agendaUserSettingsService;
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
      List<TimeBlock> busy = mergeBlocks(readBusyBlocks(targetIdentityId, start, end, userIdentityId));
      result.add(new UserAvailability(targetIdentityId, busy, complement(busy, start, end)));
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<UserBusyTime> getBusyTime(List<Long> targetIdentityIds,
                                        ZonedDateTime start,
                                        ZonedDateTime end,
                                        long userIdentityId) {
    if (CollectionUtils.isEmpty(targetIdentityIds)) {
      throw new IllegalArgumentException("agenda.availability.usersMandatory");
    }
    checkWindow(start, end);
    List<UserBusyTime> result = new ArrayList<>();
    // The same user can be named twice — an organiser who is also listed as a
    // participant — and they are one person with one answer.
    for (Long targetIdentityId : targetIdentityIds.stream().distinct().toList()) {
      result.add(readBusyTime(targetIdentityId, start, end, userIdentityId));
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
    // Every attendee is passed through the gate before any calendar is read: a
    // partial answer would look like a whole one, so nobody's calendar is
    // touched until all of them are known to be readable. An attendee who
    // shares nothing is unknown here, and unknown is not free.
    for (Long attendeeIdentityId : attendeeIdentityIds) {
      checkCanReadAvailability(attendeeIdentityId, userIdentityId);
    }
    List<TimeBlock> allBusy = new ArrayList<>();
    for (Long attendeeIdentityId : attendeeIdentityIds) {
      allBusy.addAll(readBusyBlocks(attendeeIdentityId, windowStart, windowEnd, userIdentityId));
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
        Optional<List<TimeBlock>> disclosed = readBusyBlocksIfDisclosed(attendeeIdentityId, start, end, userIdentityId);
        if (disclosed.isEmpty()) {
          notDisclosed.add(attendeeIdentityId);
          continue;
        }
        List<TimeBlock> busy = disclosed.get();
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
        if (identity == null || !identity.isUser()) {
          continue;
        }
        // Free only when a calendar was actually read and held nothing. An
        // attendee whose availability is not disclosed is unknown, and an
        // unknown attendee must not push an option up the ranking as if they
        // had said yes to it.
        Optional<List<TimeBlock>> disclosed = readBusyBlocksIfDisclosed(attendeeIdentityId,
                                                                        dateOption.getStart(),
                                                                        dateOption.getEnd(),
                                                                        userIdentityId);
        if (disclosed.isPresent() && disclosed.get().isEmpty()) {
          freeCount++;
        }
      }
      freeAttendees.put(dateOption, freeCount);
    }
    return dateOptions.stream().sorted(Comparator.comparingLong((EventDateOption o) -> freeAttendees.get(o)).reversed()).toList();
  }

  /**
   * Decides whether one user may read another's availability.
   * <p>
   * Two rules, in this order, and this is the only method that holds them:
   * <ol>
   * <li>the platform's own calendar ACL, {@code Utils.canAccessCalendar} — a
   * space calendar is readable by whoever may view the space, a personal
   * calendar by its owner alone. This is the floor and it is never
   * subtracted from;</li>
   * <li>failing that, the target's own {@link AvailabilitySharing} choice,
   * which may widen the floor and can do nothing else.</li>
   * </ol>
   *
   * @param targetIdentityId the user whose availability is wanted
   * @param userIdentityId the user asking
   * @return {@code true} when the asking user may read that availability
   */
  private boolean canReadAvailability(long targetIdentityId, long userIdentityId) {
    return Utils.canAccessCalendar(identityManager, spaceService, targetIdentityId, userIdentityId)
        || isAvailabilitySharedWith(targetIdentityId, userIdentityId);
  }

  /**
   * Decides whether the target's sharing choice opens their busy time to this
   * particular viewer.
   * <p>
   * This can only ever add to what {@link #canReadAvailability} already
   * allowed, and there is only one thing it can add: the people the target
   * shares a space with. Two things keep it from becoming a bypass of the
   * calendar ACL: it answers {@code false} for anything that is not a user on
   * both sides — so no personal setting ever discloses a <em>space</em>
   * calendar, whose readers are decided by space visibility alone — and it
   * never looks at a calendar, only at a setting and a membership.
   * <p>
   * Nobody outside all of the target's spaces is ever admitted here, whatever
   * they are. That is the boundary the two-state setting draws, and it is
   * deliberately the narrower of the two that were on the table.
   *
   * @param targetIdentityId the user whose availability is wanted
   * @param userIdentityId the user asking
   * @return {@code true} when the target chose to disclose their busy time to
   *         this viewer
   */
  private boolean isAvailabilitySharedWith(long targetIdentityId, long userIdentityId) {
    Identity target = identityManager.getIdentity(String.valueOf(targetIdentityId));
    Identity viewer = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (target == null || !target.isUser() || viewer == null || !viewer.isUser()) {
      return false;
    }
    // An exhaustive switch with no default, on a two-value enum: it is one
    // boundary, and the day a third value is added the compiler makes whoever
    // adds it come here and say what it means. A default arm would have
    // answered for them, which on this gate is the wrong kind of convenience.
    return switch (agendaUserSettingsService.getAvailabilitySharing(targetIdentityId)) {
    case SHARED_SPACES -> sharesASpaceWith(target.getRemoteId(), viewer.getRemoteId());
    case NOBODY -> false;
    };
  }

  /**
   * Tells whether two users are both actual members of at least one same
   * space.
   * <p>
   * The candidate set comes from {@code SpaceService.getCommonSpaces}, and
   * every candidate is then confirmed with {@code isMember} for <em>both</em>
   * users. That second step is not belt-and-braces: the query behind
   * {@code getCommonSpaces} joins space memberships without filtering their
   * status, so a space one of them has merely been invited to, or has asked to
   * join, comes back as "common". Trusting it alone would let a stranger who
   * requested to join a space read the busy time of everyone already in it.
   * <p>
   * Anything that goes wrong — an unreachable store, a deployment whose
   * {@code SpaceService} does not implement the lookup — answers "not shared".
   * The failure direction is deliberate: a viewer who should have been allowed
   * is told nothing, which is recoverable; the opposite is not.
   *
   * @param targetUsername remote id of the user whose availability is wanted
   * @param viewerUsername remote id of the user asking
   * @return {@code true} when both are members of a same space
   */
  private boolean sharesASpaceWith(String targetUsername, String viewerUsername) {
    try {
      ListAccess<Space> commonSpaces = spaceService.getCommonSpaces(targetUsername, viewerUsername);
      if (commonSpaces == null) {
        return false;
      }
      Space[] spaces = commonSpaces.load(0, COMMON_SPACES_PROBE_LIMIT);
      if (spaces == null) {
        return false;
      }
      for (Space space : spaces) {
        if (spaceService.isMember(space, targetUsername) && spaceService.isMember(space, viewerUsername)) {
          return true;
        }
      }
      return false;
    } catch (Exception e) { // NOSONAR any failure to establish a shared space must read as "not shared"
      LOG.warn("Error looking for a space shared by '{}' and '{}'; their availability is treated as not shared",
               targetUsername,
               viewerUsername,
               e);
      return false;
    }
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
   * The gate and the read, in one method — the only way a busy block is
   * obtained anywhere in this class.
   * <p>
   * They are one method on purpose. {@link AgendaEventService#getEvents}
   * refuses an attendee filter naming anyone but its acting user, so reading a
   * colleague's busy time means asking it as that colleague, which is only
   * legitimate once {@link #canReadAvailability} has said so. Fusing the two
   * makes "read without checking" unrepresentable rather than merely
   * discouraged: there is no method that reads and does not check.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @param userIdentityId the user asking
   * @return the busy blocks, unmerged and in no particular order
   * @throws IllegalAccessException when the asking user may not read that
   *           user's availability
   */
  private List<TimeBlock> readBusyBlocks(long targetIdentityId,
                                         ZonedDateTime start,
                                         ZonedDateTime end,
                                         long userIdentityId) throws IllegalAccessException {
    checkCanReadAvailability(targetIdentityId, userIdentityId);
    return getBusyBlocks(targetIdentityId, start, end);
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
   * <strong>Unguarded, and private for that reason.</strong> The target is
   * passed to {@link AgendaEventService#getEvents} as the acting user, because
   * that service refuses an attendee filter naming anyone else — which, once
   * the sharing setting lets a colleague be read at all, would otherwise
   * refuse every disclosure this feature exists to allow. The consequence is
   * plain and worth stating: the second gate EXO-89841 gained by passing the
   * asking user here no longer applies, and {@link #canReadAvailability} is
   * now the only thing standing between a caller and someone else's busy time.
   * That is why the sole caller of this method is {@link #readBusyBlocks},
   * which cannot reach it without going through that gate.
   * <p>
   * Events materialised from a connected remote or CalDAV calendar <em>are</em>
   * included: they are stored as ordinary events carrying their owner as an
   * ACCEPTED attendee, which is exactly what is read here. Only their times
   * are ever used; nothing about their content leaves this method.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @return the busy blocks, unmerged and in no particular order
   * @throws IllegalAccessException when the event service refuses the read
   */
  private List<TimeBlock> getBusyBlocks(long targetIdentityId,
                                        ZonedDateTime start,
                                        ZonedDateTime end) throws IllegalAccessException {
    EventFilter filter = new EventFilter(targetIdentityId,
                                         null,
                                         List.of(EventAttendeeResponse.ACCEPTED),
                                         start,
                                         end,
                                         BUSY_QUERY_LIMIT);
    List<Event> events = agendaEventService.getEvents(filter, TIMEZONE, targetIdentityId);
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
   * Reads busy blocks for the enrichment paths, which must not fail the write
   * they decorate — so a refusal comes back as a value rather than as an
   * exception.
   * <p>
   * <strong>An empty {@link Optional} is "not disclosed"; an empty list is
   * "disclosed, and nothing was in the way".</strong> Those two are the states
   * this whole feature exists to keep apart. Collapsing them — returning an
   * empty list on refusal — is what turns a person who shares nothing into a
   * person who is free, and books a meeting over their real commitments.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @param userIdentityId the user asking
   * @return the busy blocks, or {@link Optional#empty()} when this user's
   *         availability is not disclosed to the asking user
   */
  private Optional<List<TimeBlock>> readBusyBlocksIfDisclosed(long targetIdentityId,
                                                              ZonedDateTime start,
                                                              ZonedDateTime end,
                                                              long userIdentityId) {
    try {
      return Optional.of(readBusyBlocks(targetIdentityId, start, end, userIdentityId));
    } catch (IllegalAccessException e) { // NOSONAR a refusal is an answer here, not a failure of the caller
      return Optional.empty();
    }
  }

  /**
   * Reads one user's busy time and says which of the three things happened,
   * without ever failing the call.
   * <p>
   * The gate is not re-implemented here and not consulted separately: this
   * calls {@link #readBusyBlocks}, the one fused gate-and-read of this class,
   * and turns its outcome into a status. What this method adds is that a
   * refusal and a breakage stop being the same silence.
   * <p>
   * <strong>Why a refusal from either place is
   * {@link AvailabilityDisclosure#NOT_DISCLOSED}.</strong>
   * {@link #readBusyBlocks} raises {@link IllegalAccessException} from the
   * gate, and {@link AgendaEventService#getEvents} may raise the same type
   * afterwards. Telling the two apart would mean splitting the gate from the
   * read, which is precisely what {@link #readBusyBlocks} was fused to make
   * impossible. Both mean "no calendar was read", both are drawn as unchecked,
   * and the only cost of not distinguishing them is a word in a message.
   * <p>
   * Anything else that goes wrong is {@link AvailabilityDisclosure#FAILED},
   * and it is logged — a broken store is an incident, unlike a refusal, which
   * is an answer.
   *
   * @param targetIdentityId the user whose busy time is wanted
   * @param start window start
   * @param end window end
   * @param userIdentityId the user asking
   * @return the user's busy time and the status of the read, never
   *         {@code null}
   */
  private UserBusyTime readBusyTime(long targetIdentityId,
                                    ZonedDateTime start,
                                    ZonedDateTime end,
                                    long userIdentityId) {
    Identity target = identityManager.getIdentity(String.valueOf(targetIdentityId));
    if (target == null || !target.isUser()) {
      // A space, or nothing at all. "Not disclosed" is literally true of it,
      // and it keeps this enum at three values rather than gaining a fourth
      // that every caller would have to reason about.
      return new UserBusyTime(targetIdentityId, AvailabilityDisclosure.NOT_DISCLOSED, null);
    }
    try {
      return new UserBusyTime(targetIdentityId,
                              AvailabilityDisclosure.DISCLOSED,
                              mergeBlocks(readBusyBlocks(targetIdentityId, start, end, userIdentityId)));
    } catch (IllegalAccessException e) { // NOSONAR a refusal is an answer here, not a failure of the caller
      return new UserBusyTime(targetIdentityId, AvailabilityDisclosure.NOT_DISCLOSED, null);
    } catch (RuntimeException e) { // NOSONAR one broken read must not fail the answer about everybody else
      LOG.warn("Error reading the busy time of identity {} for identity {}; it is reported as unread, never as free",
               targetIdentityId,
               userIdentityId,
               e);
      return new UserBusyTime(targetIdentityId, AvailabilityDisclosure.FAILED, null);
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
