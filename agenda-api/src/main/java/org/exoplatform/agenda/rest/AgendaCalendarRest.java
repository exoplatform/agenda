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

import static org.exoplatform.agenda.util.RestUtils.DEFAULT_LIMIT;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarList;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.swagger.annotations.*;

@Path("/v1/agenda/calendars")
@Api(value = "/v1/agenda/calendars", description = "Manages agenda calendars associated to users and spaces") // NOSONAR
public class AgendaCalendarRest {

  private static final Log      LOG = ExoLogger.getLogger(AgendaCalendarRest.class);

  private AgendaCalendarService agendaCalendarService;

  public AgendaCalendarRest(AgendaCalendarService agendaCalendarService) {
    this.agendaCalendarService = agendaCalendarService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Retrieves the list of calendars available for an owner of type user or space, identitifed by its identity technical identifier."
      + " If no designated owner, all calendars available for authenticated user will be retrieved.", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
  })
  public Response list(@ApiParam(value = "Identity technical identifier", required = false) @PathParam("ownerId") long ownerId,
                       @ApiParam(value = "Search keyword to use to search for calendars", required = false) @QueryParam("q") String query,
                       @ApiParam(value = "Whether return size of results or not", required = false, defaultValue = "false") @QueryParam("returnSize") boolean returnSize,
                       @ApiParam(value = "Offset of result", required = false, defaultValue = "0") @QueryParam("offset") int offset,
                       @ApiParam(value = "Limit of result", required = false, defaultValue = "10") @QueryParam("limit") int limit) {
    if (offset < 0) {
      offset = 0;
    }
    if (limit <= 0) {
      limit = DEFAULT_LIMIT;
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      List<Calendar> calendars = null;
      if (ownerId <= 0) {
        calendars = agendaCalendarService.getCalendars(query, offset, limit, currentUser);
      } else {
        calendars = agendaCalendarService.getCalendarsByOwner(ownerId, query, offset, limit, currentUser);
      }
      CalendarList calendarList = new CalendarList();
      calendarList.setCalendars(calendars);
      if (returnSize && !calendars.isEmpty()) {
        int returnedCalendarSize = calendarList.getCalendars().size();
        // retieve count from DB only if the size of returned calendars equals
        // to the requested limit, in which case the calendar size may be
        // greater than limit
        if (returnedCalendarSize >= limit) {
          int calendarCount = agendaCalendarService.countCalendarsByOwner(ownerId, query, currentUser);
          calendarList.setSize(calendarCount);
        } else {
          calendarList.setSize(returnedCalendarSize);
        }
      }

      return Response.ok(calendarList).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized calendar with owner Id '{}'", currentUser, ownerId);
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
  @ApiOperation(value = "Retrieves a calendar identified by its technical identifier.", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
  })
  public Response getCalendarById(@ApiParam(value = "Calendar technical identifier", required = true) @PathParam("calendarId") long calendarId) {
    if (calendarId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar identifier must be a positive integer").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      Calendar calendar = agendaCalendarService.getCalendarById(calendarId, currentUser);
      if (calendar == null) {
        return Response.status(Status.NOT_FOUND).build();
      } else {
        return Response.ok(calendar).build();
      }
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized calendar with Id '{}'", currentUser, calendarId);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving a calendar with id '{}'", calendarId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Creates a new calendar", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
  })
  public Response createCalendar(@ApiParam(value = "Calendar object to create", required = true) Calendar calendar) {
    if (calendar == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar object is mandatory").build();
    }
    if (calendar.getOwner() == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar owner is mandatory").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.createCalendar(calendar, currentUser);
      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create a calendar for owner '{}'", currentUser, calendar.getOwner().getId());
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Updates an existing calendar", httpMethod = "PUT", response = Response.class, consumes = "application/json")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
  })
  public Response updateCalendar(@ApiParam(value = "Calendar object to update", required = true) Calendar calendar) {
    if (calendar == null) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar object is mandatory").build();
    }
    if (calendar.getId() <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar technical identifier must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.updateCalendar(calendar, currentUser);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Calendar not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a calendar for owner '{}'", currentUser, calendar.getOwner().getId());
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @ApiOperation(value = "Deletes an existing calendar", httpMethod = "DELETE", response = Response.class)
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
  })
  public Response deleteCalendar(@ApiParam(value = "Calendar technical identifier", required = true) @PathParam("calendarId") long calendarId) {
    if (calendarId <= 0) {
      return Response.status(Status.BAD_REQUEST).entity("Calendar technical identifier must be positive").build();
    }

    String currentUser = RestUtils.getCurrentUser();
    try {
      agendaCalendarService.deleteCalendarById(calendarId, currentUser);
      return Response.noContent().build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity("Calendar not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to deletes a non authorized calendar", currentUser);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error deleting a calendar", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}
