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

import static org.exoplatform.agenda.util.RestUtils.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.picocontainer.Startable;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import org.exoplatform.services.rest.http.PATCH;

@Path("/v1/agenda/events")
@Tag(name = "/v1/agenda/events", description = "Manages agenda events associated to users and spaces")
public class AgendaEventRest implements ResourceContainer, Startable {

  private static final String          AGENDA_APP_URI      = "agenda";

  private static final Log             LOG                 = ExoLogger.getLogger(AgendaEventRest.class);

  private IdentityManager              identityManager;

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventService           agendaEventService;

  private AgendaEventReminderService   agendaEventReminderService;

  private AgendaEventAttendeeService   agendaEventAttendeeService;

  private AgendaEventConferenceService agendaEventConferenceService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private AgendaEventDatePollService   agendaEventDatePollService;

  private ScheduledExecutorService     scheduledExecutor;

  private PortalContainer              container;

  private Map<Long, Long>              eventsToDeleteQueue = new HashMap<>();

  private String                       defaultSite         = null;

  public AgendaEventRest(IdentityManager identityManager,
                         UserPortalConfigService portalConfigService,
                         AgendaCalendarService agendaCalendarService,
                         AgendaEventService agendaEventService,
                         AgendaEventConferenceService agendaEventConferenceService,
                         AgendaRemoteEventService agendaRemoteEventService,
                         AgendaEventDatePollService agendaEventDatePollService,
                         AgendaEventReminderService agendaEventReminderService,
                         AgendaEventAttendeeService agendaEventAttendeeService,
                         PortalContainer container) {
    this.identityManager = identityManager;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.agendaEventReminderService = agendaEventReminderService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.agendaEventDatePollService = agendaEventDatePollService;
    this.container = container;
    if (portalConfigService != null && portalConfigService.getDefaultPortal() != null) {
      this.defaultSite = portalConfigService.getDefaultPortal();
    } else {
      this.defaultSite = "dw";
    }
  }

  @Override
  public void start() {
    scheduledExecutor = Executors.newScheduledThreadPool(1);
  }

