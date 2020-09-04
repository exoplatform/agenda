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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.exoplatform.agenda.rest.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.*;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileService;
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

  private FileService                  fileService;

  private AgendaEventReminderService   agendaEventReminderService;

  private AgendaEventInvitationService agendaEventInvitationService;

  public AgendaEventRest(IdentityManager identityManager,
                         FileService fileService,
                         AgendaCalendarService agendaCalendarService,
                         AgendaEventService agendaEventService,
                         AgendaEventReminderService agendaEventReminderService,
                         AgendaEventInvitationService agendaEventInvitationService) {
    this.identityManager = identityManager;
    this.fileService = fileService;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.agendaEventReminderService = agendaEventReminderService;
    this.agendaEventInvitationService = agendaEventInvitationService;
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
  public Response list(@ApiParam(value = "Identity technical identifier", required = false) @PathParam("ownerId") long ownerId,
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
      if (ownerId <= 0) {
        events = agendaEventService.getEvents(startDatetime, endDatetime, currentUser);
      } else {
        events = agendaEventService.getEventsByOwner(ownerId, startDatetime, endDatetime, currentUser);
      }
      List<EventEntity> eventEntities = events.stream().map(event -> {
        EventEntity eventEntity = EntityBuilder.fromEvent(agendaCalendarService, agendaEventService, identityManager, event);
        long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);

        try {
          fillAttendees(eventEntity);
        } catch (Exception e) {
          LOG.warn("Error retrieving event reminders, retrieve event without reminders", e);
        }
        try {
          fillAttachments(eventEntity);
        } catch (Exception e) {
          LOG.warn("Error retrieving event reminders, retrieve event without reminders", e);
        }
        try {
          fillConferences(eventEntity);
        } catch (Exception e) {
          LOG.warn("Error retrieving event reminders, retrieve event without reminders", e);
        }
        try {
          fillReminders(eventEntity, userIdentityId);
        } catch (Exception e) {
          LOG.warn("Error retrieving event reminders, retrieve event without reminders", e);
        }
        return eventEntity;
      }).collect(Collectors.toList());

      EventList eventList = new EventList();
      eventList.setEvents(eventEntities);
      eventList.setStart(start);
      eventList.setEnd(end);
      return Response.ok(eventList).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized events of owner Id '{}'", currentUser, ownerId);
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
  public Response getEventById(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId) {
    if (eventId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Event identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      Event event = agendaEventService.getEventById(eventId, currentUser);
      if (event == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        EventEntity eventEntity = EntityBuilder.fromEvent(agendaCalendarService, agendaEventService, identityManager, event);
        long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
        fillAttendees(eventEntity);
        fillAttachments(eventEntity);
        fillConferences(eventEntity);
        fillReminders(eventEntity, userIdentityId);
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
      checkCalendar(eventEntity);

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

      agendaEventService.createEvent(EntityBuilder.toEvent(eventEntity),
                                     attendees,
                                     eventEntity.getConferences(),
                                     attachments,
                                     reminders,
                                     eventEntity.isSendInvitation(),
                                     currentUser);
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
    try {
      agendaEventReminderService.saveEventReminders(eventId, reminders, currentUserIdentityId);
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
        identity = agendaEventInvitationService.readUserIdentity(token, eventId, null);
      } else if (StringUtils.isNotBlank(currentUser)) {
        identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
      }
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      EventAttendeeResponse response = agendaEventInvitationService.getEventResponse(identity.getId(), eventId);
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
  @ApiOperation(
      value = "Send event invitation response for currently authenticated user (using token or effectively authenticated).",
      httpMethod = "GET", response = Response.class
  )
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response sendEventResponse(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "eventId"
  ) long eventId,
                                    @ApiParam(value = "User token to ", required = false) @QueryParam(
                                      "token"
                                    ) String token,
                                    @ApiParam(
                                        value = "Response to event invitation. Possible values: ACCEPTED, DECLINED or TENTATIVE.",
                                        required = true
                                    ) @QueryParam("response") String responseString) {
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
        identity = agendaEventInvitationService.readUserIdentity(token, eventId, response);
      } else if (StringUtils.isNotBlank(currentUser)) {
        identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
      }
      if (identity == null) {
        return Response.status(Status.FORBIDDEN).build();
      }
      agendaEventInvitationService.sendEventResponse(identity.getId(), eventId, response);
      return Response.noContent().build();
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

  @Path("attachment/{attachmentId}")
  @GET
  @ApiOperation(value = "Download Event attachment", httpMethod = "GET", response = Response.class)
  @ApiResponses(
      value = { @ApiResponse(code = HTTPStatus.FOUND, message = "Temporary Redirect"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), }
  )
  public Response downloadAttachment(@ApiParam(value = "Event technical identifier", required = true) @PathParam(
    "attachmentId"
  ) long attachmentId) {
    String currentUser = RestUtils.getCurrentUser();
    try {
      String downloadLink = agendaEventService.getEventAttachmentDownloadLink(attachmentId, currentUser);
      if (StringUtils.isBlank(downloadLink)) {
        return Response.status(Status.NOT_FOUND).build();
      }
      return Response.temporaryRedirect(new URI(downloadLink)).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized event attachment with Id '{}'", attachmentId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event attachment with Id '{}'", attachmentId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
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

  private void fillAttendees(EventEntity eventEntity) {
    List<EventAttendee> eventAttendees = agendaEventService.getEventAttendees(eventEntity.getId());
    List<EventAttendeeEntity> eventAttendeeEntities =
                                                    eventAttendees.stream()
                                                                  .map(eventAttendee -> EntityBuilder.fromEventAttendee(identityManager,
                                                                                                                        eventAttendee))
                                                                  .collect(Collectors.toList());
    eventEntity.setAttendees(eventAttendeeEntities);
  }

  private void fillAttachments(EventEntity eventEntity) {
    List<EventAttachment> eventAttachments = agendaEventService.getEventAttachments(eventEntity.getId());
    List<EventAttachmentEntity> eventAttachmentEntities =
                                                        eventAttachments.stream()
                                                                        .map(eventAttachment -> EntityBuilder.fromEventAttachment(fileService,
                                                                                                                                  eventAttachment))
                                                                        .collect(Collectors.toList());
    eventEntity.setAttachments(eventAttachmentEntities);
  }

  private void fillConferences(EventEntity eventEntity) {
    List<EventConference> eventConferences = agendaEventService.getEventConferences(eventEntity.getId());
    eventEntity.setConferences(eventConferences);
  }

  private void fillReminders(EventEntity eventEntity, long currentUserIdentityId) {
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventEntity.getId(), currentUserIdentityId);
    List<EventReminderEntity> eventReminderEntities = eventReminders.stream()
                                                                    .map(EntityBuilder::fromEventReminder)
                                                                    .collect(Collectors.toList());
    eventEntity.setReminders(eventReminderEntities);
  }

}
