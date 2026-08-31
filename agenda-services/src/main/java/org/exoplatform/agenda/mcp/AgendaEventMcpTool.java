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
package org.exoplatform.agenda.mcp;

import static io.meeds.mcp.server.tool.util.SpaceToolUtils.toSpaceModel;
import static io.meeds.mcp.server.tool.util.UserToolUtils.toUserModel;
import static io.meeds.mcp.server.util.McpToolUtils.formatDate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.mcp.model.AgendaEventAttendeeModel;
import org.exoplatform.agenda.mcp.model.AgendaEventCollectionModel;
import org.exoplatform.agenda.mcp.model.AgendaEventModel;
import org.exoplatform.agenda.mcp.model.AttendeeConflictModel;
import org.exoplatform.agenda.mcp.model.AvailabilityModel;
import org.exoplatform.agenda.mcp.model.ConferenceModel;
import org.exoplatform.agenda.mcp.model.ConflictsModel;
import org.exoplatform.agenda.mcp.model.DatePollModel;
import org.exoplatform.agenda.mcp.model.DatePollOptionModel;
import org.exoplatform.agenda.mcp.model.TimeBlockModel;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventPermission;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.model.AgendaEventSearchFilter;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserAvailability;
import org.exoplatform.agenda.plugin.AgendaEventAclPlugin;
import org.exoplatform.agenda.plugin.AgendaEventPermanentLinkPlugin;
import org.exoplatform.agenda.service.AgendaAvailabilityService;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventDatePollService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.mcp.server.plugin.McpToolPlugin;
import io.meeds.mcp.server.tool.model.SpaceModel;
import io.meeds.mcp.server.tool.model.UserModel;
import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.social.space.plugin.SpaceAclPlugin;
import io.meeds.social.translation.service.TranslationService;

import lombok.SneakyThrows;

@Service
@Profile("mcp-server")
public class AgendaEventMcpTool implements McpToolPlugin {

  private static final String                MSG_USER_NOT_ALLOWED_TO_ACCESS_SPACE =
                                                                                  "User isn't allowed to access the space with id %s";

  private static final String                MSG_USER_NOT_ALLOWED_TO_CREATE_EVENT =
                                                                                  "User isn't allowed to create events in space with id %s";

  private static final String                MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT =
                                                                                  "User isn't allowed to access event with id %s";

  private static final String                MSG_PARAMETER_EVENT_ID_MANDATORY     = "Parameter 'event_id' is mandatory";

  private static final String                MSG_PARAMETER_ATTENDEE_IS_MANDATORY  = "Parameter 'attendee_usernames' is mandatory";

  /**
   * The only clash status the report can carry. The event query behind
   * free/busy filters on CONFIRMED events, so a TENTATIVE one is never read
   * and "tentative" could never be reported.
   */
  private static final String                CONFLICT_STATUS_BUSY                 = "busy";

  private static final String                MSG_USER_NOT_ALLOWED_TO_UPDATE       =
                                                                            "User isn't allowed to update the event with id %s";

  private static final ZoneOffset            TIMEZONE                             = ZoneOffset.UTC;

  private static final int                   MAX_LIMIT                            = 100;

  private static final int                   BUSY_QUERY_LIMIT                     = 500;

  private final SpaceService                 spaceService;

  private final IdentityManager              identityManager;

  private final AgendaCalendarService        agendaCalendarService;

  private final AgendaEventService           agendaEventService;

  private final AgendaAvailabilityService    agendaAvailabilityService;

  private final AgendaEventAttendeeService   agendaEventAttendeeService;

  private final AgendaEventConferenceService agendaEventConferenceService;

  private final AgendaEventDatePollService   agendaEventDatePollService;

  private final AgendaEventReminderService   agendaEventReminderService;

  private final AgendaUserSettingsService    agendaUserSettingsService;

  private final ProfilePropertyService       profilePropertyService;

  private final UserACL                      userAcl;

  private final TranslationService           translationService;

  private final PermanentLinkService         permanentLinkService;

  private final UserPortalConfigService      portalConfigService;

  @Autowired
  public AgendaEventMcpTool(AgendaCalendarService agendaCalendarService, // NOSONAR
                            AgendaEventService agendaEventService,
                            AgendaAvailabilityService agendaAvailabilityService,
                            AgendaEventConferenceService agendaEventConferenceService,
                            AgendaEventAttendeeService agendaEventAttendeeService,
                            AgendaEventDatePollService agendaEventDatePollService,
                            AgendaEventReminderService agendaEventReminderService,
                            AgendaUserSettingsService agendaUserSettingsService,
                            UserPortalConfigService portalConfigService,
                            ProfilePropertyService profilePropertyService,
                            PermanentLinkService permanentLinkService,
                            TranslationService translationService,
                            IdentityManager identityManager,
                            SpaceService spaceService,
                            UserACL userAcl) {
    this.agendaEventService = agendaEventService;
    this.agendaAvailabilityService = agendaAvailabilityService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaEventDatePollService = agendaEventDatePollService;
    this.agendaEventReminderService = agendaEventReminderService;
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.agendaCalendarService = agendaCalendarService;
    this.profilePropertyService = profilePropertyService;
    this.portalConfigService = portalConfigService;
    this.permanentLinkService = permanentLinkService;
    this.translationService = translationService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.userAcl = userAcl;
  }

  public AgendaEventCollectionModel getAgendaEvents(String start,
                                                    String end,
                                                    Long spaceId,
                                                    Integer limit) throws IllegalAccessException {
    if (limit == null) {
      limit = 0;
    }
    ZonedDateTime startDatetime = StringUtils.isBlank(start) ? ZonedDateTime.now(TIMEZONE) :
                                                             toZonedDateTime(start);
    ZonedDateTime endDatetime = null;
    if (StringUtils.isBlank(end)) {
      if (limit <= 0) {
        limit = 10;
      }
    } else {
      endDatetime = toZonedDateTime(end);
    }
    limit = Math.min(limit, MAX_LIMIT);
    long userIdentityId = getCurrentUserIdentityId();
    List<Long> ownerIds = spaceId == null || spaceId.longValue() == 0 ? null :
                                                                      getSpaceIdentityIds(Collections.singletonList(spaceId));
    EventFilter eventFilter = new EventFilter(userIdentityId,
                                              ownerIds,
                                              List.of(EventAttendeeResponse.ACCEPTED,
                                                      EventAttendeeResponse.TENTATIVE,
                                                      EventAttendeeResponse.NEEDS_ACTION),
                                              startDatetime,
                                              endDatetime,
                                              limit);
    List<Event> events = agendaEventService.getEvents(eventFilter, TIMEZONE, userIdentityId);
    List<AgendaEventModel> eventEntities = events.stream()
                                                 .map(this::toAgendaEventModel)
                                                 .filter(Objects::nonNull)
                                                 .toList();
    return new AgendaEventCollectionModel(eventEntities, start, end, limit);
  }

