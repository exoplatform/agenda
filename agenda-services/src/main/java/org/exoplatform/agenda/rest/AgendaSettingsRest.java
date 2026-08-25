package org.exoplatform.agenda.rest;

import java.util.Collections;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import org.exoplatform.services.rest.http.PATCH;

@Path("/v1/agenda/settings")
@Tag(name = "/v1/agenda/settings", description = "Manages agenda settings associated to users") // NOSONAR
public class AgendaSettingsRest implements ResourceContainer {
  private static final Log             LOG = ExoLogger.getLogger(AgendaSettingsRest.class);

  private AgendaUserSettingsService    agendaUserSettingsService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private AgendaWebSocketService       agendaWebSocketService;

  private IdentityManager              identityManager;

  public AgendaSettingsRest(AgendaUserSettingsService agendaUserSettingsService,
                            AgendaRemoteEventService agendaRemoteEventService,
                            AgendaWebSocketService agendaWebSocketService,
                            IdentityManager identityManager) {
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.agendaWebSocketService = agendaWebSocketService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
      summary = "Get User agenda settings",
      method = "GET"
  )
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response getUserSettings() {
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      long identityId = Long.parseLong(currentUserIdentity.getId());
      AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
      agendaUserSettings.setEmbedMapProvider(agendaUserSettingsService.getEmbedMapProvider());
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
  @Operation(
      summary = "Saves agenda settings for authenticated user",
      description = "Saves agenda settings for authenticated user",
      method = "PUT"
  )
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response saveUserSettings(
                                   @Parameter(
                                       description = "User agenda settings to update",
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
  @Operation(
      summary = "Saves agenda time zone setting for authenticated user",
      description = "Saves agenda time zone setting for authenticated user",
      method = "PUT")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response saveUserTimeZoneSetting(
                                          @Parameter(
                                              description = "User preferred time zone",
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
  @Operation(
      summary = "Saves agenda connector status whether enabled or disabled for all users",
      description = "Saves agenda connector status whether enabled or disabled for all users",
      method = "POST")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response saveRemoteProviderStatus(
                                           @Parameter(
                                               description = "Remote connector name",
                                               required = true
                                           )
                                           @FormParam("connectorName")
                                           String connectorName,
                                           @Parameter(
                                               description = "Remote connector status",
                                               required = true
                                           )
                                           @FormParam("enabled")
                                           boolean enabled,
                                           @Parameter(
                                                description = "Remote connector uses Oauth or not",
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
  @Operation(
      summary = "Saves agenda connector Client API Key that will be accessible by all users to access connector remote API",
      description = "Saves agenda connector Client API Key that will be accessible by all users to access connector remote API",
      method = "POST"
  )
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response saveRemoteProviderApiKey(
                                           @Parameter(
                                               description = "Remote connector name",
                                               required = true
                                           )
                                           @FormParam("connectorName")
                                           String connectorName,
                                           @Parameter(
                                               description = "Remote connector Api Key",
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

  @Path("connector/secretKey")
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Operation(summary = "Saves agenda connector Client Secret Key that will be accessible by all users to access connector remote API",
             description = "Saves agenda connector Client Secret Key that will be accessible by all users to access connector remote API",
             method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response saveRemoteProviderSecretKey(@Parameter(description = "Remote connector name", required = true)
                                              @FormParam("connectorName") String connectorName,
                                              @Parameter(description = "Remote connector Secret Key", required = true)
                                              @FormParam("secretKey") String secretKey) {
    if (StringUtils.isBlank(connectorName)) {
      return Response.status(Status.BAD_REQUEST).entity("'connectorName' parameter is mandatory").build();
    }

    try {
      RemoteProvider remoteProvider = agendaRemoteEventService.saveRemoteProviderSecretKey(connectorName, secretKey);
      return Response.ok(remoteProvider).build();
    } catch (Exception e) {
      LOG.warn("Error saving connector '{}' secretKey", connectorName, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("connector")
  @PATCH
  @RolesAllowed("users")
  @Operation(
      summary = "Saves agenda connector settings for authenticated user",
      description = "Saves agenda connector settings for authenticated user",
      method = "PUT")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response saveUserConnectorSettings(
                                            @Parameter(
                                                description = "User connector name",
                                                required = true
                                            )
                                            @FormParam("connectorName")
                                            String connectorName,
                                            @Parameter(
                                                description = "User connector identifier",
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
  @Operation(
      summary = "Deletes agenda connector settings for authenticated user",
      description = "Deletes agenda connector settings for authenticated user",
      method = "DELETE")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Bad request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  /**
   * Disconnects one connected account, or every account when no connector name
   * is given. The optional parameter is what lets several accounts coexist: a
   * caller naming a connector removes only that provider's account, while the
   * parameterless call keeps the behaviour the endpoint had when a single
   * account could exist. The removal itself is the service's business, not
   * this layer's.
   *
   * @param connectorName name of the remote provider whose account is
   *          disconnected, or null/blank to disconnect them all
   * @return 204 when the account is removed, 500 on failure
   */
  public Response deleteUserConnectorSettings(@Parameter(description = "Remote connector name of the account to disconnect; when absent, every connected account is disconnected")
                                              @QueryParam("connectorName") String connectorName) {
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      agendaUserSettingsService.removeUserConnector(connectorName, identityId);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error deleting agenda connector settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("embedMapProvider")
  @PATCH
  @RolesAllowed("administrators")
  @Operation(
    summary = "Saves the embed map provider to use globally",
    method = "PATCH")
  @ApiResponses(
    value = {
      @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
    })
  public Response saveEmbedMapProvider(@Parameter(description = "Map provider id", required = true)
                                       @FormParam("providerId") String providerId) {
    if (StringUtils.isBlank(providerId)) {
      return Response.status(Status.BAD_REQUEST).entity("'providerId' parameter is mandatory").build();
    }
    try {
      agendaUserSettingsService.saveEmbedMapProvider(providerId);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error saving embed map provider '{}'", providerId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("embedMapProvider")
  @DELETE
  @RolesAllowed("administrators")
  @Operation(summary = "Removes the globally configured embed map provider", method = "DELETE")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Request fulfilled"),
    @ApiResponse(responseCode = "500", description = "Internal server error"),
  })
  public Response removeEmbedMapProvider() {
    try {
      agendaUserSettingsService.removeEmbedMapProvider();
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error removing embed map provider", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}
