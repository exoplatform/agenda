package org.exoplatform.agenda.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.*;
import io.swagger.jaxrs.PATCH;

@Path("/v1/agenda/settings")
@Api(value = "/v1/agenda/settings", description = "Manages agenda settings associated to users") // NOSONAR
public class AgendaUserSettingsRest implements ResourceContainer {
  private static final Log          LOG = ExoLogger.getLogger(AgendaUserSettingsRest.class);

  private AgendaUserSettingsService agendaUserSettingsService;

  private IdentityManager           identityManager;

  public AgendaUserSettingsRest(AgendaUserSettingsService agendaUserSettingsService, IdentityManager identityManager) {
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Get User agenda settings",
      httpMethod = "GET",
      response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response getUserSettings() {
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      return Response.ok(agendaUserSettings).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving agenda settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Saves agenda settings for authenticated user",
      httpMethod = "PUT",
      response = Response.class,
      consumes = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveUserSettings(
                                   @ApiParam(
                                       value = "User agenda settings to update",
                                       required = true
                                   ) AgendaUserSettings agendaUserSettings) {
    if (agendaUserSettings == null) {
      return Response.status(Status.BAD_REQUEST).entity("Agenda settings object is mandatory").build();
    }
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving agenda settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("timeZone")
  @PATCH
  @ApiOperation(
      value = "Saves agenda time zone setting for authenticated user",
      httpMethod = "PUT",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveUserTimeZoneSetting(
                                          @ApiParam(
                                              value = "User preferred time zone",
                                              required = true
                                          ) @FormParam("timeZoneId") String timeZoneId) {
    if (StringUtils.isBlank(timeZoneId)) {
      return Response.status(Status.BAD_REQUEST).entity("'timeZoneId' parameter is mandatory").build();
    }

    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      agendaUserSettings.setTimeZoneId(timeZoneId);
      agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving agenda timezone settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector")
  @PATCH
  @ApiOperation(
      value = "Saves agenda connector settings for authenticated user",
      httpMethod = "PUT",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveUserConnectorSettings(
                                            @ApiParam(
                                                value = "User connector name",
                                                required = true
                                            ) @FormParam("connectorName") String connectorName,
                                            @ApiParam(
                                                value = "User connector identifier",
                                                required = true
                                            ) @FormParam("connectorUserId") String connectorUserId) {
    if (StringUtils.isBlank(connectorName)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorName' parameter is mandatory").build();
    }
    if (StringUtils.isBlank(connectorUserId)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorUserId' parameter is mandatory").build();
    }

    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      agendaUserSettings.setConnectedRemoteUserId(connectorUserId);
      agendaUserSettings.setConnectedRemoteProvider(connectorName);
      agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving agenda settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector")
  @DELETE
  @ApiOperation(
      value = "Deletes agenda connector settings for authenticated user",
      httpMethod = "DELETE",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response deleteUserConnectorSettings() {
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      agendaUserSettings.setConnectedRemoteUserId(null);
      agendaUserSettings.setConnectedRemoteProvider(null);
      agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error deleting agenda connector settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}
