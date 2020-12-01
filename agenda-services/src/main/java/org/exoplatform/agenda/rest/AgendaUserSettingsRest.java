package org.exoplatform.agenda.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

  @Path("agendaSettings")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Saves default settings for authenticated user", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response saveReminderSettings(AgendaUserSettings agendaUserSettings) {
    long identityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.warn("Error retrieving event reminders settings for user with id '{}'", identityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }
}
