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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

import io.swagger.annotations.*;

@Path("/v1/agenda/events")
@Api(value = "/v1/agenda/events", description = "Manages agenda events associated to users and spaces") // NOSONAR
public class AgendaEventRest implements ResourceContainer {

  private static final Log             LOG = ExoLogger.getLogger(AgendaEventRest.class);

  private IdentityManager              identityManager;

  private AgendaCalendarService        agendaCalendarService;

  private AgendaEventService           agendaEventService;

  private AgendaEventReminderService   agendaEventReminderService;

  private AgendaEventAttendeeService   agendaEventAttendeeService;

  private AgendaEventAttachmentService agendaEventAttachmentService;

  private AgendaEventConferenceService agendaEventConferenceService;

  public AgendaEventRest(IdentityManager identityManager,
                         AgendaCalendarService agendaCalendarService,
                         AgendaEventService agendaEventService,
                         AgendaEventConferenceService agendaEventConferenceService,
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
      value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response list(
                       @ApiParam(value = "Identity technical identifiers of calendar owners", required = false) @QueryParam(
                         "ownerIds"
                       ) List<Long> ownerIds,
                       @ApiParam(
                           value = "Attendee identity identifier to filter on events where user is attendee", required = true
                       ) @QueryParam("attendeeIdentityId") long attendeeIdentityId,
                       @ApiParam(value = "Properties to expand", required = true) @QueryParam(
                         "expand"
                       ) String expand,
                       @ApiParam(value = "Start datetime using RFC-3339 representation including timezone", required = true) @QueryParam("start") String start,
                       @ApiParam(value = "End datetime using RFC-3339 representation including timezone", required = true) @QueryParam("end") String end) {
    if (StringUtils.isBlank(start)) {
      return Response.status(Status.BAD_REQUEST).entity("Start datetime is mandatory").build();
    }
    if (StringUtils.isBlank(end)) {
      return Response.status(Status.BAD_REQUEST).entity("End datetime is mandatory").build();
    }

    ZonedDateTime startDatetime = AgendaDateUtils.parseRFC3339ToZonedDateTime(start);
    ZonedDateTime endDatetime = AgendaDateUtils.parseRFC3339ToZonedDateTime(end);
    if (endDatetime.isBefore(startDatetime) || endDatetime.equals(startDatetime)) {
      return Response.status(Status.BAD_REQUEST).entity("Start date must be before end date").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<Event> events = null;
      if (attendeeIdentityId > 0) {
        if (ownerIds == null || ownerIds.isEmpty()) {
          events = agendaEventService.getEventsByAttendee(attendeeIdentityId, startDatetime, endDatetime, currentUser);
        } else {
          events = agendaEventService.getEventsByOwnersAndAttendee(attendeeIdentityId,
                                                                   ownerIds,
                                                                   startDatetime,
                                                                   endDatetime,
                                                                   currentUser);
        }
      } else {
        if (ownerIds == null || ownerIds.isEmpty()) {
          events = agendaEventService.getEvents(startDatetime, endDatetime, currentUser);
        } else {
          events = agendaEventService.getEventsByOwners(ownerIds, startDatetime, endDatetime, currentUser);
        }
      }
      Map<Long, List<EventAttendeeEntity>> attendeesByParentEventId = new HashMap<>();
      Map<Long, List<EventAttachmentEntity>> attachmentsByParentEventId = new HashMap<>();
      Map<Long, List<EventConference>> conferencesByParentEventId = new HashMap<>();
      Map<Long, List<EventReminderEntity>> remindersByParentEventId = new HashMap<>();
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      List<EventEntity> eventEntities = events.stream().map(event -> {
        EventEntity eventEntity = EntityBuilder.fromEvent(agendaCalendarService, agendaEventService, identityManager, event);
        long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);

        if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
          try {
            fillAttendees(eventEntity, attendeesByParentEventId);
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
        if (isComputedOccurrence(eventEntity)) {
          cleanupAttachedEntitiesIds(eventEntity);
        }
        return eventEntity;
      }).collect(Collectors.toList());

      EventList eventList = new EventList();
      eventList.setEvents(eventEntities);
      eventList.setStart(start);
      eventList.setEnd(end);
      return Response.ok(eventList).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized events of owner Id '{}'", currentUser, ownerIds);
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
                               @ApiParam(value = "Properties to expand", required = true) @QueryParam(
                                 "expand"
                               ) String expand) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                  : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                    ","));
      EventEntity eventEntity =
                              getEventByIAndUser(eventId, RestUtils.getCurrentUserIdentityId(identityManager), expandProperties);
      if (eventEntity == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        return Response.ok(eventEntity).build();
      }
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized event with Id '{}'", currentUser, eventId);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Create a new event", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response createEvent(@ApiParam(value = "Event object to create", required = true) EventEntity eventEntity) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getCalendar() == null || eventEntity.getCalendar().getOwner() == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND).build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      createEvent(eventEntity, currentUser);
      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create an event in calendar '{}'", currentUser, eventEntity.getCalendar());
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (AgendaException e) {
      return Response.serverError().entity(e.getAgendaExceptionType().getCompleteMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
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
  public Response updateEvent(@ApiParam(value = "Event object to update", required = true) EventEntity eventEntity) {
    if (eventEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_MANDATORY.getCompleteMessage()).build();
    }
    if (eventEntity.getId() <= 0) {
      return Response.status(Status.BAD_REQUEST).entity(AgendaExceptionType.EVENT_ID_MANDATORY.getCompleteMessage()).build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      checkCalendar(eventEntity);

      List<EventAttendeeEntity> attendeeEntities = eventEntity.getAttendees();
      List<EventAttendee> attendees = null;
      if (attendeeEntities != null && !attendeeEntities.isEmpty()) {
        attendees = new ArrayList<>();
        for (EventAttendeeEntity attendeeEntity : attendeeEntities) {
          attendees.add(EntityBuilder.toEventAttendee(identityManager, attendeeEntity));
        }
      }

      List<EventAttachmentEntity> attachmentEntities = eventEntity.getAttachments();
      List<EventAttachment> attachments = attachmentEntities == null
          || attachmentEntities.isEmpty() ? null
                                          : attachmentEntities.stream()
                                                              .map(EntityBuilder::toEventAttachment)
                                                              .collect(Collectors.toList());

      Event event = EntityBuilder.toEvent(eventEntity);
      List<EventReminder> reminders = eventEntity.getReminders() == null ? null
                                                                         : eventEntity.getReminders()
                                                                                      .stream()
                                                                                      .map(EntityBuilder::toEventReminder)
                                                                                      .collect(Collectors.toList());
      agendaEventService.updateEvent(event,
                                     attendees,
                                     eventEntity.getConferences(),
                                     attachments,
                                     reminders,
                                     eventEntity.isSendInvitation(),
                                     currentUser);
      return Response.noContent().build();
    } catch (AgendaException e) {
      return Response.serverError().entity(e.getAgendaExceptionType().getCompleteMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating an event", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Path("{eventId}")
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
  ) long eventId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event technical identifier must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaEventService.deleteEventById(eventId, currentUser);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Event not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to delete a non authorized event", currentUser);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error deleting an event", e);
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
  public Response updateEventReminders(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId,
                                       @ApiParam(
                                           value = "List of reminders to store on event for currently authenticated user",
                                           required = true
                                       ) List<EventReminder> reminders) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    long currentUserIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    String currentUser = RestUtils.getCurrentUser();
    try {
      Event event = agendaEventService.getEventById(eventId, currentUser);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).entity("Event with id " + eventId + " is not found").build();
      }
      agendaEventReminderService.saveEventReminders(event, reminders, currentUserIdentityId);
      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access reminders for a not authorized event with Id '{}'", currentUserIdentityId, eventId);
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

    String currentUser = RestUtils.getCurrentUser();
    try {
      Identity identity = null;
      if (StringUtils.isNotBlank(token)) {
        identity = agendaEventAttendeeService.decryptUserIdentity(eventId, token, null);
      } else if (StringUtils.isNotBlank(currentUser)) {
        identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
      }
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      long identityId = Long.parseLong(identity.getId());
      EventAttendeeResponse response = agendaEventAttendeeService.getEventResponse(eventId, identityId);
      return Response.ok(response.getValue()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Event not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access invitation response for a not authorized event with Id '{}'", currentUser, eventId);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event reminders with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{eventId}/response/send")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Send event invitation response for currently authenticated user (using token or effectively authenticated).",
      httpMethod = "GET",
      produces = "application/json",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
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
                                    @ApiParam(value = "User token to ", required = false) @QueryParam(
                                      "token"
                                    ) String token) {
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
        occurrenceIdDateTime = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId);
        Event occurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceIdDateTime);
        if (occurrenceEvent == null) { // Exceptional occurrence not yet created
          EventEntity eventEntity = getEventByIAndUser(eventId, identityId, Collections.singletonList("all"));
          if (eventEntity == null) {
            throw new ObjectNotFoundException("Parent recurrent event with id " + eventId + " is not found");
          }
          if (eventEntity.getRecurrence() == null) {
            throw new IllegalStateException("Event with id " + eventId + " is not a recurrent event");
          }
          occurrenceEvent = createEventOccurrence(eventEntity, occurrenceIdDateTime);
        }
        eventId = occurrenceEvent.getId();
      }
      agendaEventAttendeeService.sendEventResponse(eventId, identityId, response);
      return getEventById(eventId, "attendees");
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Event not found").build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to send invitation response for a not authorized event with Id '{}'", currentUser, eventId);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event reminders with id '{}'", eventId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  private Event createEvent(EventEntity eventEntity, String currentUser) throws AgendaException, IllegalAccessException {
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
        attendees.add(EntityBuilder.toEventAttendee(identityManager, attendeeEntity));
      }
    }

    List<EventAttachmentEntity> attachmentEntities = eventEntity.getAttachments();
    List<EventAttachment> attachments = attachmentEntities == null
        || attachmentEntities.isEmpty() ? null
                                        : attachmentEntities.stream()
                                                            .map(EntityBuilder::toEventAttachment)
                                                            .collect(Collectors.toList());
    List<EventReminderEntity> reminderEntities = eventEntity.getReminders();
    List<EventReminder> reminders = reminderEntities == null
        || reminderEntities.isEmpty() ? null
                                      : reminderEntities.stream()
                                                        .map(EntityBuilder::toEventReminder)
                                                        .collect(Collectors.toList());

    return agendaEventService.createEvent(EntityBuilder.toEvent(eventEntity),
                                          attendees,
                                          eventEntity.getConferences(),
                                          attachments,
                                          reminders,
                                          eventEntity.isSendInvitation(),
                                          currentUser);
  }

  private Event createEventOccurrence(EventEntity eventEntity, ZonedDateTime occurrenceId) throws AgendaException,
                                                                                           IllegalAccessException,
                                                                                           ObjectNotFoundException {
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
        attendees.add(EntityBuilder.toEventAttendee(identityManager, attendeeEntity));
      }
    }

    List<EventAttachmentEntity> attachmentEntities = eventEntity.getAttachments();
    List<EventAttachment> attachments = attachmentEntities == null
        || attachmentEntities.isEmpty() ? null
                                        : attachmentEntities.stream()
                                                            .map(EntityBuilder::toEventAttachment)
                                                            .collect(Collectors.toList());
    List<EventReminderEntity> reminderEntities = eventEntity.getReminders();
    List<EventReminder> reminders = reminderEntities == null
        || reminderEntities.isEmpty() ? null
                                      : reminderEntities.stream()
                                                        .map(EntityBuilder::toEventReminder)
                                                        .collect(Collectors.toList());

    return agendaEventService.createEventExceptionalOccurrence(eventEntity.getId(),
                                                               attendees,
                                                               eventEntity.getConferences(),
                                                               attachments,
                                                               reminders,
                                                               occurrenceId);
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
      eventEntity.setCalendar(EntityBuilder.fromCalendar(identityManager, calendar));
    } else {
      eventEntity.getCalendar().setId(calendar.getId());
    }
  }

  private EventEntity getEventByIAndUser(long eventId,
                                         long identityId,
                                         List<String> expandProperties) throws IllegalAccessException {
    Event event = agendaEventService.getEventById(eventId, identityId);
    if (event == null) {
      return null;
    } else {
      EventEntity eventEntity = EntityBuilder.fromEvent(agendaCalendarService, agendaEventService, identityManager, event);
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (expandProperties.contains("all") || expandProperties.contains("attendees")) {
        fillAttendees(eventEntity);
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
      if (isComputedOccurrence(eventEntity)) {
        cleanupAttachedEntitiesIds(eventEntity);
      }
      return eventEntity;
    }
  }

  private void fillAttendees(EventEntity eventEntity, Map<Long, List<EventAttendeeEntity>> attendeesByParentEventId) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    if (attendeesByParentEventId.containsKey(eventId)) {
      eventEntity.setAttendees(attendeesByParentEventId.get(eventId));
    } else {
      fillAttendees(eventEntity);
      attendeesByParentEventId.put(eventId, eventEntity.getAttendees());
    }
  }

  private void fillAttendees(EventEntity eventEntity) {
    boolean computedOccurrence = isComputedOccurrence(eventEntity);
    long eventId = computedOccurrence ? eventEntity.getParent().getId()
                                      : eventEntity.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendees == null ? null
                                                                             : eventAttendees.stream()
                                                                                             .map(eventAttendee -> EntityBuilder.fromEventAttendee(identityManager,
                                                                                                                                                   eventAttendee))
                                                                                             .collect(Collectors.toList());
    eventEntity.setAttendees(eventAttendeeEntities);
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
                                                                                                     .map(EntityBuilder::fromEventAttachment)
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
                             Map<Long, List<EventReminderEntity>> remindersByParentEventId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    if (remindersByParentEventId.containsKey(eventId)) {
      eventEntity.setReminders(remindersByParentEventId.get(eventId));
    } else {
      fillReminders(eventEntity, userIdentityId);
      remindersByParentEventId.put(eventId, eventEntity.getReminders());
    }
  }

  private void fillReminders(EventEntity eventEntity, long userIdentityId) {
    long eventId = isComputedOccurrence(eventEntity) ? eventEntity.getParent().getId()
                                                     : eventEntity.getId();
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    List<EventReminderEntity> eventReminderEntities = eventReminders == null ? null
                                                                             : eventReminders.stream()
                                                                                             .map(EntityBuilder::fromEventReminder)
                                                                                             .collect(Collectors.toList());
    eventEntity.setReminders(eventReminderEntities);
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
