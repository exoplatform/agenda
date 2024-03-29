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

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.rest.model.CalendarEntity;
import org.exoplatform.agenda.rest.model.CalendarList;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.util.RestEntityBuilder;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;


@Path("/v1/agenda/calendars")
@Tag(name = "/v1/agenda/calendars", description = "Manages agenda calendars associated to users and spaces")
public class AgendaCalendarRest implements ResourceContainer {

  private static final Log      LOG = ExoLogger.getLogger(AgendaCalendarRest.class);

  private AgendaCalendarService agendaCalendarService;

  private IdentityManager       identityManager;

  public AgendaCalendarRest(AgendaCalendarService agendaCalendarService, IdentityManager identityManager) {
    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Retrieves the list of calendars",   
      description = "Retrieves the list of calendars available for an owner of type user or space, identitifed by its identity technical identifier."
          + " If no designated owner, all calendars available for authenticated user will be retrieved.",
      method = "GET"
  )
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response list(
                       @Parameter(description = "Limit of calendar owner identity ids to incluse in results")
                       @QueryParam(
                         "ownerIds"
                       )
                       List<Long> ownerIds,
                       @Parameter(description= "Whether return size of results or not") @Schema(defaultValue = "false")
                       @QueryParam("returnSize")
                       boolean returnSize,
                       @Parameter(description= "Offset of result") @Schema(defaultValue = "0")
                       @QueryParam(
                         "offset"
                       )
                       int offset,
                       @Parameter(description= "Limit of result") @Schema(defaultValue = "10")
                       @QueryParam(
                         "limit"
                       )
                       int limit) {
    if (offset < 0) {
      return Response.status(Status.BAD_REQUEST).entity("Offset must be 0 or positive").build();
    }
    if (limit <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Limit must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<Calendar> calendars = null;
      if (ownerIds == null || ownerIds.isEmpty()) {
        calendars = agendaCalendarService.getCalendars(offset, limit, currentUser);
      } else {
        calendars = agendaCalendarService.getCalendarsByOwnerIds(ownerIds, currentUser);
      }
      CalendarList calendarList = new CalendarList();
      if (ownerIds != null && !ownerIds.isEmpty()) {
        for (Long ownerId : ownerIds) {
          boolean calendarNotFound = calendars.stream().noneMatch(calendar -> calendar.getOwnerId() == ownerId);
          if (calendarNotFound) {
            long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
            Calendar calendar = agendaCalendarService.createCalendarInstance(ownerId, userIdentityId);
            calendars.add(calendar);
          }
        }
      } else if (returnSize && !calendars.isEmpty()) {
        int returnedCalendarSize = calendars.size();
        // retieve count from DB only if the size of returned calendars equals
        // to the requested limit, in which case the calendar size may be
        // greater than limit
        if (returnedCalendarSize >= limit) {
          int calendarCount = agendaCalendarService.countCalendars(currentUser);
          calendarList.setSize(calendarCount);
        } else {
          calendarList.setSize(returnedCalendarSize);
        }
      }
      List<CalendarEntity> calendarEntities = calendars.stream()
                                                       .map(calendar -> RestEntityBuilder.fromCalendar(identityManager, calendar))
                                                       .collect(Collectors.toList());
      calendarList.setCalendars(calendarEntities);

      return Response.ok(calendarList).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized calendar with owner Ids '{}'", currentUser, ownerIds, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of calendars", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{calendarId}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Retrieves a calendar identified by its technical identifier",
      description = "Retrieves a calendar identified by its technical identifier",
      method = "GET"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error") }
  )
  public Response getCalendarById(
                                  @Parameter(
                                      description = "Calendar technical identifier",
                                      required = true
                                  )
                                  @PathParam("calendarId")
                                  long calendarId) {
    if (calendarId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      Calendar calendar = agendaCalendarService.getCalendarById(calendarId, currentUser);
      if (calendar == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        return Response.ok(RestEntityBuilder.fromCalendar(identityManager, calendar)).build();
      }
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized calendar with Id '{}'", currentUser, calendarId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving a calendar with id '{}'", calendarId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
          summary = "Creates a new calendar",
          description = "Creates a new calendar",
          method = "POST")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response createCalendar(
                                 @Parameter(
                                     description = "Calendar object to create",
                                     required = true
                                 )
                                 CalendarEntity calendarEntity) {
    if (calendarEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar object is mandatory").build();
    }
    if (calendarEntity.getOwner() == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar owner is mandatory").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.createCalendar(RestEntityBuilder.toCalendar(calendarEntity), currentUser);
      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create a calendar for owner '{}'", currentUser, calendarEntity.getOwner(), e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Updates an existing calendar",
      description = "Updates an existing calendar",
      method = "PUT"
  )
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response updateCalendar(
                                 @Parameter(
                                     description = "Calendar object to update",
                                     required = true
                                 )
                                 CalendarEntity calendarEntity) {
    if (calendarEntity == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar object is mandatory").build();
    }
    if (calendarEntity.getId() <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar technical identifier must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.updateCalendar(RestEntityBuilder.toCalendar(calendarEntity), currentUser);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing calendar '{}'", currentUser, calendarEntity.getOwner(), e);
      return Response.status(Status.NOT_FOUND).entity("Calendar not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a calendar for owner '{}'", currentUser, calendarEntity.getOwner(), e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @Operation(summary = "Deletes an existing calendar", description = "Deletes an existing calendar", method = "DELETE")
  @ApiResponses(
      value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Object not found"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), }
  )
  public Response deleteCalendar(
                                 @Parameter(description = "Calendar technical identifier", required = true)
                                 @PathParam(
                                   "calendarId"
                                 )
                                 long calendarId) {
    if (calendarId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar technical identifier must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.deleteCalendarById(calendarId, currentUser);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to delete a not existing calendar '{}'", currentUser, calendarId, e);
      return Response.status(Status.NOT_FOUND).entity("Calendar not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to deletes a non authorized calendar", currentUser, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error deleting a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}
