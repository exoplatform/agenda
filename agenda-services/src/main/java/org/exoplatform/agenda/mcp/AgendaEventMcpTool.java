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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.mcp.model.AgendaEventAttendeeModel;
import org.exoplatform.agenda.mcp.model.AgendaEventCollectionModel;
import org.exoplatform.agenda.mcp.model.AgendaEventModel;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventPermission;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.agenda.plugin.AgendaEventAclPlugin;
import org.exoplatform.agenda.plugin.AgendaEventPermanentLinkPlugin;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventConferenceService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
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

  private static final String                MSG_PARAMETER_SPACE_ID_MANDATORY     = "parameter 'space_id' is mandatory";

  private static final String                MSG_PARAMETER_EVENT_ID_MADATORY      = "Parameter 'event_id' is madatory";

  private static final String                MSG_PARAMETER_ATTENDEE_IS_MANDATORY  = "Parameter 'attendee_usernames' is mandatory";

  private static final String                MSG_USER_NOT_ALLOWED_TO_UPDATE       =
                                                                            "User isn't allowed to update the event with id %s";

  private static final ZoneOffset            TIMEZONE                             = ZoneOffset.UTC;

  private static final int                   MAX_LIMIT                            = 100;

  private final SpaceService                 spaceService;

  private final IdentityManager              identityManager;

  private final AgendaCalendarService        agendaCalendarService;

  private final AgendaEventService           agendaEventService;

  private final AgendaEventAttendeeService   agendaEventAttendeeService;

  private final AgendaEventConferenceService agendaEventConferenceService;

  private final AgendaUserSettingsService    agendaUserSettingsService;

  private final ProfilePropertyService       profilePropertyService;

  private final UserACL                      userAcl;

  private final TranslationService           translationService;

  private final PermanentLinkService         permanentLinkService;

  private final UserPortalConfigService      portalConfigService;

  @Autowired
  public AgendaEventMcpTool(AgendaCalendarService agendaCalendarService, // NOSONAR
                            AgendaEventService agendaEventService,
                            AgendaEventConferenceService agendaEventConferenceService,
                            AgendaEventAttendeeService agendaEventAttendeeService,
                            AgendaUserSettingsService agendaUserSettingsService,
                            UserPortalConfigService portalConfigService,
                            ProfilePropertyService profilePropertyService,
                            PermanentLinkService permanentLinkService,
                            TranslationService translationService,
                            IdentityManager identityManager,
                            SpaceService spaceService,
                            UserACL userAcl) {
    this.agendaEventService = agendaEventService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.agendaEventConferenceService = agendaEventConferenceService;
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
      throw new ObjectNotFoundException("Agenda Event with id %s not found");
    }
    return toAgendaEventModel(event);
  }

  public AgendaEventModel createAgendaEvent(Long spaceId,
                                            String summary,
                                            String description,
                                            String location,
                                            String start,
                                            String end,
                                            List<String> attendeeUsernames) throws IllegalAccessException,
                                                                            ObjectNotFoundException,
                                                                            AgendaException {
    if (spaceId == null || spaceId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_SPACE_ID_MANDATORY);
    } else if (!userAcl.hasPermission(SpaceAclPlugin.OBJECT_TYPE,
                                      String.valueOf(spaceId),
                                      SpaceAclPlugin.REDACT_PERMISSION_TYPE,
                                      getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_CREATE_EVENT.formatted(spaceId));
    }
    long userIdentityId = getCurrentUserIdentityId();
    ZonedDateTime startDate = toZonedDateTime(start);
    ZonedDateTime endDate = toZonedDateTime(end);
    Event event = new Event(0l,
                            0l,
                            getSpaceCalendarId(spaceId),
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
                            null,
                            null,
                            new EventPermission(true, true),
                            true,
                            true);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        toEventAttendees(attendeeUsernames, true),
                                                        null,
                                                        getDefaultUserEventReminders(),
                                                        null,
                                                        null,
                                                        true,
                                                        userIdentityId);
    return toAgendaEventModel(createdEvent);
  }

  public void declineAgendaEventInvitation(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
    } else if (!userAcl.hasAccessPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                            String.valueOf(eventId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_ACCESS_EVENT.formatted(eventId));
    }
    agendaEventAttendeeService.sendEventResponse(eventId, getCurrentUserIdentityId(), EventAttendeeResponse.DECLINED);
  }

  public void cancelAgendaEvent(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    declineAgendaEventInvitation(eventId);
  }

  public void acceptAgendaEventInvitation(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
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
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
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
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
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

  public AgendaEventModel updateAgendaEvent(Long eventId,
                                            String summary,
                                            String description,
                                            String location,
                                            String start,
                                            String end) throws IllegalAccessException, ObjectNotFoundException, AgendaException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
    } else if (!userAcl.hasEditPermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                          String.valueOf(eventId),
                                          getCurrentUserAclIdentity())) {
      throw new IllegalAccessException(MSG_USER_NOT_ALLOWED_TO_UPDATE.formatted(eventId));
    }
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
    agendaEventService.updateEventFields(eventId,
                                         fieldsToUpdate,
                                         false,
                                         false,
                                         getCurrentUserIdentityId());
    return getAgendaEventById(eventId);
  }

  public void deleteAgendaEvent(Long eventId) throws IllegalAccessException, ObjectNotFoundException {
    if (eventId == null || eventId == 0) {
      throw new IllegalArgumentException(MSG_PARAMETER_EVENT_ID_MADATORY);
    } else if (!userAcl.hasDeletePermission(AgendaEventAclPlugin.OBJECT_TYPE,
                                            String.valueOf(eventId),
                                            getCurrentUserAclIdentity())) {
      throw new IllegalAccessException("User isn't allowed to delete the event with id %s".formatted(eventId));
    }
    agendaEventService.deleteEventById(eventId, getCurrentUserIdentityId());
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
                                getUserModel(event.getCreatorId()));
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

  private long getCalendarSpaceId(long calendarId) {
    Calendar agendaCalendar = agendaCalendarService.getCalendarById(calendarId);
    long ownerIdentityId = agendaCalendar.getOwnerId();
    Identity ownerIdentity = identityManager.getIdentity(ownerIdentityId);
    if (ownerIdentity == null || ownerIdentity.isDeleted()) {
      return 0l;
    } else {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
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
