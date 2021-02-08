/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.*;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

import io.swagger.annotations.*;
import io.swagger.jaxrs.PATCH;

@Path("/v1/agenda/events")
@Api(value = "/v1/agenda/events", description = "Manages agenda events associated to users and spaces") // NOSONAR
public class AgendaEventRest implements ResourceContainer {

  private static final String          AGENDA_APP_URI = "agenda";

  private static final Log             LOG            = ExoLogger.getLogger(AgendaEventRest.class);

  private IdentityManager              identityManager;

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventService           agendaEventService;

  private AgendaEventReminderService   agendaEventReminderService;

  private AgendaEventAttendeeService   agendaEventAttendeeService;

  private AgendaEventAttachmentService agendaEventAttachmentService;

  private AgendaEventConferenceService agendaEventConferenceService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private AgendaEventDatePollService   agendaEventDatePollService;

  private String                       defaultSite    = null;

  public AgendaEventRest(IdentityManager identityManager,
                         UserPortalConfigService portalConfigService,
                         AgendaCalendarService agendaCalendarService,
                         AgendaEventService agendaEventService,
                         AgendaEventConferenceService agendaEventConferenceService,
                         AgendaRemoteEventService agendaRemoteEventService,
                         AgendaEventDatePollService agendaEventDatePollService,
                         AgendaEventAttachmentService agendaEventAttachmentService,
                         AgendaEventReminderService agendaEventReminderService,
                         AgendaEventAttendeeService agendaEventAttendeeService) {
    this.identityManager = identityManager;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.agendaEventReminderService = agendaEventReminderService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.agendaEventAttachmentService = agendaEventAttachmentService;
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.agendaEventDatePollService = agendaEventDatePollService;
    if (portalConfigService != null && portalConfigService.getDefaultPortal() != null) {
      this.defaultSite = portalConfigService.getDefaultPortal();
    } else {
      this.defaultSite = "dw";
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves the list of events available for an owner of type user or space, identitifed by its identity technical identifier."
          + " If no designated owner, all events available for authenticated user will be retrieved.",
      httpMethod = "GET", response = Response.class, produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response getEvents(
                            @ApiParam(value = "Identity technical identifiers of calendar owners", required = false) @QueryParam(
                              "ownerIds"
                            ) List<Long> ownerIds,
                            @ApiParam(
                                value = "Attendee identity identifier to filter on events where user is attendee", required = true
                            ) @QueryParam("attendeeIdentityId") long attendeeIdentityId,
                            @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                              "expand"
                            ) String expand,
                            @ApiParam(value = "Start datetime using RFC-3339 representation", required = true) @QueryParam(
                              "start"
                            ) String start,
                            @ApiParam(value = "End datetime using RFC-3339 representation", required = false) @QueryParam(
                              "end"
                            ) String end,
                            @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                              "timeZoneId"
                            ) String timeZoneId,
                            @ApiParam(
                                value = "Limit of results to return, used only when end date isn't set", required = false,
                                defaultValue = "10"
                            ) @QueryParam("limit") int limit,
                            @ApiParam(
                                value = "Attendee Response statuses to filter events by attendee response",
                                required = false
                            ) @QueryParam("responseTypes") List<EventAttendeeResponse> responseTypes) {

    if (StringUtils.isBlank(start)) {
      return Response.status(Status.BAD_REQUEST).entity("Start datetime is mandatory").build();
    }
    if (StringUtils.isBlank(timeZoneId)) {
      return Response.status(Status.BAD_REQUEST).entity("Time zone is mandatory").build();
    }

    ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);

    ZonedDateTime endDatetime = null;
    if (StringUtils.isBlank(end)) {
      if (limit <= 0) {
        limit = 10;
      }
    } else {
      endDatetime = AgendaDateUtils.parseRFC3339ToZonedDateTime(end, userTimeZone);
    }

