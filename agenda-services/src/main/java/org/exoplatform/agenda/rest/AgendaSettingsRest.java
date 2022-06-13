package org.exoplatform.agenda.rest;

import java.util.Collections;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.*;
import io.swagger.jaxrs.PATCH;

@Path("/v1/agenda/settings")
@Api(value = "/v1/agenda/settings", description = "Manages agenda settings associated to users") // NOSONAR
public class AgendaSettingsRest implements ResourceContainer {
  private static final Log             LOG = ExoLogger.getLogger(AgendaSettingsRest.class);

  private AgendaUserSettingsService    agendaUserSettingsService;

  private AgendaEventConferenceService agendaEventConferenceService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private AgendaWebSocketService       agendaWebSocketService;

  private IdentityManager              identityManager;

  public AgendaSettingsRest(AgendaUserSettingsService agendaUserSettingsService,
                            AgendaEventConferenceService agendaEventConferenceService,
                            AgendaRemoteEventService agendaRemoteEventService,
                            AgendaWebSocketService agendaWebSocketService,
                            IdentityManager identityManager) {
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.agendaWebSocketService = agendaWebSocketService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
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
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      long identityId = Long.parseLong(currentUserIdentity.getId());
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      String cometdToken = agendaWebSocketService.getUserToken(currentUserIdentity.getRemoteId());
      agendaUserSettings.setCometdToken(cometdToken);
      agendaUserSettings.setCometdContextName(agendaWebSocketService.getCometdContextName());
      return Response.ok(agendaUserSettings).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving agenda settings for user with id '{}'", currentUserIdentity, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
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
                                   )
                                   AgendaUserSettings agendaUserSettings) {
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
  @RolesAllowed("users")
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
                                          )
                                          @FormParam("timeZoneId")
                                          String timeZoneId) {
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

  @Path("connector/status")
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @ApiOperation(
      value = "Saves agenda connector status whether enabled or disabled for all users",
      httpMethod = "POST",
      response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveRemoteProviderStatus(
                                           @ApiParam(
                                               value = "Remote connector name",
                                               required = true
                                           )
                                           @FormParam("connectorName")
                                           String connectorName,
                                           @ApiParam(
                                               value = "Remote connector status",
                                               required = true
                                           )
                                           @FormParam("enabled")
                                           boolean enabled,
                                           @ApiParam(
                                                value = "Remote connector uses Oauth or not",
                                                required = true
                                           )
                                           @FormParam("isOauth")
                                           boolean isOauth) {
    if (StringUtils.isBlank(connectorName)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorName' parameter is mandatory").build();
    }

    try {
      RemoteProvider remoteProvider = agendaRemoteEventService.saveRemoteProviderStatus(connectorName, enabled, isOauth);
      return Response.ok(remoteProvider).build();
    } catch (Exception e) {
      LOG.warn("Error saving connector '{}' status", connectorName, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector/apiKey")
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @ApiOperation(
      value = "Saves agenda connector Client API Key that will be accessible by all users to access connector remote API",
      httpMethod = "POST",
      response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Bad request"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveRemoteProviderApiKey(
                                           @ApiParam(
                                               value = "Remote connector name",
                                               required = true
                                           )
                                           @FormParam("connectorName")
                                           String connectorName,
                                           @ApiParam(
                                               value = "Remote connector Api Key",
                                               required = true
                                           )
                                           @FormParam("apiKey")
                                           String apiKey) {
    if (StringUtils.isBlank(connectorName)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorName' parameter is mandatory").build();
    }

    try {
      RemoteProvider remoteProvider = agendaRemoteEventService.saveRemoteProviderApiKey(connectorName, apiKey);
      return Response.ok(remoteProvider).build();
    } catch (Exception e) {
      LOG.warn("Error saving connector '{}' apiKey", connectorName, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("webConferencing")
  @POST
  @RolesAllowed("administrators")
  @ApiOperation(
      value = "Saves enabled web conferencing provider to use for all users",
      httpMethod = "PUT",
      response = Response.class
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response saveEnabledWebConferencing(
                                             @ApiParam(
                                                 value = "Web conferencing provider name",
                                                 required = true
                                             )
                                             @FormParam("providerName")
                                             String providerName) {
    try {
      if (providerName == null) {
        providerName = "";
      } else {
        providerName = providerName.trim();
      }
      agendaEventConferenceService.saveEnabledWebConferenceProviders(Collections.singletonList(providerName));
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving enabled web conferencing provider '{}' status", providerName, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector")
  @PATCH
  @RolesAllowed("users")
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
                                            )
                                            @FormParam("connectorName")
                                            String connectorName,
                                            @ApiParam(
                                                value = "User connector identifier",
                                                required = true
                                            )
                                            @FormParam("connectorUserId")
                                            String connectorUserId) {
    if (StringUtils.isBlank(connectorName)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorName' parameter is mandatory").build();
    }
    if (StringUtils.isBlank(connectorUserId)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorUserId' parameter is mandatory").build();
    }

    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      agendaUserSettingsService.saveUserConnector(connectorName, connectorUserId, identityId);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving agenda settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector")
  @DELETE
  @RolesAllowed("users")
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