  public AgendaEventModel getAgendaEventById(long eventId) throws ObjectNotFoundException, IllegalAccessException {
    Event event = agendaEventService.getEventById(eventId, TIMEZONE, getCurrentUserIdentityId());
    if (event == null) {
      throw new ObjectNotFoundException("Agenda Event with id %s not found".formatted(eventId));
    }
    return toAgendaEventModel(event);
  }

  // Create an agenda event in a space calendar, optionally recurrent, with a server-side conflict report over its
  // attendees. When fail_on_conflict is true and at least one attendee is busy/tentative in the window, creation is
  // aborted. Every event must belong to a space: pass either space_id or a space name via the 'space' parameter.
  public AgendaEventModel createAgendaEvent(Long spaceId,
                                            String summary,
                                            String description,
                                            String location,
                                            String start,
                                            String end,
                                            List<String> attendeeUsernames,
                                            String recurrenceFrequency,
                                            Integer recurrenceInterval,
                                            String recurrenceUntil,
                                            Integer recurrenceCount,
                                            Boolean failOnConflict,
                                            String space) throws IllegalAccessException,
                                                          ObjectNotFoundException,
                                                          AgendaException {
    long resolvedSpaceId = resolveSpaceId(spaceId, space);
    checkSpaceCreatePermission(resolvedSpaceId);
    long userIdentityId = getCurrentUserIdentityId();
    long calendarId = getSpaceCalendarId(resolvedSpaceId);
    ZonedDateTime startDate = toZonedDateTime(start);
    ZonedDateTime endDate = toZonedDateTime(end);
    List<EventAttendee> attendees = toEventAttendees(attendeeUsernames, true);

    // Compute the conflict report over the event window before writing anything
    ConflictsModel conflicts = computeConflicts(attendees, startDate, endDate);
    if (Boolean.TRUE.equals(failOnConflict) && !conflicts.isAllAvailable()) {
      throw new IllegalStateException("Event not created: %s attendee(s) have a conflict in the requested window"
          .formatted(conflicts.getConflicts().size()));
    }

    Event event = new Event(0l,
                            0l,
                            calendarId,
                            userIdentityId,
                            0l,
                            ZonedDateTime.now(),
                            ZonedDateTime.now(),
                            summary,
                            description,
                            location,
                            null,
                            TIMEZONE,
                            startDate,
                            endDate,
                            false,
                            EventAvailability.DEFAULT,
                            EventStatus.CONFIRMED,
                            buildRecurrence(recurrenceFrequency, recurrenceInterval, recurrenceUntil, recurrenceCount),
                            null,
                            new EventPermission(true, true),
                            true,
                            true);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        attendees,
                                                        null,
                                                        getDefaultUserEventReminders(),
                                                        null,
                                                        null,
                                                        true,
                                                        userIdentityId);
    AgendaEventModel model = toAgendaEventModel(createdEvent);
    model.setConflicts(conflicts);
    return model;
  }

  // Decline an event invitation for the current user. When occurrence_id is given this is a "this-and-following"
  // decline: it declines that occurrence AND every subsequent one of the series (it does not cancel a single date;
  // for cancelling only one date use cancel_agenda_event with occurrence_id).
  public void declineAgendaEventInvitation(Long eventId, String occurrenceId) throws IllegalAccessException,
                                                                              ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                            String.valueOf(eventId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    if (StringUtils.isNotBlank(occurrenceId)) {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           toZonedDateTime(occurrenceId),
                                                           getCurrentUserIdentityId(),
                                                           EventAttendeeResponse.DECLINED);
    } else {
      agendaEventAttendeeService.sendEventResponse(eventId, getCurrentUserIdentityId(), EventAttendeeResponse.DECLINED);
    }
  }

  // Cancel an event. Without occurrence_id, the whole event is declined for the current user.
  // With occurrence_id, ONLY that single occurrence of a recurring series is removed (occurrences before AND after are
  // kept), using the canonical eXo pattern: materialize the occurrence as an exceptional occurrence, then mark it
  // CANCELLED so the series expansion skips exactly that one date.
  public void cancelAgendaEvent(Long eventId, String occurrenceId) throws IllegalAccessException,
                                                                   ObjectNotFoundException,
                                                                   AgendaException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (StringUtils.isBlank(occurrenceId)) {
      declineAgendaEventInvitation(eventId, null);
      return;
    }
    if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
    cancelSingleOccurrence(eventId, toZonedDateTime(occurrenceId), getCurrentUserIdentityId());
  }

  // Cancel a single occurrence of a recurring event: create the exceptional occurrence for that date, then set its
  // status to CANCELLED. The computed occurrence for that date is then skipped (an exceptional occurrence exists) and
  // the cancelled exceptional itself is hidden (event listing only returns CONFIRMED), while every other occurrence
  // (before and after) is preserved.
  private void cancelSingleOccurrence(long eventId,
                                      ZonedDateTime occurrenceId,
                                      long userIdentityId) throws AgendaException,
                                                           IllegalAccessException,
                                                           ObjectNotFoundException {
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.updateEventFields(exceptionalOccurrence.getId(),
                                         Collections.singletonMap("status",
                                                                  Collections.singletonList(EventStatus.CANCELLED.name())),
                                         false,
                                         false,
                                         userIdentityId);
  }

  public void acceptAgendaEventInvitation(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                            String.valueOf(eventId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    agendaEventAttendeeService.sendEventResponse(eventId, getCurrentUserIdentityId(), EventAttendeeResponse.ACCEPTED);
  }

  public AgendaEventModel inviteUsersToAgendaEvent(Long eventId, List<String> attendeeUsernames) throws IllegalAccessException,
                                                                                                 ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (CollectionUtils.isEmpty(attendeeUsernames)) {
      throw new IllegalArgumentException(MSG_PARAMETER_ATTENDEE_IS_MANDATORY);
    } else if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                          String.valueOf(eventId),
                                          getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
    EventAttendeeList attendeeList = agendaEventAttendeeService.getEventAttendees(eventId);
    List<EventAttendee> existingAttendees = attendeeList.getEventAttendees() == null ? Collections.emptyList() :
                                                                                     attendeeList.getEventAttendees();
    List<String> existingAttendeeUsernames = existingAttendees.stream()
                                                              .map(eat -> eat.getIdentityId())
                                                              .map(identityManager::getIdentity)
                                                              .filter(Identity::isUser)
                                                              .map(Identity::getRemoteId)
                                                              .toList();
    List<EventAttendee> attendees = toEventAttendees(attendeeUsernames.stream()
                                                                      .filter(u -> !existingAttendeeUsernames.contains(u))
                                                                      .toList(),
                                                     false);
    attendees.addAll(existingAttendees);
    agendaEventAttendeeService.saveEventAttendees(agendaEventService.getEventById(eventId),
                                                  attendees,
                                                  getCurrentUserIdentityId(),
                                                  false,
                                                  false,
                                                  null);
    return getAgendaEventById(eventId);
  }

  public AgendaEventModel inviteSpaceToAgendaEvent(Long eventId, Long spaceId) throws IllegalAccessException,
                                                                               ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (spaceId == null || spaceId == 0) {
      throw new IllegalArgumentException("Parameter 'space_id' is mandatory");
    } else if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                          String.valueOf(eventId),
                                          getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    } else if (!userAcl.hasAccessPermission(SpaceAclPlugin.OBJECT_TYPE,
                                            String.valueOf(spaceId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_SPACE.formatted(spaceId));
    }
    EventAttendeeList attendeeList = agendaEventAttendeeService.getEventAttendees(eventId);
    List<EventAttendee> existingAttendees = attendeeList.getEventAttendees() == null ? Collections.emptyList() :
                                                                                     attendeeList.getEventAttendees();
    List<Long> existingAttendeeSpaces = existingAttendees.stream()
                                                         .map(eat -> eat.getIdentityId())
                                                         .map(identityManager::getIdentity)
                                                         .filter(Identity::isSpace)
                                                         .map(Identity::getRemoteId)
                                                         .map(spaceService::getSpaceByPrettyName)
                                                         .filter(Objects::nonNull)
                                                         .map(Space::getSpaceId)
                                                         .toList();
    if (!existingAttendeeSpaces.contains(spaceId)) {
      List<EventAttendee> attendees = new ArrayList<>(existingAttendees);
      attendees.add(new EventAttendee(0l, getSpaceIdentityId(spaceId), EventAttendeeResponse.NEEDS_ACTION));
      agendaEventAttendeeService.saveEventAttendees(agendaEventService.getEventById(eventId),
                                                    attendees,
                                                    getCurrentUserIdentityId(),
                                                    false,
                                                    false,
                                                    null);
    }
    return getAgendaEventById(eventId);
  }

  // Update an agenda event (only provided fields change), optionally (re)setting recurrence, with a conflict report.
  // When fail_on_conflict is true and an attendee is busy/tentative in the (new) window, the update is aborted.
  public AgendaEventModel updateAgendaEvent(Long eventId,
                                            String summary,
                                            String description,
                                            String location,
                                            String start,
                                            String end,
                                            String recurrenceFrequency,
                                            Integer recurrenceInterval,
                                            String recurrenceUntil,
                                            Integer recurrenceCount,
                                            Boolean failOnConflict) throws IllegalAccessException,
                                                                    ObjectNotFoundException,
                                                                    AgendaException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                          String.valueOf(eventId),
                                          getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
    long userIdentityId = getCurrentUserIdentityId();
    Event existingEvent = agendaEventService.getEventById(eventId, TIMEZONE, userIdentityId);
    if (existingEvent == null) {
      throw new ObjectNotFoundException("Agenda Event with id %s not found".formatted(eventId));
    }
    // Resolve the effective window (new values if provided, else the current event's) for the conflict report
    ZonedDateTime startDate = start != null ? toZonedDateTime(start) : existingEvent.getStart();
    ZonedDateTime endDate = end != null ? toZonedDateTime(end) : existingEvent.getEnd();
    List<EventAttendee> attendees = getExistingEventAttendees(eventId);
    ConflictsModel conflicts = computeConflicts(attendees, startDate, endDate);
    if (Boolean.TRUE.equals(failOnConflict) && !conflicts.isAllAvailable()) {
      throw new IllegalStateException("Event not updated: %s attendee(s) have a conflict in the requested window"
          .formatted(conflicts.getConflicts().size()));
    }

    EventRecurrence recurrence = buildRecurrence(recurrenceFrequency, recurrenceInterval, recurrenceUntil, recurrenceCount);
    if (recurrence != null) {
      // Recurrence changes require a full-event update (updateEventFields doesn't support recurrence)
      if (summary != null) {
        existingEvent.setSummary(summary);
      }
      if (description != null) {
        existingEvent.setDescription(description);
      }
      if (location != null) {
        existingEvent.setLocation(location);
      }
      existingEvent.setStart(startDate);
      existingEvent.setEnd(endDate);
      existingEvent.setRecurrence(recurrence);
      // Passing null for attendees/conferences/reminders keeps the existing ones untouched
      agendaEventService.updateEvent(existingEvent, null, null, null, null, null, false, userIdentityId);
    } else {
      Map<String, List<String>> fieldsToUpdate = new HashMap<>();
      if (summary != null) {
        fieldsToUpdate.put("summary", Collections.singletonList(summary));
      }
      if (description != null) {
        fieldsToUpdate.put("description", Collections.singletonList(description));
      }
      if (location != null) {
        fieldsToUpdate.put("location", Collections.singletonList(location));
      }
      if (start != null) {
        fieldsToUpdate.put("start", Collections.singletonList(start));
      }
      if (end != null) {
        fieldsToUpdate.put("end", Collections.singletonList(end));
      }
      if (!fieldsToUpdate.isEmpty()) {
        agendaEventService.updateEventFields(eventId, fieldsToUpdate, false, false, userIdentityId);
      }
    }
    AgendaEventModel model = getAgendaEventById(eventId);
    model.setConflicts(conflicts);
    return model;
  }

  public void deleteAgendaEvent(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    } else if (!userAcl.hasDeletePermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                            String.valueOf(eventId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException("User isn't allowed to delete the event with id %s".formatted(eventId));
    }
    agendaEventService.deleteEventById(eventId, getCurrentUserIdentityId());
  }

  // ==========================================================================
  // Date polls (expose AgendaEventDatePollService)
  // ==========================================================================

  /**
   * Create a date poll: a TENTATIVE event carrying several proposed slots that attendees can vote on.
   * Each proposed slot is a "&lt;startRFC3339&gt;|&lt;endRFC3339&gt;" string. When rank_slots_by_availability is
   * true, slots are ordered so the ones with the most available internal attendees come first. Every poll must
   * belong to a space: pass either space_id or a space name via the 'space' parameter (resolved to resolvedSpaceId).
   */
  public DatePollModel createDatePoll(Long spaceId,
                                      String title,
                                      List<String> proposedSlots,
                                      List<String> attendeeUsernames,
                                      String description,
                                      Boolean rankSlotsByAvailability,
                                      String space) throws IllegalAccessException,
                                                    ObjectNotFoundException,
                                                    AgendaException {
    if (CollectionUtils.isEmpty(proposedSlots)) {
      throw new IllegalArgumentException("Parameter 'proposed_slots' is mandatory (at least one '<start>|<end>' slot)");
    }
    long resolvedSpaceId = resolveSpaceId(spaceId, space);
    checkSpaceCreatePermission(resolvedSpaceId);
    long userIdentityId = getCurrentUserIdentityId();
    List<EventAttendee> attendees = toEventAttendees(attendeeUsernames, true);
    List<EventDateOption> dateOptions = new ArrayList<>();
    for (String slot : proposedSlots) {
      String[] parts = slot.split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid slot '%s', expected '<startRFC3339>|<endRFC3339>'".formatted(slot));
      }
      dateOptions.add(new EventDateOption(0l, 0l, toZonedDateTime(parts[0].trim()), toZonedDateTime(parts[1].trim()), false, false, null));
    }
    if (Boolean.TRUE.equals(rankSlotsByAvailability)) {
      dateOptions = rankDateOptionsByAvailability(dateOptions, attendees);
    }
    ZonedDateTime overallStart = dateOptions.stream().map(EventDateOption::getStart).min(Comparator.naturalOrder()).orElseThrow();
    ZonedDateTime overallEnd = dateOptions.stream().map(EventDateOption::getEnd).max(Comparator.naturalOrder()).orElseThrow();
    Event event = new Event(0l,
                            0l,
                            getSpaceCalendarId(resolvedSpaceId),
                            userIdentityId,
                            0l,
                            ZonedDateTime.now(),
                            ZonedDateTime.now(),
                            title,
                            description,
                            null,
                            null,
                            TIMEZONE,
                            overallStart,
                            overallEnd,
                            false,
                            EventAvailability.DEFAULT,
                            EventStatus.TENTATIVE,
                            null,
                            null,
                            new EventPermission(true, true),
                            true,
                            true);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        attendees,
                                                        null,
                                                        getDefaultUserEventReminders(),
                                                        dateOptions,
                                                        null,
                                                        true,
                                                        userIdentityId);
    return getDatePollResults(createdEvent.getId());
  }

  // Cast the current user's votes on a date poll: accepted_slot_ids are the date-option ids the user accepts.
  public DatePollModel voteDatePoll(Long eventId, List<Long> acceptedSlotIds) throws ObjectNotFoundException,
                                                                              IllegalAccessException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    agendaEventDatePollService.saveEventVotes(eventId,
                                              acceptedSlotIds == null ? Collections.emptyList() : acceptedSlotIds,
                                              getCurrentUserIdentityId());
    return getDatePollResults(eventId);
  }

  // Return the current tally of a date poll: each proposed slot with its vote count and the usernames who voted.
  public DatePollModel getDatePollResults(long eventId) throws ObjectNotFoundException, IllegalAccessException {
    if (eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    Event event = agendaEventService.getEventById(eventId, TIMEZONE, getCurrentUserIdentityId());
    if (event == null) {
      throw new ObjectNotFoundException("Agenda Event with id %s not found".formatted(eventId));
    }
    List<EventDateOption> options = agendaEventDatePollService.getEventDateOptions(eventId, TIMEZONE);
    List<DatePollOptionModel> optionModels = options.stream().map(this::toDatePollOptionModel).toList();
    return new DatePollModel(eventId, event.getSummary(), optionModels);
  }

  // Confirm a date poll by picking a winning slot: converts the poll into a normal confirmed event on that slot,
  // then re-computes and returns the attendee conflict report for the newly confirmed window.
  public AgendaEventModel confirmDatePoll(Long dateOptionId) throws ObjectNotFoundException, IllegalAccessException {
    if (dateOptionId == null || dateOptionId == 0) {
      throw new IllegalArgumentException("Parameter 'date_option_id' is mandatory");
    }
    EventDateOption dateOption = agendaEventDatePollService.getEventDateOption(dateOptionId, TIMEZONE);
    if (dateOption == null) {
      throw new ObjectNotFoundException("Event Date Option with id %s not found".formatted(dateOptionId));
    }
    long eventId = dateOption.getEventId();
    if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
    long userIdentityId = getCurrentUserIdentityId();
    agendaEventService.selectEventDateOption(eventId, dateOptionId, userIdentityId);
    AgendaEventModel model = getAgendaEventById(eventId);
    List<EventAttendee> attendees = getExistingEventAttendees(eventId);
    ConflictsModel conflicts = computeConflicts(attendees,
                                                dateOption.getStart(),
                                                dateOption.getEnd());
    model.setConflicts(conflicts);
    return model;
  }

  // ==========================================================================
  // Free / busy + suggestions
  // ==========================================================================

  /**
   * Returns each named user's busy and free time blocks over a window.
   * <p>
   * Glue only: usernames and RFC3339 bounds are resolved here, the ACL and the
   * free/busy algebra belong to {@link AgendaAvailabilityService}. Asking
   * about a user whose availability the caller may not read is refused, not
   * silently omitted.
   *
   * @param usernames the users to report on
   * @param start window start, RFC3339
   * @param end window end, RFC3339
   * @return one entry per username that resolved to an identity
   * @throws IllegalAccessException when the caller may not read one of them
   */
  public List<AvailabilityModel> getAvailability(List<String> usernames,
                                                 String start,
                                                 String end) throws IllegalAccessException {
    if (CollectionUtils.isEmpty(usernames)) {
      throw new IllegalArgumentException("Parameter 'usernames' is mandatory");
    }
    Map<Long, String> usernamesByIdentityId = resolveUsers(usernames);
    if (usernamesByIdentityId.isEmpty()) {
      return List.of();
    }
    List<UserAvailability> availabilities = agendaAvailabilityService.getAvailability(List.copyOf(usernamesByIdentityId.keySet()),
                                                                                      toZonedDateTime(start),
                                                                                      toZonedDateTime(end),
                                                                                      getCurrentUserIdentityId());
    return availabilities.stream()
                         .map(availability -> new AvailabilityModel(usernamesByIdentityId.get(availability.getIdentityId()),
                                                                    toTimeBlocks(availability.getBusy()),
                                                                    toTimeBlocks(availability.getFree())))
                         .toList();
  }

  /**
   * Suggests candidate slots of at least {@code duration_minutes} over which
   * every attendee is free.
   * <p>
   * Glue only. Note that this refuses outright when one attendee's
   * availability is not readable by the caller: a suggestion computed from a
   * subset of the attendees would read as a whole answer.
   *
   * @param attendees the users who must all be free
   * @param durationMinutes the wanted slot length, in minutes
   * @param windowStart earliest acceptable start, RFC3339
   * @param windowEnd latest acceptable end, RFC3339
   * @param constraints optional free text; "mornings" and "afternoons" are
   *          honoured
   * @return the candidate slots, earliest first
   * @throws IllegalAccessException when the caller may not read one attendee
   */
  public List<TimeBlockModel> suggestMeetingTime(List<String> attendees,
                                                 Integer durationMinutes,
                                                 String windowStart,
                                                 String windowEnd,
                                                 String constraints) throws IllegalAccessException {
    if (CollectionUtils.isEmpty(attendees)) {
      throw new IllegalArgumentException(MSG_PARAMETER_ATTENDEE_IS_MANDATORY);
    }
    if (durationMinutes == null || durationMinutes <= 0) {
      throw new IllegalArgumentException("Parameter 'duration_minutes' must be a positive number of minutes");
    }
    Map<Long, String> usernamesByIdentityId = resolveUsers(attendees);
    if (usernamesByIdentityId.isEmpty()) {
      return List.of();
    }
    String constraint = constraints == null ? "" : constraints.toLowerCase();
    return toTimeBlocks(agendaAvailabilityService.suggestMeetingTime(List.copyOf(usernamesByIdentityId.keySet()),
                                                                     Duration.ofMinutes(durationMinutes),
                                                                     toZonedDateTime(windowStart),
                                                                     toZonedDateTime(windowEnd),
                                                                     constraint.contains("morning"),
                                                                     constraint.contains("afternoon"),
                                                                     MAX_LIMIT,
                                                                     getCurrentUserIdentityId()));
  }

  // ==========================================================================
  // Parity tools (attendees, responses, reminders, conference, search)
  // ==========================================================================

  // List the attendees of an event together with their response (ACCEPTED/DECLINED/TENTATIVE/NEEDS_ACTION).
  public List<AgendaEventAttendeeModel> getEventAttendees(long eventId) throws IllegalAccessException {
    if (eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    return getEventAttendees(agendaEventService.getEventById(eventId));
  }

  // Respond to an event invitation as the current user with ACCEPTED, DECLINED or TENTATIVE (comment is not persisted).
  public AgendaEventModel respondToAgendaEvent(Long eventId,
                                               String response,
                                               String comment) throws ObjectNotFoundException, IllegalAccessException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (StringUtils.isBlank(response)) {
      throw new IllegalArgumentException("Parameter 'response' is mandatory (ACCEPTED, DECLINED or TENTATIVE)");
    }
    EventAttendeeResponse attendeeResponse = EventAttendeeResponse.valueOf(response.trim().toUpperCase());
    if (attendeeResponse == EventAttendeeResponse.NEEDS_ACTION) {
      throw new IllegalArgumentException("Response NEEDS_ACTION isn't allowed, use ACCEPTED, DECLINED or TENTATIVE");
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    agendaEventAttendeeService.sendEventResponse(eventId, getCurrentUserIdentityId(), attendeeResponse);
    return getAgendaEventById(eventId);
  }

  // Set the current user's reminders on an event; each reminder is a "<number> <MINUTE|HOUR|DAY|WEEK>" string.
  public List<String> setEventReminders(Long eventId, List<String> reminders) throws IllegalAccessException, AgendaException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    long userIdentityId = getCurrentUserIdentityId();
    List<EventReminder> eventReminders = new ArrayList<>();
    if (reminders != null) {
      for (String reminder : reminders) {
        eventReminders.add(parseReminder(reminder, userIdentityId));
      }
    }
    Event event = agendaEventService.getEventById(eventId);
    agendaEventReminderService.saveEventReminders(event, eventReminders, userIdentityId);
    return agendaEventReminderService.getEventReminders(eventId, userIdentityId)
                                     .stream()
                                     .map(r -> "%s %s".formatted(r.getBefore(), r.getBeforePeriodType()))
                                     .toList();
  }

  // Return the web-conference (e.g. Jitsi) link attached to an event, if any.
  public ConferenceModel getEventConference(long eventId) throws IllegalAccessException {
    if (eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    List<EventConference> conferences = agendaEventConferenceService.getEventConferences(eventId);
    if (CollectionUtils.isEmpty(conferences)) {
      return new ConferenceModel(null, null);
    }
    EventConference conference = conferences.get(0);
    return new ConferenceModel(conference.getType(), conference.getUrl());
  }

  // Attach (or, when url is blank, remove) a web-conference link on an event. Defaults the type to "web".
  public ConferenceModel setEventConference(Long eventId, String url, String type) throws IllegalAccessException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MANDATORY);
    }
    if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
    if (StringUtils.isBlank(url)) {
      agendaEventConferenceService.saveEventConferences(eventId, Collections.emptyList());
      return new ConferenceModel(null, null);
    }
    EventConference conference = new EventConference();
    conference.setEventId(eventId);
    conference.setType(StringUtils.isBlank(type) ? "web" : type);
    conference.setUrl(url);
    agendaEventConferenceService.saveEventConferences(eventId, Collections.singletonList(conference));
    return new ConferenceModel(conference.getType(), conference.getUrl());
  }

  // Keyword search over agenda events the current user can access, optionally restricted to a space.
  // Note: the underlying search index has no date-range filter, so start/end are applied as a post-filter here.
  public List<AgendaEventModel> searchAgendaEvents(String query,
                                                   String start,
                                                   String end,
                                                   Long spaceId) {
    if (StringUtils.isBlank(query)) {
      throw new IllegalArgumentException("Parameter 'query' is mandatory");
    }
    long userIdentityId = getCurrentUserIdentityId();
    List<Long> spaceIdentityIds = spaceId == null || spaceId == 0 ? null
                                                                   : getSpaceIdentityIds(Collections.singletonList(spaceId));
    AgendaEventSearchFilter filter = new AgendaEventSearchFilter(userIdentityId,
                                                                 TIMEZONE,
                                                                 query,
                                                                 spaceIdentityIds,
                                                                 null,
                                                                 null,
                                                                 0,
                                                                 MAX_LIMIT);
    List<EventSearchResult> results = agendaEventService.search(filter);
    ZonedDateTime startFilter = StringUtils.isBlank(start) ? null : toZonedDateTime(start);
    ZonedDateTime endFilter = StringUtils.isBlank(end) ? null : toZonedDateTime(end);
    return results.stream()
                  .filter(event -> startFilter == null || event.getEnd() == null || !event.getEnd().isBefore(startFilter))
                  .filter(event -> endFilter == null || event.getStart() == null || !event.getStart().isAfter(endFilter))
                  .map(this::toAgendaEventModel)
                  .filter(Objects::nonNull)
                  .toList();
  }

  // ==========================================================================
  // Scheduling helpers
  // ==========================================================================

  /**
   * Resolves usernames to identity ids, keeping the order they were asked in
   * and dropping the ones that resolve to nothing.
   *
   * @param usernames the usernames to resolve
   * @return the usernames that resolved, keyed by their identity id
   */
  private Map<Long, String> resolveUsers(List<String> usernames) {
    Map<Long, String> usernamesByIdentityId = new LinkedHashMap<>();
    for (String username : usernames) {
      Identity identity = identityManager.getOrCreateUserIdentity(username);
      if (identity != null) {
        usernamesByIdentityId.put(identity.getIdentityId(), username);
      }
    }
    return usernamesByIdentityId;
  }

  /**
   * Reads back the username behind an identity id.
   *
   * @param identityId the identity to name
   * @return the username, or {@code null} when the identity is unknown
   */
  private String usernameOf(long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity == null ? null : identity.getRemoteId();
  }

  /**
   * Collects the identity ids of an event's attendees.
   *
   * @param attendees the attendees, possibly {@code null}
   * @return their identity ids, never {@code null}
   */
  private List<Long> toIdentityIds(List<EventAttendee> attendees) {
    return attendees == null ? List.of() : attendees.stream().map(EventAttendee::getIdentityId).toList();
  }

  /**
   * Maps the service's time blocks to their RFC3339 tool representation.
   *
   * @param blocks the blocks to map
   * @return the mapped blocks, in the same order
   */
  private List<TimeBlockModel> toTimeBlocks(List<TimeBlock> blocks) {
    return blocks.stream().map(block -> toTimeBlock(block.getStart(), block.getEnd())).toList();
  }

  /**
   * Builds the attendee clash report decorating a create, update or date-poll
   * confirmation.
   * <p>
   * Glue only. The report covers the attendees the acting user is allowed to
   * read and names the ones it could not check, so a caller must read
   * {@code all_available} as "no clash was found", never as "everyone is
   * free" — see {@link AvailabilityConflicts}.
   *
   * @param attendees the event's attendees
   * @param start proposed window start
   * @param end proposed window end
   * @return the clash report
   */
  private ConflictsModel computeConflicts(List<EventAttendee> attendees, ZonedDateTime start, ZonedDateTime end) {
    AvailabilityConflicts availabilityConflicts = agendaAvailabilityService.getConflicts(toIdentityIds(attendees),
                                                                                        start,
                                                                                        end,
                                                                                        getCurrentUserIdentityId());
    List<AttendeeConflictModel> conflicts = availabilityConflicts.getConflicts()
                                                                 .stream()
                                                                 .map(conflict -> new AttendeeConflictModel(usernameOf(conflict.getIdentityId()),
                                                                                                            CONFLICT_STATUS_BUSY,
                                                                                                            toTimeBlock(conflict.getOverlap()
                                                                                                                                .getStart(),
                                                                                                                        conflict.getOverlap()
                                                                                                                                .getEnd())))
                                                                 .toList();
    List<String> notDisclosed = availabilityConflicts.getNotDisclosedIdentityIds()
                                                     .stream()
                                                     .map(this::usernameOf)
                                                     .filter(Objects::nonNull)
                                                     .toList();
    return new ConflictsModel(availabilityConflicts.isAllAvailable(), conflicts, notDisclosed);
  }

  /**
   * Orders date-poll slots so the ones suiting the most attendees come first.
   * <p>
   * Glue only; the ordering, and the ACL that decides which attendees can be
   * counted, belong to {@link AgendaAvailabilityService}.
   *
   * @param dateOptions the options to order
   * @param attendees the event's attendees
   * @return the options, most-available first
   */
  private List<EventDateOption> rankDateOptionsByAvailability(List<EventDateOption> dateOptions,
                                                              List<EventAttendee> attendees) {
    return agendaAvailabilityService.rankDateOptionsByAvailability(dateOptions,
                                                                   toIdentityIds(attendees),
                                                                   getCurrentUserIdentityId());
  }

  // Build an EventRecurrence from simple frequency/interval/until/count params, or null when no frequency is given.
  private EventRecurrence buildRecurrence(String frequency, Integer interval, String until, Integer count) {
    if (StringUtils.isBlank(frequency)) {
      return null;
    }
    EventRecurrenceFrequency recurrenceFrequency = EventRecurrenceFrequency.valueOf(frequency.trim().toUpperCase());
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(recurrenceFrequency);
    recurrence.setInterval(interval == null || interval <= 0 ? 1 : interval);
    recurrence.setType(toRecurrenceType(recurrenceFrequency));
    if (count != null && count > 0) {
      recurrence.setCount(count);
    } else if (StringUtils.isNotBlank(until)) {
      recurrence.setUntil(LocalDate.parse(until.trim().substring(0, 10)));
    }
    return recurrence;
  }

  // Map a recurrence frequency to the display recurrence type (CUSTOM for hourly/minutely/secondly).
  private EventRecurrenceType toRecurrenceType(EventRecurrenceFrequency frequency) {
    return switch (frequency) {
      case DAILY -> EventRecurrenceType.DAILY;
      case WEEKLY -> EventRecurrenceType.WEEKLY;
      case MONTHLY -> EventRecurrenceType.MONTHLY;
      case YEARLY -> EventRecurrenceType.YEARLY;
      default -> EventRecurrenceType.CUSTOM;
    };
  }

  // Parse a "<number> <MINUTE|HOUR|DAY|WEEK>" reminder string into an EventReminder for the given user.
  private EventReminder parseReminder(String reminder, long userIdentityId) {
    if (StringUtils.isBlank(reminder)) {
      throw new IllegalArgumentException("Empty reminder, expected '<number> <MINUTE|HOUR|DAY|WEEK>'");
    }
    String[] parts = reminder.trim().split("\\s+");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid reminder '%s', expected '<number> <MINUTE|HOUR|DAY|WEEK>'".formatted(reminder));
    }
    int before = Integer.parseInt(parts[0]);
    ReminderPeriodType periodType = ReminderPeriodType.valueOf(parts[1].toUpperCase());
    return new EventReminder(userIdentityId, before, periodType);
  }

  // Load an event's current attendees as EventAttendee list (empty when none), used for conflict computation.
  private List<EventAttendee> getExistingEventAttendees(long eventId) {
    EventAttendeeList attendeeList = agendaEventAttendeeService.getEventAttendees(eventId);
    return attendeeList == null || attendeeList.getEventAttendees() == null ? Collections.emptyList()
                                                                            : attendeeList.getEventAttendees();
  }

  // Map an EventDateOption to its DTO with vote count and voter usernames.
  private DatePollOptionModel toDatePollOptionModel(EventDateOption option) {
    List<String> voters = option.getVoters() == null ? Collections.emptyList()
                                                      : option.getVoters()
                                                              .stream()
                                                              .map(identityManager::getIdentity)
                                                              .filter(Objects::nonNull)
                                                              .filter(Identity::isUser)
                                                              .map(Identity::getRemoteId)
                                                              .toList();
    return new DatePollOptionModel(option.getId(),
                                   formatDate(Date.from(option.getStart().toInstant())),
                                   formatDate(Date.from(option.getEnd().toInstant())),
                                   option.isAllDay(),
                                   option.isSelected(),
                                   voters.size(),
                                   voters);
  }

  // Build a TimeBlockModel (RFC3339 start/end) from two ZonedDateTime bounds.
  private TimeBlockModel toTimeBlock(ZonedDateTime start, ZonedDateTime end) {
    return new TimeBlockModel(formatDate(Date.from(start.toInstant())), formatDate(Date.from(end.toInstant())));
  }

  @SneakyThrows
  private AgendaEventModel toAgendaEventModel(Event event) {
    List<AgendaEventAttendeeModel> eventAttendees = getEventAttendees(event);
    String currentUsername = getCurrentUserName();
    EventAttendeeResponse userAnswer = eventAttendees == null ? null :
                                                              eventAttendees.stream()
                                                                            .filter(eat -> eat.getUser() != null
                                                                                           && StringUtils.equals(eat.getUser()
                                                                                                                    .getUsername(),
                                                                                                                 currentUsername))
                                                                            .map(AgendaEventAttendeeModel::getResponse)
                                                                            .findFirst()
                                                                            .orElse(null);
    return new AgendaEventModel(event.getId(),
                                event.getParentId(),
                                getCalendarSpaceId(event.getCalendarId()),
                                event.getSummary(),
                                event.getDescription(),
                                formatDate(Date.from(event.getStart()
                                                          .toInstant())),
                                formatDate(Date.from(event.getEnd()
                                                          .toInstant())),
                                event.getLocation(),
                                getUrl(event.getId()),
                                getConferenceUrl(event),
                                event.isAllDay(),
                                eventAttendees,
                                userAnswer,
                                getUserModel(event.getCreatorId()),
                                null);
  }

  private List<EventAttendee> toEventAttendees(List<String> attendeeUsernames, boolean includeCurrentUser) {
    List<EventAttendee> attendees = CollectionUtils.isEmpty(attendeeUsernames) ?
                                                                               Collections.emptyList() :
                                                                               attendeeUsernames.stream()
                                                                                                .map(identityManager::getOrCreateUserIdentity)
                                                                                                .filter(Objects::nonNull)
                                                                                                .map(i -> new EventAttendee(0l,
                                                                                                                            i.getIdentityId(),
                                                                                                                            EventAttendeeResponse.NEEDS_ACTION))
                                                                                                .toList();
    attendees = new ArrayList<>(attendees);
    if (includeCurrentUser) {
      attendees.add(new EventAttendee(0l,
                                      getCurrentUserIdentityId(),
                                      EventAttendeeResponse.ACCEPTED));
    }
    return attendees;
  }

  /**
   * Resolve the space id owning the given calendar, degrading gracefully to 0 when the owner identity is missing,
   * deleted, or its space can no longer be resolved (stale pretty name / space-deletion race). This method backs
   * {@link #toAgendaEventModel(Event)}, which every read/list tool funnels through, so a null space must not NPE and
   * break the whole listing.
   *
   * @param calendarId the calendar id whose owning space id is looked up
   * @return the owning space id, or 0 when it cannot be resolved
   */
  private long getCalendarSpaceId(long calendarId) {
    Calendar agendaCalendar = agendaCalendarService.getCalendarById(calendarId);
    long ownerIdentityId = agendaCalendar.getOwnerId();
    Identity ownerIdentity = identityManager.getIdentity(ownerIdentityId);
    if (ownerIdentity == null || ownerIdentity.isDeleted()) {
      return 0l;
    } else {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (space == null) {
        return 0l;
      }
      return space.getSpaceId();
    }
  }

  private long getSpaceCalendarId(long spaceId) throws ObjectNotFoundException {
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ObjectNotFoundException("Space with id %s not found".formatted(spaceId));
    }
    Identity ownerIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
    Calendar agendaCalendar = agendaCalendarService.getOrCreateCalendarByOwnerId(ownerIdentity.getIdentityId());
    return agendaCalendar.getId();
  }

  // Resolve the target space id from an explicit space_id or a space name. Every event must belong to a space, so a
  // clear, actionable error (listing the user's spaces) is raised when neither an id nor a resolvable name is given.
  private long resolveSpaceId(Long spaceId, String spaceName) {
    if (spaceId != null && spaceId != 0) {
      return spaceId;
    }
    if (StringUtils.isNotBlank(spaceName)) {
      Space space = findUserSpaceByName(spaceName.trim());
      if (space != null) {
        return space.getSpaceId();
      }
      throw new IllegalArgumentException("Space '%s' wasn't found among the spaces you belong to. %s".formatted(spaceName.trim(),
                                                                                                               mySpacesHint()));
    }
    throw new IllegalArgumentException("An event must belong to a space. Please tell me which space to use. %s".formatted(mySpacesHint()));
  }

  // Find one of the current user's member spaces by pretty name or (case-insensitive) display name.
  private Space findUserSpaceByName(String spaceName) {
    Space space = spaceService.getSpaceByPrettyName(spaceName);
    if (space != null && spaceService.isMember(space, getCurrentUserName())) {
      return space;
    }
    for (Space memberSpace : listUserSpaces(100)) {
      if (StringUtils.equalsIgnoreCase(memberSpace.getDisplayName(), spaceName)
          || StringUtils.equalsIgnoreCase(memberSpace.getPrettyName(), spaceName)) {
        return memberSpace;
      }
    }
    return null;
  }

  // Short hint listing up to 10 of the current user's spaces to guide the model/user toward picking one.
  private String mySpacesHint() {
    List<Space> spaces = listUserSpaces(10);
    if (spaces.isEmpty()) {
      return "You don't seem to be a member of any space yet.";
    }
    return "You're a member of: " + String.join(", ", spaces.stream().map(Space::getDisplayName).toList()) + ".";
  }

  // Load up to 'limit' of the current user's member spaces.
  @SneakyThrows
  private List<Space> listUserSpaces(int limit) {
    ListAccess<Space> memberSpaces = spaceService.getMemberSpaces(getCurrentUserName());
    if (memberSpaces == null) {
      return Collections.emptyList();
    }
    int size = memberSpaces.getSize();
    if (size <= 0) {
      return Collections.emptyList();
    }
    return Arrays.asList(memberSpaces.load(0, Math.min(size, limit)));
  }

  // Verify the current user is allowed to create events in the given space (REDACT permission on the space).
  private void checkSpaceCreatePermission(long spaceId) throws IllegalAccessException {
    if (!userAcl.hasPermission(SpaceAclPlugin.OBJECT_TYPE,
                               String.valueOf(spaceId),
                               SpaceAclPlugin.REDACT_PERMISSION_TYPE,
                               getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_CREATE_EVENT.formatted(spaceId));
    }
  }

  private List<Long> getSpaceIdentityIds(List<Long> spaceIds) {
    if (CollectionUtils.isEmpty(spaceIds)) {
      return Collections.emptyList();
    }
    List<String> spaceIdsString = spaceIds.stream().map(String::valueOf).toList();
    return SpaceUtils.getSpaceIdentityIds(getCurrentUserName(), spaceIdsString).stream().map(Long::valueOf).toList();
  }

  private long getSpaceIdentityId(long spaceId) {
    Space space = spaceService.getSpaceById(spaceId);
    Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
    return spaceIdentity.getIdentityId();
  }

  private String getConferenceUrl(Event event) {
    long eventId = isComputedOccurrence(event) ? event.getParentId() : event.getId();
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    if (CollectionUtils.isEmpty(eventConferences)) {
      return null;
    } else {
      return eventConferences.stream()
                             .map(EventConference::getUrl)
                             .filter(StringUtils::isNotBlank)
                             .findFirst()
                             .orElse(null);
    }
  }

  private String getUrl(long eventId) throws ObjectNotFoundException {
    return "%s%s".formatted(CommonsUtils.getCurrentDomain(),
                            permanentLinkService.getLink(new PermanentLinkObject(AgendaEventPermanentLinkPlugin.OBJECT_TYPE,
                                                                                 String.valueOf(eventId))));
  }

  private List<AgendaEventAttendeeModel> getEventAttendees(Event event) {
    long eventId = isComputedOccurrence(event) ? event.getParentId() : event.getId();
    EventAttendeeList eventAttendeeList = agendaEventAttendeeService.getEventAttendees(eventId);
    if (eventAttendeeList == null || CollectionUtils.isEmpty(eventAttendeeList.getEventAttendees())) {
      return Collections.emptyList();
    } else {
      return eventAttendeeList.getEventAttendees()
                              .stream()
                              .map(eat -> new AgendaEventAttendeeModel(getUserModel(eat.getIdentityId()),
                                                                       getSpaceModel(eat.getIdentityId()),
                                                                       eat.getResponse()))
                              .toList();
    }
  }

  private List<EventReminder> getDefaultUserEventReminders() {
    long identityId = getCurrentUserIdentityId();
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
    List<EventReminderParameter> reminderParameters = agendaUserSettings.getReminders();
    if (reminderParameters != null && !reminderParameters.isEmpty()) {
      return reminderParameters.stream()
                               .map(reminderParameter -> new EventReminder(identityId,
                                                                           reminderParameter.getBefore(),
                                                                           reminderParameter.getBeforePeriodType()))
                               .toList();
    } else {
      return Collections.emptyList();
    }
  }

  private boolean isComputedOccurrence(Event event) {
    return event.getId() == 0 && event.getParentId() > 0;
  }

  private UserModel getUserModel(long identityId) {
    if (identityId == 0) {
      return null;
    } else {
      Locale locale = getCurrentUserLocale();
      Identity userIdentity = identityManager.getIdentity(identityId);
      if (userIdentity == null || !userIdentity.isUser()) {
        return null;
      } else {
        return toUserModel(identityManager,
                           profilePropertyService,
                           userAcl,
                           translationService,
                           portalConfigService,
                           userIdentity.getRemoteId(),
                           getCurrentUserName(),
                           locale,
                           false);
      }
    }
  }

  private SpaceModel getSpaceModel(long identityId) {
    if (identityId == 0) {
      return null;
    } else {
      Identity spaceIdentity = identityManager.getIdentity(identityId);
      if (spaceIdentity == null || !spaceIdentity.isSpace()) {
        return null;
      } else {
        return toSpaceModel(spaceService,
                            spaceService.getSpaceByPrettyName(spaceIdentity.getRemoteId()),
                            getCurrentUserName());
      }
    }
  }

  private ZonedDateTime toZonedDateTime(String dateString) {
    return AgendaDateUtils.parseRFC3339ToZonedDateTime(dateString, TIMEZONE);
  }

  private long getCurrentUserIdentityId() {
    return identityManager.getOrCreateUserIdentity(getCurrentUserName()).getIdentityId();
  }

}