    ZonedDateTime startDatetime = AgendaDateUtils.parseRFC3339ToZonedDateTime(start, userTimeZone);
    if (endDatetime != null && (endDatetime.isBefore(startDatetime) || endDatetime.equals(startDatetime))) {
      return Response.status(Status.BAD_REQUEST).entity("Start date must be before end date").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      EventFilter eventFilter = new EventFilter(attendeeIdentityId,
                                                ownerIds,
                                                responseTypes,
                                                startDatetime,
                                                endDatetime,
                                                limit);
      List<Event> events = agendaEventService.getEvents(eventFilter, userTimeZone, userIdentityId);
      Map<Long, List<EventAttendeeEntity>> attendeesByParentEventId = new HashMap<>();
      Map<Long, List<EventAttachmentEntity>> attachmentsByParentEventId = new HashMap<>();
      Map<Long, List<EventConference>> conferencesByParentEventId = new HashMap<>();
      Map<Long, List<EventReminder>> remindersByParentEventId = new HashMap<>();
      Map<Long, List<EventDateOptionEntity>> dateOptionsByParentEventId = new HashMap<>();
      Map<Long, RemoteEvent> remoteEventByParentEventId = new HashMap<>();
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      List<EventEntity> eventEntities = events.stream().map(event -> {
        EventEntity eventEntity = RestEntityBuilder.fromEvent(agendaCalendarService,
                                                              agendaEventService,
                                                              identityManager,
                                                              event,
                                                              userTimeZone);
        if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
          try {
            fillAttendees(eventEntity, attendeesByParentEventId, 0);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        } else if (expandProperties.contains("response")) {
          try {
            fillAttendees(eventEntity, attendeesByParentEventId, userIdentityId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("attachments")) {
          try {
            fillAttachments(eventEntity, attachmentsByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
          try {
            fillConferences(eventEntity, conferencesByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event conferences, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
          try {
            fillReminders(eventEntity, userIdentityId, remindersByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
          try {
            fillDateOptions(eventEntity, userTimeZone, dateOptionsByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event date options, retrieve event without it", e);
          }
        }
        fillRemoteEvent(eventEntity, userIdentityId);
        if (eventEntity.getParent() != null) {
          fillRemoteEvent(eventEntity.getParent(), userIdentityId, remoteEventByParentEventId);
        }
        if (isComputedOccurrence(eventEntity)) {
          cleanupAttachedEntitiesIds(eventEntity);
        }
        return eventEntity;
      }).collect(Collectors.toList());

      EventList eventList = new EventList();
      eventList.setEvents(eventEntities);
      eventList.setStart(start);
      eventList.setEnd(end);
      eventList.setLimit(limit);
      return Response.ok(eventList).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized events of owner Id '{}'", RestUtils.getCurrentUser(), ownerIds, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of events", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves an event identified by its technical identifier.", httpMethod = "GET", response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response getEventById(
                               @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                 "eventId"
                               ) long eventId,
                               @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                                 "expand"
                               ) String expand,
                               @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                 "timeZoneId"
                               ) String timeZoneId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
      EventEntity eventEntity = getEventByIdAndUser(eventId,
                                                    RestUtils.getCurrentUserIdentityId(identityManager),
                                                    userTimeZone,
                                                    expandProperties);
      if (eventEntity == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        return Response.ok(eventEntity).build();
      }
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized event with Id '{}'", currentUser, eventId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("occurrence/{parentEventId}/{occurrenceId}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves an event identified by its technical identifier.", httpMethod = "GET", response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response getEventOccurrence(
                                     @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                       "parentEventId"
                                     ) long parentEventId,
                                     @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                       "occurrenceId"
                                     ) String occurrenceId,
                                     @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                                       "expand"
                                     ) String expand,
                                     @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                       "timeZoneId"
                                     ) String timeZoneId) {
    if (parentEventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (StringUtils.isBlank("occurrenceId")) {
      return Response.status(Status.BAD_REQUEST).entity("Event occurrence identifier is mandatory").build();
    }

    try {
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
      long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
      ZonedDateTime occurrenceDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, userTimeZone);
      Event event = agendaEventService.getEventOccurrence(parentEventId, occurrenceDate, userTimeZone, identityId);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      EventEntity eventEntity = getEventEntity(event, userTimeZone, expandProperties);
      return Response.ok(eventEntity).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized event with parentId '{}' and occurrenceId '{}'",
               RestUtils.getCurrentUser(),
               parentEventId,
               occurrenceId,
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event with parentId '{}' and occurrenceId '{}'",
               RestUtils.getCurrentUser(),
               parentEventId,
               occurrenceId,
               e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/exceptionalOccurrences")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves the list of exceptional occurrences of an event.",
      httpMethod = "GET", response = Response.class, produces = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response getEventExceptionalOccurrences(
                                                 @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                                   "eventId"
                                                 ) long parentEventId,
                                                 @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                                                   "expand"
                                                 ) String expand,
                                                 @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                                   "timeZoneId"
                                                 ) String timeZoneId) {
    List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                  ","));
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
      List<Event> events = agendaEventService.getExceptionalOccurrenceEvents(parentEventId, userTimeZone, userIdentityId);
      List<EventEntity> eventEntities = events.stream()
                                              .map(event -> getEventEntity(event, userTimeZone, expandProperties))
                                              .collect(Collectors.toList());
      return Response.ok(eventEntities).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized events", RestUtils.getCurrentUser(), e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of events", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Create a new event", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response createEvent(
                              @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                "timeZoneId"
                              ) String timeZoneId,
                              @ApiParam(value = "Event object to create", required = true) EventEntity eventEntity) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getCalendar() == null || eventEntity.getCalendar().getOwner() == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND).build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      Event event = createEvent(eventEntity, userIdentityId, timeZoneId);
      return getEventById(event.getId(), "all", timeZoneId == null ? event.getTimeZoneId().getId() : timeZoneId);
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create an event in calendar '{}'",
               RestUtils.getCurrentUser(),
               eventEntity.getCalendar(),
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (AgendaException e) {
      LOG.debug("Error in event validation", e);
      return Response.serverError().entity(e.getAgendaExceptionType().getCompleteMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Update an existing event", httpMethod = "PUT", response = Response.class, consumes = "application/json")
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response updateEvent(
                              @ApiParam(value = "Event object to update", required = true) EventEntity eventEntity,
                              @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                "timeZoneId"
                              ) String timeZoneId) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getId() <= 0) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_ID_MANDATORY.getCompleteMessage()).build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      checkCalendar(eventEntity);

      List<EventAttendeeEntity> attendeeEntities = eventEntity.getAttendees();
      List<EventAttendee> attendees = null;
      if (attendeeEntities != null && !attendeeEntities.isEmpty()) {
        attendees = new ArrayList<>();
        for (EventAttendeeEntity attendeeEntity : attendeeEntities) {
          attendees.add(RestEntityBuilder.toEventAttendee(identityManager, eventEntity.getId(), attendeeEntity));
        }
      }

      List<EventAttachmentEntity> attachmentEntities = eventEntity.getAttachments();
      List<EventAttachment> attachments = attachmentEntities == null
          || attachmentEntities.isEmpty() ? null
                                          : attachmentEntities.stream()
                                                              .map(RestEntityBuilder::toEventAttachment)
                                                              .collect(Collectors.toList());

      Event event = RestEntityBuilder.toEvent(eventEntity);
      List<EventReminderEntity> reminderEntities = eventEntity.getReminders();
      List<EventReminder> reminders = null;
      if (reminderEntities != null && !reminderEntities.isEmpty()) {
        reminders = new ArrayList<>();
        for (EventReminderEntity reminderEntity : reminderEntities) {
          reminders.add(RestEntityBuilder.toEventReminder(eventEntity.getId(), reminderEntity));
        }
      }

      RemoteEvent remoteEvent = getRemoteEvent(eventEntity, userIdentityId);

      String userTimeZoneId = timeZoneId == null ? event.getTimeZoneId().getId() : timeZoneId;
      ZoneId userTimeZone = userTimeZoneId == null ? ZoneOffset.UTC : ZoneId.of(userTimeZoneId);

      List<EventDateOptionEntity> dateOptionEntities = eventEntity.getDateOptions();
      List<EventDateOption> dateOptions = dateOptionEntities == null ? Collections.emptyList()
                                                                     : dateOptionEntities.stream()
                                                                                         .map(dateOptionEntity -> RestEntityBuilder.toEventDateOption(dateOptionEntity,
                                                                                                                                                      userTimeZone))
                                                                                         .collect(Collectors.toList());

      agendaEventService.updateEvent(event,
                                     attendees,
                                     eventEntity.getConferences(),
                                     attachments,
                                     reminders,
                                     dateOptions,
                                     remoteEvent,
                                     eventEntity.isSendInvitation(),
                                     userIdentityId);
      return getEventById(event.getId(), "all", userTimeZoneId);
    } catch (AgendaException e) {
      LOG.debug("Error in event validation", e);
      return Response.serverError().entity(e.getAgendaExceptionType().getCompleteMessage()).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a non authorized event", RestUtils.getCurrentUser(), e);
      return Response.status(Status.UNAUTHORIZED).build();
    } catch (Exception e) {
      LOG.warn("Error updating an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}")
  @PATCH
  @Consumes("application/x-www-form-urlencoded")
  @RolesAllowed("users")
  @ApiOperation(value = "Update an attribute of an existing event", httpMethod = "PATCH", response = Response.class)
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response updateEventFields(
                                    @ApiParam(
                                        value = "Event technical identifier",
                                        required = true
                                    ) @PathParam("eventId") long eventId,
                                    @ApiParam(
                                        value = "Update all event occurrences or only parent occurrence",
                                        required = false,
                                        defaultValue = "false"
                                    ) @QueryParam("updateAllOccurrences") boolean updateAllOccurrences,
                                    @ApiParam(
                                        value = "Whether notify attendees about the modification or not",
                                        required = false,
                                        defaultValue = "false"
                                    ) @QueryParam("sendInvitations") boolean sendInvitations,
                                    @ApiParam(
                                        value = "Event fields to patch",
                                        required = true
                                    ) MultivaluedMap<String, String> eventFields) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(AgendaExceptionType.EVENT_ID_MANDATORY.getCompleteMessage())
                     .build();
    }

    if (eventFields == null || eventFields.isEmpty()) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(AgendaExceptionType.EVENT_FIELDS_MANDATORY.getCompleteMessage())
                     .build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      Event event = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).entity("Event not found").build();
      }

      if (eventFields.containsKey("remoteId") || eventFields.containsKey("remoteProviderId")
          || eventFields.containsKey("remoteProviderName")) {
        String remoteId = eventFields.containsKey("remoteId") ? eventFields.get("remoteId").get(0) : null;
        String remoteProviderId = eventFields.containsKey("remoteProviderId") ? eventFields.get("remoteProviderId").get(0) : null;
        String remoteProviderName = eventFields.containsKey("remoteProviderName") ? eventFields.get("remoteProviderName").get(0)
                                                                                  : null;

        RemoteEvent remoteEvent = new RemoteEvent(0,
                                                  eventId,
                                                  userIdentityId,
                                                  remoteId,
                                                  remoteProviderId == null ? 0 : Long.parseLong(remoteProviderId),
                                                  remoteProviderName);
        agendaRemoteEventService.saveRemoteEvent(eventId, remoteEvent, userIdentityId);
      }

      eventFields.remove("remoteId");
      eventFields.remove("remoteProviderId");
      eventFields.remove("remoteProviderName");

      if (!eventFields.isEmpty()) {
        agendaEventService.updateEventFields(eventId,
                                             eventFields,
                                             updateAllOccurrences,
                                             sendInvitations,
                                             userIdentityId);
      }
      return Response.noContent().build();
    } catch (AgendaException e) {
      LOG.debug("Error in event validation", e);
      return Response.serverError().entity(e.getAgendaExceptionType().getCompleteMessage()).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a non authorized event", RestUtils.getCurrentUser(), e);
      return Response.status(Status.UNAUTHORIZED).build();
    } catch (Exception e) {
      LOG.warn("Error updating an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Path("{eventId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Delete an existing event", httpMethod = "DELETE", response = Response.class)
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response deleteEvent(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId,
                              @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                "timeZoneId"
                              ) String timeZoneId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event technical identifier must be positive").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      ZoneId userTimeZone = timeZoneId == null ? null : ZoneId.of(timeZoneId);
      EventEntity eventEntity = getEventByIdAndUser(eventId,
                                                    userIdentityId,
                                                    userTimeZone,
                                                    Collections.singletonList("all"));
      if (eventEntity == null) {
        return Response.status(Status.NOT_FOUND).entity("Event not found").build();
      }
      agendaEventService.deleteEventById(eventId, userIdentityId);
      return Response.ok(eventEntity).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to delete a non authorized event", RestUtils.getCurrentUser(), e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error deleting an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Path("{eventId}/selectDateOption/{dateOptionId}")
  @RolesAllowed("users")
  @ApiOperation(
      value = "Select an Date Option for an event having multiple dates options",
      httpMethod = "POST",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response selectEventDateOption(
                                        @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                          "eventId"
                                        ) long eventId,
                                        @ApiParam(value = "Event date option technical identifier", required = true) @PathParam(
                                          "dateOptionId"
                                        ) long dateOptionId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (dateOptionId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event date option identifier must be a positive integer").build();
    }

    Identity identity = RestUtils.getCurrentUserIdentity(identityManager);
    if (identity == null) {
      return Response.status(Status.FORBIDDEN).build();
    }
    long userIdentityId = Long.parseLong(identity.getId());
    try {
      agendaEventService.selectEventDateOption(eventId, dateOptionId, userIdentityId);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to select date option a not existing event '{}'", userIdentityId, eventId, e);
      return Response.status(Status.NOT_FOUND).entity("Event Date Option not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to select Date Option on a not authorized event with Id '{}'",
               RestUtils.getCurrentUser(),
               eventId,
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error voting on event with id '{}' with date option '{}'", eventId, dateOptionId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/reminders")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieve preferred reminders for currently authenticated user for an event identified by its technical identifier.",
      httpMethod = "GET", response = Response.class, produces = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response getEventRemindersById(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId, identityId);
      if (reminders == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        return Response.ok(reminders).build();
      }
    } catch (Exception e) {
      LOG.warn("Error retrieving event reminders with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/reminders")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Update the list of preferred reminders for authenticated user on a selected event.", httpMethod = "PUT",
      response = Response.class, consumes = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response saveEventReminders(
                                     @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                       "eventId"
                                     ) long eventId,
                                     @ApiParam(value = "Event occurrence identifier", required = true) @QueryParam(
                                       "occurrenceId"
                                     ) String occurrenceId,
                                     @ApiParam(
                                         value = "Whether apply on Event occurrence and its upcoming or not", required = true
                                     ) @QueryParam(
                                       "upcoming"
                                     ) boolean upcoming,
                                     @ApiParam(
                                         value = "List of reminders to store on event for currently authenticated user",
                                         required = true
                                     ) List<EventReminder> reminders) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      Event event = agendaEventService.getEventById(eventId, null, userIdentityId);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).entity("Event with id " + eventId + " is not found").build();
      }

      if (StringUtils.isBlank(occurrenceId)) {
        agendaEventReminderService.saveEventReminders(event, reminders, userIdentityId);
      } else {
        if (event.getRecurrence() == null) {
          return Response.status(Status.BAD_REQUEST).entity("Event is not recurrent, no occurrenceId is needed").build();
        }

        ZonedDateTime occurrenceIdDateTime = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
        if (upcoming) {
          agendaEventReminderService.saveUpcomingEventReminders(eventId, occurrenceIdDateTime, reminders, userIdentityId);
        } else {
          Event occurrenceEvent = agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceIdDateTime);
          agendaEventReminderService.saveEventReminders(occurrenceEvent, reminders, userIdentityId);
        }
      }

      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access reminders for a not authorized event with Id '{}'", userIdentityId, eventId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating event reminders with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/response")
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @ApiOperation(
      value = "Retrieves currently authenticated (using token or effectively authenticated) user response to an event.",
      httpMethod = "GET", response = Response.class, produces = "text/plain"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response getEventResponse(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId,
                                   @ApiParam(value = "User token to ", required = false) @QueryParam("token") String token) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    long identityId = 0;

    try {
      Identity identity = null;
      if (StringUtils.isNotBlank(token)) {
        identity = agendaEventAttendeeService.decryptUserIdentity(eventId, token, null);
      } else {
        identity = RestUtils.getCurrentUserIdentity(identityManager);
      }
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      identityId = Long.parseLong(identity.getId());
      EventAttendeeResponse response = agendaEventAttendeeService.getEventResponse(eventId, identityId);
      return Response.ok(response.getValue()).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to get event response of a not existing event '{}'", identityId, eventId, e);
      return Response.status(Status.NOT_FOUND).entity("Event not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access invitation response for a not authorized event with Id '{}'",
               RestUtils.getCurrentUser(),
               eventId,
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event response with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/votes")
  @POST
  @Consumes("application/x-www-form-urlencoded")
  @RolesAllowed("users")
  @ApiOperation(
      value = "Registers voted date poll options for currently authenticated user",
      httpMethod = "POST", response = Response.class
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response voteEvent(
                            @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                              "eventId"
                            ) long eventId,
                            @ApiParam(value = "Accepted event date options technical identifier", required = false) @FormParam(
                              "dateOptionId"
                            ) List<Long> dateOptionId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (dateOptionId == null) {
      dateOptionId = Collections.emptyList();
    }

    long identityId = 0;
    try {
      Identity identity = RestUtils.getCurrentUserIdentity(identityManager);
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      identityId = Long.parseLong(identity.getId());
      agendaEventDatePollService.saveEventVotes(eventId, dateOptionId, identityId);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to vote on non existing event '{}'", identityId, eventId, e);
      return Response.status(Status.NOT_FOUND).entity("Event with id " + eventId + " not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to vote on a not authorized event with Id '{}'",
               RestUtils.getCurrentUser(),
               eventId,
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error voting on event with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/votes/{dateOptionId}")
  @POST
  @RolesAllowed("users")
  @ApiOperation(
      value = "Registers voted date poll option for currently authenticated user",
      httpMethod = "POST", response = Response.class
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response voteEventDateOption(
                                      @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                        "eventId"
                                      ) long eventId,
                                      @ApiParam(value = "Event date option technical identifier", required = true) @PathParam(
                                        "dateOptionId"
                                      ) long dateOptionId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (dateOptionId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event date option identifier must be a positive integer").build();
    }

    long identityId = 0;
    try {
      Identity identity = RestUtils.getCurrentUserIdentity(identityManager);
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      identityId = Long.parseLong(identity.getId());
      agendaEventDatePollService.voteDateOption(dateOptionId, identityId);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to vote on non existing event '{}' with date option '{}'",
                identityId,
                eventId,
                dateOptionId,
                e);
      return Response.status(Status.NOT_FOUND).entity("Date option with id " + dateOptionId + " not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to vote on a not authorized event with Id '{}'",
               RestUtils.getCurrentUser(),
               eventId,
               e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error voting on event with id '{}' with date option '{}'", eventId, dateOptionId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/votes/{dateOptionId}")
  @DELETE
  @RolesAllowed("users")
  @ApiOperation(
      value = "Dismisses vote on date poll option for currently authenticated user",
      httpMethod = "DELETE", response = Response.class
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response dismissEventDateOption(
                                         @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                           "eventId"
                                         ) long eventId,
                                         @ApiParam(value = "Event date option technical identifier", required = true) @PathParam(
                                           "dateOptionId"
                                         ) long dateOptionId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (dateOptionId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event date option identifier must be a positive integer").build();
    }

    long identityId = 0;
    try {
      Identity identity = RestUtils.getCurrentUserIdentity(identityManager);
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      identityId = Long.parseLong(identity.getId());
      agendaEventDatePollService.dismissDateOption(dateOptionId, identityId);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to dismiss vote on non existing event '{}' with date option '{}'",
                identityId,
                eventId,
                dateOptionId,
                e);
      return Response.status(Status.NOT_FOUND).entity("Date option with id " + dateOptionId + " not found").build();
    } catch (Exception e) {
      LOG.warn("Error dismissing vote on event with id '{}' with date option '{}'", eventId, dateOptionId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/response/send")
  @GET
  @ApiOperation(
      value = "Send event invitation response for currently authenticated user (using token or effectively authenticated).",
      httpMethod = "GET",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response sendEventResponse(
                                    @ApiParam(value = "Event technical identifier", required = true) @PathParam(
                                      "eventId"
                                    ) long eventId,
                                    @ApiParam(value = "Event occurrence identifier", required = true) @QueryParam(
                                      "occurrenceId"
                                    ) String occurrenceId,
                                    @ApiParam(
                                        value = "Response to event invitation. Possible values: ACCEPTED, DECLINED or TENTATIVE.",
                                        required = true
                                    ) @QueryParam("response") String responseString,
                                    @ApiParam(
                                        value = "User token used to identify user and his response to apply new reponse even when user is not authenticated",
                                        required = false
                                    ) @QueryParam(
                                      "token"
                                    ) String token,
                                    @ApiParam(
                                        value = "Whether redirect to Event details after applying new response or not",
                                        required = false,
                                        defaultValue = "false"
                                    ) @QueryParam(
                                      "redirect"
                                    ) boolean redirect) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (StringUtils.isBlank(responseString)) {
      return Response.status(Status.BAD_REQUEST).entity("Event response is mandatory").build();
    }
    EventAttendeeResponse response = EventAttendeeResponse.fromValue(responseString);
    if (response == null || response == EventAttendeeResponse.NEEDS_ACTION) {
      return Response.status(Status.BAD_REQUEST).entity("Event response is not recognized").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      Identity identity = null;
      if (StringUtils.isNotBlank(token)) {
        identity = agendaEventAttendeeService.decryptUserIdentity(eventId, token, response);
        currentUser = identity.getRemoteId();
      } else if (StringUtils.isNotBlank(currentUser)) {
        identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
      }
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      long identityId = Long.parseLong(identity.getId());
      if (!agendaEventAttendeeService.isEventAttendee(eventId, identityId)) {
        throw new IllegalAccessException("User " + currentUser + " isn't attendee of event with id " + eventId);
      }

      ZonedDateTime occurrenceIdDateTime = null;
      if (StringUtils.isNotBlank(occurrenceId)) {
        occurrenceIdDateTime = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
        Event occurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceIdDateTime);
        if (occurrenceEvent == null) { // Exceptional occurrence not yet created
          occurrenceEvent = agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceIdDateTime);
        }
        eventId = occurrenceEvent.getId();
      }
      agendaEventAttendeeService.sendEventResponse(eventId, identityId, response);
      if (redirect) {
        URI location = new URI("/portal/" + this.defaultSite + "/" + AGENDA_APP_URI + "?eventId=" + eventId); // NOSONAR
        return Response.seeOther(location).build();
      } else {
        return Response.noContent().build();
      }
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Event not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to send invitation response for a not authorized event with Id '{}'", currentUser, eventId);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error sending event invitation response for event with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("search")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Search the list of events available with query for an owner of type user or space, identified by its identity technical identifier."
          + " If no designated owner, all events available for authenticated user will be retrieved.",
      httpMethod = "GET", response = Response.class, produces = "application/json"
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response search(
                         @ApiParam(value = "Term to search", required = true) @QueryParam(
                           "query"
                         ) String query,
                         @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                           "timeZoneId"
                         ) String timeZoneId,
                         @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                           "expand"
                         ) String expand,
                         @ApiParam(value = "Offset", required = false, defaultValue = "0") @QueryParam(
                           "offset"
                         ) int offset,
                         @ApiParam(value = "Limit", required = false, defaultValue = "20") @QueryParam(
                           "limit"
                         ) int limit) throws Exception { // NOSONAR
    if (offset < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Offset must be 0 or positive").build();
    }
    if (limit < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Limit must be positive").build();
    }
    List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                  ","));
    long currentUserId = RestUtils.getCurrentUserIdentityId(identityManager);
    ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
    List<EventSearchResult> searchResults = agendaEventService.search(currentUserId, userTimeZone, query, offset, limit);
    List<EventSearchResultEntity> results = searchResults.stream()
                                                         .map(searchResult -> getEventSearchResultEntity(searchResult,
                                                                                                         userTimeZone,
                                                                                                         expandProperties))
                                                         .collect(Collectors.toList());
    return Response.ok(results).build();
  }

  @Path("datePolls")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves the list of pending date polls for currently authenticated user.",
      httpMethod = "GET",
      response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response getPendingEvents(
                                   @ApiParam(value = "Offset", required = false, defaultValue = "0") @QueryParam(
                                     "offset"
                                   ) int offset,
                                   @ApiParam(
                                       value = "Limit of results to return", required = false
                                   ) @QueryParam("limit") int limit,
                                   @ApiParam(value = "IANA Time zone identitifer", required = false) @QueryParam(
                                     "timeZoneId"
                                   ) String timeZoneId,
                                   @ApiParam(value = "Properties to expand", required = false) @QueryParam(
                                     "expand"
                                   ) String expand) {
    if (offset < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Offset must be 0 or positive").build();
    }
    if (limit < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Limit must be positive").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
    try {
      EventList eventList = new EventList();
      eventList.setLimit(limit);
      if (limit > 0) {
        List<Event> events = agendaEventService.getPendingDatePolls(userIdentityId,
                                                                    userTimeZone,
                                                                    offset,
                                                                    limit);
        List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                    : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                      ","));
        List<EventEntity> eventEntities = events.stream()
                                                .map(event -> getEventEntity(event, userTimeZone, expandProperties))
                                                .collect(Collectors.toList());
        eventList.setEvents(eventEntities);
      }
      eventList.setSize(agendaEventService.countPendingDatePolls(userIdentityId));
      return Response.ok(eventList).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of events", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  private Event createEvent(EventEntity eventEntity, long userIdentityId, String timeZoneId) throws AgendaException,
                                                                                             IllegalAccessException {
    checkCalendar(eventEntity);

    cleanupAttachedEntitiesIds(eventEntity);

    List<EventAttendeeEntity> attendeeEntities = eventEntity.getAttendees();
    List<EventAttendee> attendees = null;
    if (attendeeEntities != null && !attendeeEntities.isEmpty()) {
      attendees = new ArrayList<>();
      for (EventAttendeeEntity attendeeEntity : attendeeEntities) {
        IdentityEntity attendeeIdentity = attendeeEntity.getIdentity();
        String attendeeIdString = RestUtils.getIdentityId(attendeeIdentity, identityManager);
        if (StringUtils.isBlank(attendeeIdString)) {
          throw new AgendaException(AgendaExceptionType.ATTENDEE_IDENTITY_NOT_FOUND);
        }
        attendeeIdentity.setId(attendeeIdString);
        attendees.add(RestEntityBuilder.toEventAttendee(identityManager, eventEntity.getId(), attendeeEntity));
      }
    }

    List<EventAttachmentEntity> attachmentEntities = eventEntity.getAttachments();
    List<EventAttachment> attachments = attachmentEntities == null
        || attachmentEntities.isEmpty() ? null
                                        : attachmentEntities.stream()
                                                            .map(RestEntityBuilder::toEventAttachment)
                                                            .collect(Collectors.toList());
    List<EventReminderEntity> reminderEntities = eventEntity.getReminders();
    List<EventReminder> reminders = null;
    if (reminderEntities != null && !reminderEntities.isEmpty()) {
      reminders = new ArrayList<>();
      for (EventReminderEntity reminderEntity : reminderEntities) {
        reminders.add(RestEntityBuilder.toEventReminder(eventEntity.getId(), reminderEntity));
      }
    }

    RemoteEvent remoteEvent = getRemoteEvent(eventEntity, userIdentityId);

    String userTimeZoneId = timeZoneId == null ? eventEntity.getTimeZoneId() : timeZoneId;
    ZoneId userTimeZone = userTimeZoneId == null ? ZoneOffset.UTC : ZoneId.of(userTimeZoneId);

    List<EventDateOptionEntity> dateOptionEntities = eventEntity.getDateOptions();
    List<EventDateOption> dateOptions = dateOptionEntities == null ? Collections.emptyList()
                                                                   : dateOptionEntities.stream()
                                                                                       .map(dateOptionEntity -> RestEntityBuilder.toEventDateOption(dateOptionEntity,
                                                                                                                                                    userTimeZone))
                                                                                       .collect(Collectors.toList());

    return agendaEventService.createEvent(RestEntityBuilder.toEvent(eventEntity),
                                          attendees,
                                          eventEntity.getConferences(),
                                          attachments,
                                          reminders,
                                          dateOptions,
                                          remoteEvent,
                                          eventEntity.isSendInvitation(),
                                          userIdentityId);
  }

  private void checkCalendar(EventEntity eventEntity) throws AgendaException {
    IdentityEntity identityEntity = eventEntity.getCalendar().getOwner();

    String ownerIdString = RestUtils.getIdentityId(identityEntity, identityManager);
    if (StringUtils.isBlank(ownerIdString)) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND);
    }
    identityEntity.setId(ownerIdString);
    Calendar calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(ownerIdString));
    if (calendar == null) {
      throw new AgendaException(AgendaExceptionType.CALENDAR_NOT_FOUND);
    } else if (eventEntity.getCalendar() == null) {
      eventEntity.setCalendar(RestEntityBuilder.fromCalendar(identityManager, calendar));
    } else {
      eventEntity.getCalendar().setId(calendar.getId());
    }
  }

  private EventEntity getEventByIdAndUser(long eventId,
                                          long identityId,
                                          ZoneId userTimeZone,
                                          List<String> expandProperties) throws IllegalAccessException {
    Event event = agendaEventService.getEventById(eventId, userTimeZone, identityId);
    return getEventEntity(event, userTimeZone, expandProperties);
  }

  private EventEntity getEventEntity(Event event, ZoneId userTimeZone, List<String> expandProperties) {
    if (event == null) {
      return null;
    } else {
      if (userTimeZone == null) {
        if (event.getTimeZoneId() == null) {
          userTimeZone = ZoneOffset.UTC;
        } else {
          userTimeZone = event.getTimeZoneId();
        }
      }
      EventEntity eventEntity = RestEntityBuilder.fromEvent(agendaCalendarService,
                                                            agendaEventService,
                                                            identityManager,
                                                            event,
                                                            userTimeZone);
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
        fillAttendees(eventEntity, 0);
      } else if (expandProperties.contains("response")) {
        fillAttendees(eventEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("attachments")) {
        fillAttachments(eventEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
        fillConferences(eventEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
        fillReminders(eventEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
        fillDateOptions(eventEntity, userTimeZone);
      }
      fillRemoteEvent(eventEntity, userIdentityId);
      if (eventEntity.getParent() != null) {
        fillRemoteEvent(eventEntity.getParent(), userIdentityId);
      }
      if (isComputedOccurrence(eventEntity)) {
        cleanupAttachedEntitiesIds(eventEntity);
      }
      return eventEntity;
    }
  }

  private EventSearchResultEntity getEventSearchResultEntity(EventSearchResult eventSearchResult,
                                                             ZoneId userTimeZone,
                                                             List<String> expandProperties) {
    if (eventSearchResult == null) {
      return null;
    } else {
      EventSearchResultEntity eventSearchResultEntity = RestEntityBuilder.fromSearchEvent(agendaCalendarService,
                                                                                          agendaEventService,
                                                                                          identityManager,
                                                                                          eventSearchResult,
                                                                                          userTimeZone);

      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
        fillAttendees(eventSearchResultEntity, 0);
      } else if (expandProperties.contains("response")) {
        fillAttendees(eventSearchResultEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("attachments")) {
        fillAttachments(eventSearchResultEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
        fillConferences(eventSearchResultEntity);
      }
      if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
        fillReminders(eventSearchResultEntity, userIdentityId);
      }
      if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
        fillDateOptions(eventSearchResultEntity, userTimeZone);
      }
      fillRemoteEvent(eventSearchResultEntity, userIdentityId);
      if (eventSearchResultEntity.getParent() != null) {
        fillRemoteEvent(eventSearchResultEntity.getParent(), userIdentityId);
      }
      if (isComputedOccurrence(eventSearchResultEntity)) {
        cleanupAttachedEntitiesIds(eventSearchResultEntity);
      }
      return eventSearchResultEntity;
    }
  }

  private void fillAttendees(EventEntity eventEntity,
                             Map<Long, List<EventAttendeeEntity>> attendeesByParentEventId,
                             long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    if (attendeesByParentEventId.containsKey(eventId)) {
      eventEntity.setAttendees(attendeesByParentEventId.get(eventId));
    } else {
      fillAttendees(eventEntity, userIdentityId);
      attendeesByParentEventId.put(eventId, eventEntity.getAttendees());
    }
  }

  private void fillAttendees(EventEntity eventEntity, long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    if (userIdentityId > 0) {
      List<EventAttendeeEntity> eventAttendeeEntities = eventAttendees == null ? null
                                                                               : eventAttendees.stream()
                                                                                               .filter(eventAttendee -> eventAttendee.getIdentityId() == userIdentityId)
                                                                                               .map(eventAttendee -> RestEntityBuilder.fromEventAttendee(identityManager,
                                                                                                                                                         eventAttendee))
                                                                                               .collect(Collectors.toList());
      eventEntity.setAttendees(eventAttendeeEntities);
    } else {
      List<EventAttendeeEntity> eventAttendeeEntities = eventAttendees == null ? null
                                                                               : eventAttendees.stream()
                                                                                               .map(eventAttendee -> RestEntityBuilder.fromEventAttendee(identityManager,
                                                                                                                                                         eventAttendee))
                                                                                               .collect(Collectors.toList());
      eventEntity.setAttendees(eventAttendeeEntities);
    }
  }

  private void fillAttachments(EventEntity eventEntity, Map<Long, List<EventAttachmentEntity>> attachmentsByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (attachmentsByParentEventId.containsKey(eventId)) {
      eventEntity.setAttachments(attachmentsByParentEventId.get(eventId));
    } else {
      fillAttachments(eventEntity);
      attachmentsByParentEventId.put(eventId, eventEntity.getAttachments());
    }
  }

  private void fillAttachments(EventEntity eventEntity) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventAttachment> eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    List<EventAttachmentEntity> eventAttachmentEntities = eventAttachments == null ? null
                                                                                   : eventAttachments.stream()
                                                                                                     .map(RestEntityBuilder::fromEventAttachment)
                                                                                                     .collect(Collectors.toList());
    eventEntity.setAttachments(eventAttachmentEntities);
  }

  private void fillConferences(EventEntity eventEntity, Map<Long, List<EventConference>> conferencesByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (conferencesByParentEventId.containsKey(eventId)) {
      eventEntity.setConferences(conferencesByParentEventId.get(eventId));
    } else {
      fillConferences(eventEntity);
      conferencesByParentEventId.put(eventId, eventEntity.getConferences());
    }
  }

  private void fillConferences(EventEntity eventEntity) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    eventEntity.setConferences(eventConferences);
  }

  private void fillReminders(EventEntity eventEntity,
                             long userIdentityId,
                             Map<Long, List<EventReminder>> remindersByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (remindersByParentEventId.containsKey(eventId)) {
      List<EventReminder> reminders = remindersByParentEventId.get(eventId);
      ZonedDateTime occurrenceId =
                                 AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getOccurrence().getId(), ZoneOffset.UTC);
      reminders = reminders.stream()
                           .filter(reminder -> (reminder.getFromOccurrenceId() == null
                               || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                               || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                               && (reminder.getUntilOccurrenceId() == null
                                   || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                           .collect(Collectors.toList());
      eventEntity.setReminders(reminders.stream().map(RestEntityBuilder::fromEventReminder).collect(Collectors.toList()));
    } else {
      fillReminders(eventEntity, userIdentityId);
      remindersByParentEventId.put(eventId,
                                   eventEntity.getReminders()
                                              .stream()
                                              .map(reminderEntity -> RestEntityBuilder.toEventReminder(eventId, reminderEntity))
                                              .collect(Collectors.toList()));
    }
  }

  private void fillDateOptions(EventEntity eventEntity,
                               ZoneId userTimeZone,
                               Map<Long, List<EventDateOptionEntity>> dateOptionsByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (dateOptionsByParentEventId.containsKey(eventId)) {
      eventEntity.setDateOptions(dateOptionsByParentEventId.get(eventId));
    } else {
      fillDateOptions(eventEntity, userTimeZone);
      dateOptionsByParentEventId.put(eventId, eventEntity.getDateOptions());
    }
  }

  private void fillRemoteEvent(EventEntity eventEntity,
                               long userIdentityId) {
    if (isComputedOccurrence(eventEntity)) {
      return;
    }

    long eventId = eventEntity.getId();
    RemoteEvent remoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId);
    if (remoteEvent != null) {
      eventEntity.setRemoteId(remoteEvent.getRemoteId());
      eventEntity.setRemoteProviderId(remoteEvent.getRemoteProviderId());
      eventEntity.setRemoteProviderName(remoteEvent.getRemoteProviderName());
    }
  }

  private void fillRemoteEvent(EventEntity eventEntity, long userIdentityId, Map<Long, RemoteEvent> remoteEventByParentEventId) {
    if (isComputedOccurrence(eventEntity)) {
      return;
    }

    long eventId = eventEntity.getId();
    RemoteEvent remoteEvent = null;
    if (remoteEventByParentEventId.containsKey(eventId)) {
      remoteEvent = remoteEventByParentEventId.get(eventId);
    } else {
      remoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId);
      remoteEventByParentEventId.put(eventId, remoteEvent);
    }
    if (remoteEvent != null) {
      eventEntity.setRemoteId(remoteEvent.getRemoteId());
      eventEntity.setRemoteProviderId(remoteEvent.getRemoteProviderId());
      eventEntity.setRemoteProviderName(remoteEvent.getRemoteProviderName());
    }
  }

  private RemoteEvent getRemoteEvent(EventEntity eventEntity, long userIdentityId) {
    return new RemoteEvent(0l,
                           eventEntity.getId(),
                           userIdentityId,
                           eventEntity.getRemoteId(),
                           eventEntity.getRemoteProviderId(),
                           eventEntity.getRemoteProviderName());

  }

  private void fillReminders(EventEntity eventEntity, long userIdentityId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    if (computedOccurrence) {
      ZonedDateTime occurrenceId =
                                 AgendaDateUtils.parseRFC3339ToZonedDateTime(eventEntity.getOccurrence().getId(), ZoneOffset.UTC);
      reminders = reminders.stream()
                           .filter(reminder -> (reminder.getFromOccurrenceId() == null
                               || reminder.getFromOccurrenceId().isEqual(occurrenceId)
                               || reminder.getFromOccurrenceId().isBefore(occurrenceId))
                               && (reminder.getUntilOccurrenceId() == null
                                   || reminder.getUntilOccurrenceId().isAfter(occurrenceId)))
                           .collect(Collectors.toList());
    }
    List<EventReminderEntity> eventReminderEntities = reminders == null ? null
                                                                        : reminders.stream()
                                                                                   .map(RestEntityBuilder::fromEventReminder)
                                                                                   .collect(Collectors.toList());
    eventEntity.setReminders(eventReminderEntities);
  }

  private void fillDateOptions(EventEntity eventEntity, ZoneId userTimeZone) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, userTimeZone);
    List<EventDateOptionEntity> dateOptionEntities = dateOptions == null ? Collections.emptyList()
                                                                         : dateOptions.stream()
                                                                                      .map(dateOption -> RestEntityBuilder.fromEventDateOption(userTimeZone,
                                                                                                                                               dateOption))
                                                                                      .collect(Collectors.toList());
    eventEntity.setDateOptions(dateOptionEntities);
  }

  private boolean isComputedOccurrence(EventEntity eventEntity) {
    return eventEntity.getId() == 0 && eventEntity.getParent() != null;
  }

  private void cleanupAttachedEntitiesIds(EventEntity eventEntity) {
    List<EventAttendeeEntity> attendees = eventEntity.getAttendees();
    if (attendees != null && !attendees.isEmpty()) {
      attendees = attendees.stream().map(attendee -> {
        attendee = attendee.clone();
        attendee.setId(0);
        return attendee;
      }).collect(Collectors.toList());
      eventEntity.setAttendees(attendees);
    }
    List<EventAttachmentEntity> attachments = eventEntity.getAttachments();
    if (attachments != null && !attachments.isEmpty()) {
      attachments = attachments.stream().map(attachment -> {
        attachment = attachment.clone();
        attachment.setId(0);
        return attachment;
      }).collect(Collectors.toList());
      eventEntity.setAttachments(attachments);
    }
    List<EventConference> conferences = eventEntity.getConferences();
    if (conferences != null && !conferences.isEmpty()) {
      conferences = conferences.stream().map(conference -> {
        conference = conference.clone();
        conference.setId(0);
        return conference;
      }).collect(Collectors.toList());
      eventEntity.setConferences(conferences);
    }
    List<EventReminderEntity> reminders = eventEntity.getReminders();
    if (reminders != null && !reminders.isEmpty()) {
      reminders = reminders.stream().map(reminder -> {
        reminder = reminder.clone();
        reminder.setId(0);
        return reminder;
      }).collect(Collectors.toList());
      eventEntity.setReminders(reminders);
    }
  }

}