  @Override
  public void stop() {
    if (scheduledExecutor != null) {
      scheduledExecutor.shutdown();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
          summary = "Retrieves the list of events available for an owner of type user or space",
          description = "Retrieves the list of events available for an owner of type user or space, identitifed by its identity technical identifier."
               + " If no designated owner, all events available for authenticated user will be retrieved.",
           method = "GET"
  )
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response getEvents(
                            @Parameter(description = "Identity technical identifiers of calendar owners")
                            @QueryParam(
                              "ownerIds"
                            )
                            List<Long> ownerIds,
                            @Parameter(
                                description = "Attendee identity identifier to filter on events where user is attendee",
                                required = true
                            )
                            @QueryParam("attendeeIdentityId")
                            long attendeeIdentityId,
                            @Parameter(description = "Properties to expand", required = false)
                            @QueryParam(
                              "expand"
                            )
                            String expand,
                            @Parameter(description = "Start datetime using RFC-3339 representation", required = true)
                            @QueryParam(
                              "start"
                            )
                            String start,
                            @Parameter(description = "End datetime using RFC-3339 representation", required = false)
                            @QueryParam(
                              "end"
                            )
                            String end,
                            @Parameter(description = "IANA Time zone identitifer", required = false)
                            @QueryParam(
                              "timeZoneId"
                            )
                            String timeZoneId,
                            @Parameter(
                                description = "Limit of results to return, used only when end date isn't set") @Schema(defaultValue = "10")
                            @QueryParam("limit")
                            int limit,
                            @Parameter(
                                description = "Attendee Response statuses to filter events by attendee response",
                                required = false
                            )
                            @QueryParam("responseTypes")
                            List<EventAttendeeResponse> responseTypes) {

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
      Map<Long, EventAttendeeList> attendeesByParentEventId = new HashMap<>();
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

        ZonedDateTime occurrenceId = event.getOccurrence() == null ? null : event.getOccurrence().getId();
        if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
          try {
            fillAttendees(identityManager,
                          agendaEventAttendeeService,
                          eventEntity,
                          occurrenceId,
                          attendeesByParentEventId,
                          0l);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        } else if (expandProperties.contains("response")) {
          try {
            fillAttendees(identityManager,
                          agendaEventAttendeeService,
                          eventEntity,
                          occurrenceId,
                          attendeesByParentEventId,
                          userIdentityId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        }
        List<EventAttendeeEntity> attendees = eventEntity.getAttendees();
        // Avoid to return an event with user response not in selected
        // responseTypes
        if (CollectionUtils.isNotEmpty(responseTypes) && CollectionUtils.isNotEmpty(attendees)) {
          for (EventAttendeeEntity attendee : attendees) {
            if (Long.parseLong(attendee.getIdentity().getId()) == userIdentityId
                && !responseTypes.contains(attendee.getResponse())) {
              return null;
            }
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("conferences")) {
          try {
            fillConferences(agendaEventConferenceService, eventEntity, conferencesByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event conferences, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("reminders")) {
          try {
            fillReminders(agendaEventReminderService, eventEntity, userIdentityId, remindersByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event reminders, retrieve event without it", e);
          }
        }
        if (expandProperties.contains("all") || expandProperties.contains("dateOptions")) {
          try {
            fillDateOptions(agendaEventDatePollService, eventEntity, userTimeZone, dateOptionsByParentEventId);
          } catch (Exception e) {
            LOG.warn("Error retrieving event date options, retrieve event without it", e);
          }
        }
        fillRemoteEvent(agendaRemoteEventService, eventEntity, userIdentityId);
        if (eventEntity.getParent() != null) {
          fillRemoteEvent(agendaRemoteEventService, eventEntity.getParent(), userIdentityId, remoteEventByParentEventId);
        }
        if (isComputedOccurrence(eventEntity)) {
          cleanupAttachedEntitiesIds(eventEntity);
        }
        return eventEntity;
      }).filter(eventEntity -> eventEntity != null).collect(Collectors.toList());

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
  @Operation(
      summary = "Retrieves an event identified by its technical identifier",
      description = "Retrieves an event identified by its technical identifier",
      method = "GET"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response getEventById(
                               @Parameter(description = "Event technical identifier", required = true)
                               @PathParam(
                                 "eventId"
                               )
                               long eventId,
                               @Parameter(description= "Properties to expand")
                               @QueryParam(
                                 "expand"
                               )
                               String expand,
                               @Parameter(description= "IANA Time zone identitifer")
                               @QueryParam(
                                 "timeZoneId"
                               )
                               String timeZoneId,
                               @Parameter(description= "Whether to retrieve first occurrence or not")
                               @QueryParam(
                                 "firstOccurrence"
                               )
                               boolean firstOccurrence) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
      EventEntity eventEntity = getEventByIdAndUser(identityManager,
                                                    agendaCalendarService,
                                                    agendaEventService,
                                                    agendaRemoteEventService,
                                                    agendaEventDatePollService,
                                                    agendaEventReminderService,
                                                    agendaEventConferenceService,
                                                    agendaEventAttendeeService,
                                                    eventId,
                                                    firstOccurrence,
                                                    RestUtils.getCurrentUserIdentityId(identityManager),
                                                    null,
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
  @Operation(
      summary = "Retrieves an event identified by its technical identifier",
      description = "Retrieves an event identified by its technical identifier",
      method = "GET"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response getEventOccurrence(
                                     @Parameter(description= "Event technical identifier", required = true)
                                     @PathParam(
                                       "parentEventId"
                                     )
                                     long parentEventId,
                                     @Parameter(description= "Event technical identifier", required = true)
                                     @PathParam(
                                       "occurrenceId"
                                     )
                                     String occurrenceId,
                                     @Parameter(description= "Properties to expand")
                                     @QueryParam(
                                       "expand"
                                     )
                                     String expand,
                                     @Parameter(description= "IANA Time zone identitifer")
                                     @QueryParam(
                                       "timeZoneId"
                                     )
                                     String timeZoneId) {
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
      ZonedDateTime occurrenceDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
      Event event = agendaEventService.getEventOccurrence(parentEventId, occurrenceDate, userTimeZone, identityId);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      EventEntity eventEntity = getEventEntity(identityManager,
                                               agendaCalendarService,
                                               agendaEventService,
                                               agendaRemoteEventService,
                                               agendaEventDatePollService,
                                               agendaEventReminderService,
                                               agendaEventConferenceService,
                                               agendaEventAttendeeService,
                                               event,
                                               occurrenceDate,
                                               userTimeZone,
                                               expandProperties);
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
  @Operation(
      summary = "Retrieves the list of exceptional occurrences of an event",
      description = "Retrieves the list of exceptional occurrences of an event",
      method = "GET"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response getEventExceptionalOccurrences(
                                                 @Parameter(description= "Event technical identifier", required = true)
                                                 @PathParam(
                                                   "eventId"
                                                 )
                                                 long parentEventId,
                                                 @Parameter(description= "Properties to expand")
                                                 @QueryParam(
                                                   "expand"
                                                 )
                                                 String expand,
                                                 @Parameter(description = "IANA Time zone identitifer")
                                                 @QueryParam(
                                                   "timeZoneId"
                                                 )
                                                 String timeZoneId) {
    List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                  ","));
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
      List<Event> events = agendaEventService.getExceptionalOccurrenceEvents(parentEventId, userTimeZone, userIdentityId);
      List<EventEntity> eventEntities = events.stream()
                                              .map(event -> getEventEntity(identityManager,
                                                                           agendaCalendarService,
                                                                           agendaEventService,
                                                                           agendaRemoteEventService,
                                                                           agendaEventDatePollService,
                                                                           agendaEventReminderService,
                                                                           agendaEventConferenceService,
                                                                           agendaEventAttendeeService,
                                                                           event,
                                                                           null,
                                                                           userTimeZone,
                                                                           expandProperties))
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
  @Operation(summary = "Create a new event", description = "Create a new event", method = "POST")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response createEvent(
                              @Parameter(description= "IANA Time zone identitifer")
                              @QueryParam(
                                "timeZoneId"
                              )
                              String timeZoneId,
                              @Parameter(description = "Event object to create", required = true)
                              EventEntity eventEntity) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getCalendar() == null || eventEntity.getCalendar().getOwner() == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND).build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      Event event = createEventEntity(identityManager,
                                      agendaCalendarService,
                                      agendaEventService,
                                      eventEntity,
                                      userIdentityId,
                                      timeZoneId);
      return getEventById(event.getId(), "all", timeZoneId == null ? event.getTimeZoneId().getId() : timeZoneId, false);
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
  @Operation(summary = "Update an existing event", description = "Update an existing event", method = "PUT")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response updateEvent(
                              @Parameter(description= "Event object to update", required = true)
                              EventEntity eventEntity,
                              @Parameter(description= "IANA Time zone identitifer")
                              @QueryParam(
                                "timeZoneId"
                              )
                              String timeZoneId) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getId() <= 0) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_ID_MANDATORY.getCompleteMessage()).build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      checkCalendar(identityManager,
                    agendaCalendarService,
                    eventEntity);

      List<EventAttendeeEntity> attendeeEntities = eventEntity.getAttendees();
      List<EventAttendee> attendees = null;
      if (attendeeEntities != null && !attendeeEntities.isEmpty()) {
        attendees = new ArrayList<>();
        for (EventAttendeeEntity attendeeEntity : attendeeEntities) {
          attendees.add(RestEntityBuilder.toEventAttendee(identityManager, eventEntity.getId(), attendeeEntity));
        }
      }

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
                                     reminders,
                                     dateOptions,
                                     remoteEvent,
                                     eventEntity.isSendInvitation(),
                                     userIdentityId);
      return getEventById(event.getId(), "all", userTimeZoneId, false);
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
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Update an attribute of an existing event", description = "Update an attribute of an existing event", method = "PATCH")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response updateEventFields(
                                    @Parameter(
                                        description = "Event technical identifier",
                                        required = true
                                    )
                                    @PathParam("eventId")
                                    long eventId,
                                    @Parameter(
                                        description = "Update all event occurrences or only parent occurrence")
                                    @Schema(defaultValue = "false")
                                    @QueryParam("updateAllOccurrences")
                                    boolean updateAllOccurrences,
                                    @Parameter(
                                        description = "Whether notify attendees about the modification or not")
                                    @Schema(defaultValue = "false")
                                    @QueryParam("sendInvitations")
                                    boolean sendInvitations,
                                    @Parameter(description= "IANA Time zone identitifer", required = false)
                                    @QueryParam(
                                      "timeZoneId"
                                    )
                                    String timeZoneId,
                                    @Parameter(
                                        description = "Event fields to patch",
                                        required = true
                                    )
                                    MultivaluedMap<String, String> eventFields) {
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

      ZoneId userTimeZone = timeZoneId == null ? null : ZoneId.of(timeZoneId);
      EventEntity eventEntity = getEventByIdAndUser(identityManager,
                                                    agendaCalendarService,
                                                    agendaEventService,
                                                    agendaRemoteEventService,
                                                    agendaEventDatePollService,
                                                    agendaEventReminderService,
                                                    agendaEventConferenceService,
                                                    agendaEventAttendeeService,
                                                    eventId,
                                                    false,
                                                    userIdentityId,
                                                    null,
                                                    userTimeZone,
                                                    Collections.singletonList("all"));
      return Response.ok(eventEntity).build();
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
  @Operation(
      summary = "Delete an existing event or delay deleting an existing event with a fixed period.",
      method = "DELETE")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response deleteEvent(
                              @Parameter(description= "Event technical identifier", required = true)
                              @PathParam(
                                "eventId"
                              )
                              long eventId,
                              @Parameter(description= "Time to effectively delete event", required = false)
                              @QueryParam(
                                "delay"
                              )
                              long delay,
                              @Parameter(description= "IANA Time zone identitifer", required = false)
                              @QueryParam(
                                "timeZoneId"
                              )
                              String timeZoneId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event technical identifier must be positive").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      ZoneId userTimeZone = timeZoneId == null ? null : ZoneId.of(timeZoneId);
      EventEntity eventEntity = getEventByIdAndUser(identityManager,
                                                    agendaCalendarService,
                                                    agendaEventService,
                                                    agendaRemoteEventService,
                                                    agendaEventDatePollService,
                                                    agendaEventReminderService,
                                                    agendaEventConferenceService,
                                                    agendaEventAttendeeService,
                                                    eventId,
                                                    false,
                                                    userIdentityId,
                                                    null,
                                                    userTimeZone,
                                                    Collections.singletonList("all"));
      if (eventEntity == null) {
        return Response.status(Status.NOT_FOUND).entity("Event not found").build();
      }
      if (delay > 0) {
        eventsToDeleteQueue.put(eventId, userIdentityId);
        scheduledExecutor.schedule(() -> {
          if (eventsToDeleteQueue.containsKey(eventId)) {
            ExoContainerContext.setCurrentContainer(container);
            RequestLifeCycle.begin(container);
            try {
              eventsToDeleteQueue.remove(eventId);
              agendaEventService.deleteEventById(eventId, userIdentityId);
            } catch (IllegalAccessException e) {
              LOG.error("User '{}' attempts to delete a non authorized event", userIdentityId, e);
            } catch (Exception e) {
              LOG.warn("Error deleting an event", e);
            } finally {
              RequestLifeCycle.end();
            }
          }
        }, delay, TimeUnit.SECONDS);
      } else {
        eventsToDeleteQueue.remove(eventId);
        agendaEventService.deleteEventById(eventId, userIdentityId);
      }
      return Response.ok(eventEntity).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to delete a non authorized event", RestUtils.getCurrentUser(), e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error deleting an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/undoDelete")
  @POST
  @RolesAllowed("users")
  @Operation(
      summary = "Undo deleting event if not yet effectively deleted",
      description = "Undo deleting event if not yet effectively deleted",
      method = "POST")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response undoDeleteEvent(
                                  @Parameter(description= "Event technical identifier", required = true)
                                  @PathParam(
                                    "eventId"
                                  )
                                  long eventId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }
    if (eventsToDeleteQueue.containsKey(eventId)) {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      Long originalModifierUserId = eventsToDeleteQueue.get(eventId);
      if (originalModifierUserId != userIdentityId) {
        LOG.warn("User {} attempts to cancel deletion of an event deleted by user {}",
                 userIdentityId,
                 originalModifierUserId);
        return Response.status(Status.FORBIDDEN).build();
      }
      eventsToDeleteQueue.remove(eventId);
      return Response.noContent().build();
    } else {
      return Response.status(Status.BAD_REQUEST).entity("Event id was already deleted or isn't planned to be deleted").build();
    }
  }

  @POST
  @Path("{eventId}/selectDateOption/{dateOptionId}")
  @RolesAllowed("users")
  @Operation(
      summary = "Select an Date Option for an event having multiple dates options",
      description = "Select an Date Option for an event having multiple dates options",
      method = "POST")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response selectEventDateOption(
                                        @Parameter(description= "Event technical identifier", required = true)
                                        @PathParam(
                                          "eventId"
                                        )
                                        long eventId,
                                        @Parameter(description= "Event date option technical identifier", required = true)
                                        @PathParam(
                                          "dateOptionId"
                                        )
                                        long dateOptionId) {
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
  @Operation(
      summary = "Retrieve preferred reminders for currently authenticated user for an event",
      description = "Retrieve preferred reminders for currently authenticated user for an event identified by its technical identifier.",
      method = "GET")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response getEventRemindersById(
                                        @Parameter(description= "Event technical identifier", required = true)
                                        @PathParam(
                                          "eventId"
                                        )
                                        long eventId) {
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
  @Operation(
      summary = "Update the list of preferred reminders for authenticated user on a selected event.",
      description = "Update the list of preferred reminders for authenticated user on a selected event",
      method = "PUT"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response saveEventReminders(
                                     @Parameter(description= "Event technical identifier", required = true)
                                     @PathParam(
                                       "eventId"
                                     )
                                     long eventId,
                                     @Parameter(description= "Event occurrence identifier", required = true)
                                     @QueryParam(
                                       "occurrenceId"
                                     )
                                     String occurrenceId,
                                     @Parameter(
                                         description = "Whether apply on Event occurrence and its upcoming or not",
                                         required = true
                                     )
                                     @QueryParam(
                                       "upcoming"
                                     )
                                     boolean upcoming,
                                     @Parameter(
                                         description = "List of reminders to store on event for currently authenticated user",
                                         required = true
                                     )
                                     List<EventReminder> reminders) {
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
  @Operation(
      summary = "Retrieves currently authenticated user response to an event",
      description = "Retrieves currently authenticated (using token or effectively authenticated) user response to an event.",
      method = "GET"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response getEventResponse(
                                   @Parameter(description= "Event technical identifier", required = true)
                                   @PathParam(
                                     "eventId"
                                   )
                                   long eventId,
                                   @Parameter(
                                       description = "Event occurrence identifier"
                                   )
                                   @QueryParam(
                                     "occurrenceId"
                                   )
                                   String occurrenceId,
                                   @Parameter(description= "User token to ", required = false)
                                   @QueryParam("token")
                                   String token) {
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
      ZonedDateTime occurrenceIdDateTime = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
      EventAttendeeResponse response = agendaEventAttendeeService.getEventResponse(eventId, occurrenceIdDateTime, identityId);
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
  @Operation(
          summary = "Registers voted date poll options for currently authenticated user",
          description = "Registers voted date poll options for currently authenticated user",
          method = "POST")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response voteEvent(
                            @Parameter(description= "Event technical identifier", required = true)
                            @PathParam(
                              "eventId"
                            )
                            long eventId,
                            @Parameter(description= "Accepted event date options technical identifier")
                            @FormParam(
                              "dateOptionId"
                            )
                            List<Long> dateOptionId) {
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
  @Operation(
      summary = "Registers voted date poll option for currently authenticated user",
      description = "Registers voted date poll option for currently authenticated user",
      method = "POST")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response voteEventDateOption(
                                      @Parameter(description= "Event technical identifier", required = true)
                                      @PathParam(
                                        "eventId"
                                      )
                                      long eventId,
                                      @Parameter(description= "Event date option technical identifier", required = true)
                                      @PathParam(
                                        "dateOptionId"
                                      )
                                      long dateOptionId) {
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
  @Operation(
      summary = "Dismisses vote on date poll option for currently authenticated user",
      description = "Dismisses vote on date poll option for currently authenticated user",
      method = "DELETE")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response dismissEventDateOption(
                                         @Parameter(description= "Event technical identifier", required = true)
                                         @PathParam(
                                           "eventId"
                                         )
                                         long eventId,
                                         @Parameter(description= "Event date option technical identifier", required = true)
                                         @PathParam(
                                           "dateOptionId"
                                         )
                                         long dateOptionId) {
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
  // This query modify data, but to accept events, so the risk of attack via this bias is low.
  // a hacker will not delete or modify important data via this request.
  @Operation(
      summary = "Send event invitation response for currently authenticated user",
      description = "Send event invitation response for currently authenticated user (using token or effectively authenticated)",
      method = "GET")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response sendEventResponse(
                                    @Parameter(description= "Event technical identifier", required = true)
                                    @PathParam(
                                      "eventId"
                                    )
                                    long eventId,
                                    @Parameter(
                                        description = "Event occurrence identifier"
                                    )
                                    @QueryParam(
                                      "occurrenceId"
                                    )
                                    String occurrenceId,
                                    @Parameter(
                                        description = "Response to event invitation. Possible values: ACCEPTED, DECLINED or TENTATIVE.",
                                        required = true
                                    )
                                    @QueryParam("response")
                                    String responseString,
                                    @Parameter(
                                        description = "Whether apply response on upcoming event of a recurrent event or not"
                                    )
                                    @QueryParam("upcoming")
                                    boolean upcoming,
                                    @Parameter(
                                        description = "User token used to identify user and his response to apply new reponse even when user is not authenticated"
                                    )
                                    @QueryParam(
                                      "token"
                                    )
                                    String token,
                                    @Parameter(
                                        description = "Whether redirect to Event details after applying new response or not")
                                    @Schema(defaultValue = "false")
                                    @QueryParam(
                                      "redirect"
                                    )
                                    boolean redirect) {
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
    if (upcoming && StringUtils.isBlank(occurrenceId)) {
      return Response.status(Status.BAD_REQUEST).entity("Event occurrenceId is mandatory when having upcoming = true").build();
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

      if (StringUtils.isBlank(occurrenceId)) {
        agendaEventAttendeeService.sendEventResponse(eventId, identityId, response);
      } else {
        ZonedDateTime occurrenceIdDateTime = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
        if (upcoming) {
          agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                               occurrenceIdDateTime,
                                                               identityId,
                                                               response);
        } else {
          Event occurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceIdDateTime);
          if (occurrenceEvent == null) { // Exceptional occurrence not yet
                                         // created
            occurrenceEvent = agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceIdDateTime);
          }
          agendaEventAttendeeService.sendEventResponse(occurrenceEvent.getId(), identityId, response);
        }
      }
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
  @Operation(
          summary = "Search the list of events available with query for an owner of type user or space",
          description = "Search the list of events available with query for an owner of type user or space, identified by its identity technical identifier."
             + " If no designated owner, all events available for authenticated user will be retrieved.",
          method = "GET")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response search(
                         @Parameter(description= "Term to search", required = true)
                         @QueryParam(
                           "query"
                         )
                         String query,
                         @Parameter(description= "IANA Time zone identitifer")
                         @QueryParam(
                           "timeZoneId"
                         )
                         String timeZoneId,
                         @Parameter(description= "Properties to expand")
                         @QueryParam(
                           "expand"
                         )
                         String expand,
                         @Parameter(description= "Offset") @Schema(defaultValue = "0")
                         @QueryParam(
                           "offset"
                         )
                         int offset,
                         @Parameter(description= "Limit") @Schema(defaultValue = "20")
                         @QueryParam(
                           "limit"
                         )
                         int limit) throws Exception { // NOSONAR
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
                                                         .map(searchResult -> getEventSearchResultEntity(identityManager,
                                                                                                         agendaCalendarService,
                                                                                                         agendaEventService,
                                                                                                         agendaRemoteEventService,
                                                                                                         agendaEventDatePollService,
                                                                                                         agendaEventReminderService,
                                                                                                         agendaEventConferenceService,
                                                                                                         agendaEventAttendeeService,
                                                                                                         searchResult,
                                                                                                         null,
                                                                                                         userTimeZone,
                                                                                                         expandProperties))
                                                         .collect(Collectors.toList());
    return Response.ok(results).build();
  }

  @Path("datePolls")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Retrieves the list of pending date polls for currently authenticated user",
      description = "Retrieves the list of pending date polls for currently authenticated user.",
      method = "GET")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response getDatePolls(
                               @Parameter(description= "Identity technical identifiers of calendar owners")
                               @QueryParam(
                                 "ownerIds"
                               )
                               List<Long> ownerIds,
                               @Parameter(description= "Offset") @Schema(defaultValue = "0")
                               @QueryParam(
                                 "offset"
                               )
                               int offset,
                               @Parameter(
                                   description = "Limit of results to return"
                               )
                               @QueryParam("limit")
                               int limit,
                               @Parameter(description= "Start datetime using RFC-3339 representation", required = true)
                               @QueryParam(
                                 "start"
                               )
                               String start,
                               @Parameter(description= "End datetime using RFC-3339 representation")
                               @QueryParam(
                                 "end"
                               )
                               String end,
                               @Parameter(description= "IANA Time zone identitifer")
                               @QueryParam(
                                 "timeZoneId"
                               )
                               String timeZoneId,
                               @Parameter(description= "Properties to expand")
                               @QueryParam(
                                 "expand"
                               )
                               String expand) {
    if (offset < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Offset must be 0 or positive").build();
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

    ZonedDateTime startDatetime = null;
    if (StringUtils.isBlank(start)) {
      if (limit <= 0) {
        limit = 10;
      }
    } else {
      startDatetime = AgendaDateUtils.parseRFC3339ToZonedDateTime(start, userTimeZone);
    }

    if (endDatetime != null && startDatetime != null
        && (endDatetime.isBefore(startDatetime) || endDatetime.equals(startDatetime))) {
      return Response.status(Status.BAD_REQUEST).entity("Start date must be before end date").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      EventList eventList = new EventList();
      eventList.setLimit(limit);
      EventFilter eventFilter = startDatetime != null && endDatetime != null ? new EventFilter(ownerIds,
                                                                                               startDatetime,
                                                                                               endDatetime)
                                                                             : new EventFilter(ownerIds,
                                                                                               offset,
                                                                                               limit);

      List<Event> events = agendaEventService.getEventDatePolls(eventFilter, userTimeZone, userIdentityId);
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      List<EventEntity> eventEntities = events.stream()
                                              .map(event -> getEventEntity(identityManager,
                                                                           agendaCalendarService,
                                                                           agendaEventService,
                                                                           agendaRemoteEventService,
                                                                           agendaEventDatePollService,
                                                                           agendaEventReminderService,
                                                                           agendaEventConferenceService,
                                                                           agendaEventAttendeeService,
                                                                           event,
                                                                           null,
                                                                           userTimeZone,
                                                                           expandProperties))
                                              .collect(Collectors.toList());
      eventList.setEvents(eventEntities);
      eventList.setSize(agendaEventService.countEventDatePolls(ownerIds, userIdentityId));
      return Response.ok(eventList).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of events", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("pending")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Retrieves the list of pending events for currently authenticated user",
      description = "Retrieves the list of pending events for currently authenticated user.",
      method = "GET"
  )
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response getPendingEvents(
                                   @Parameter(description= "Identity technical identifiers of calendar owners")
                                   @QueryParam(
                                     "ownerIds"
                                   )
                                   List<Long> ownerIds,
                                   @Parameter(description= "Offset") @Schema(defaultValue = "0")
                                   @QueryParam(
                                     "offset"
                                   )
                                   int offset,
                                   @Parameter(
                                       description = "Limit of results to return"
                                   )
                                   @QueryParam("limit")
                                   int limit,
                                   @Parameter(description= "IANA Time zone identitifer")
                                   @QueryParam(
                                     "timeZoneId"
                                   )
                                   String timeZoneId,
                                   @Parameter(description= "Properties to expand")
                                   @QueryParam(
                                     "expand"
                                   )
                                   String expand) {
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
        List<Event> events = agendaEventService.getPendingEvents(ownerIds,
                                                                 userIdentityId,
                                                                 userTimeZone,
                                                                 offset,
                                                                 limit);
        List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                    : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                      ","));
        List<EventEntity> eventEntities = events.stream()
                                                .map(event -> getEventEntity(identityManager,
                                                                             agendaCalendarService,
                                                                             agendaEventService,
                                                                             agendaRemoteEventService,
                                                                             agendaEventDatePollService,
                                                                             agendaEventReminderService,
                                                                             agendaEventConferenceService,
                                                                             agendaEventAttendeeService,
                                                                             event,
                                                                             null,
                                                                             userTimeZone,
                                                                             expandProperties))
                                                .collect(Collectors.toList());
        eventList.setEvents(eventEntities);
      }
      eventList.setSize(agendaEventService.countPendingEvents(ownerIds, userIdentityId));
      return Response.ok(eventList).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of pending events", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}
