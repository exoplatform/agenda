/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.rest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.ConferenceEventEntity;
import org.exoplatform.agenda.rest.util.ConferenceEventEntityBuilder;
import org.exoplatform.agenda.service.AgendaConferenceEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tells which event a web conferencing call was held for, so that what the
 * call left behind, such as a recording, can link to it.
 */
@RestController
@RequestMapping("conference-event")
@Tag(name = "conference-event", description = "Finds the event a web conferencing call was held for")
public class AgendaConferenceEventRest {

  public static final String                 INVALID_MOMENT      = "agenda.conferenceEvent.invalidMoment";

  /**
   * The grace when the caller gives none: the default of web conferencing's
   * <code>occurrence-grace-hours</code>, which files a recording under an
   * occurrence up to that long after its end.
   */
  public static final String                 DEFAULT_GRACE_HOURS = "12";

  private final AgendaConferenceEventService conferenceEventService;

  private final IdentityManager              identityManager;

  /**
   * Builds the resource.
   *
   * @param conferenceEventService finds the event of a call
   * @param identityManager resolves the authenticated user's identity
   */
  @Autowired
  public AgendaConferenceEventRest(AgendaConferenceEventService conferenceEventService, IdentityManager identityManager) {
    this.conferenceEventService = conferenceEventService;
    this.identityManager = identityManager;
  }

  /**
   * Answers the event, or the occurrence of a recurring event, a call was
   * held for at a moment, as {@link AgendaConferenceEventService#getConferenceEvent}
   * finds it for the authenticated user.
   *
   * @param request the authenticated request, the only source of the reading
   *          user's identity
   * @param providerType the web conferencing provider type
   * @param callId the call identifier
   * @param moment when the call was held, ISO-8601 with an offset
   * @param graceHours how many hours after an occurrence's end the call still
   *          belongs to it
   * @return 200 with the event, or 204 when the lookup answers no event the
   *         user can read in full; never stored by a cache, the answer
   *         depending on the reader
   */
  @GetMapping
  @Secured("users")
  @Operation(summary = "Get the event a web conferencing call was held for", method = "GET",
             description = "Answers the event a call was held for, among the single events and the occurrences whose own"
                 + " meeting link refers to the call and that the authenticated user can read in full: the one whose"
                 + " span, extended by the grace, holds the moment, the latest when several do; else the single event or"
                 + " series referring to the call started last before the moment. An occurrence is looked at when it"
                 + " started at most the grace and 36 hours before the moment.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The event"),
      @ApiResponse(responseCode = "204", description = "The rule answers no event the user can read in full"),
      @ApiResponse(responseCode = "400", description = "Missing provider type or call identifier, unreadable moment, or a grace out of 0 to 168 hours"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
  })
  public ResponseEntity<ConferenceEventEntity> getConferenceEvent(HttpServletRequest request,
                                                                  @Parameter(description = "Web conferencing provider type")
                                                                  @RequestParam("providerType")
                                                                  String providerType,
                                                                  @Parameter(description = "Call identifier")
                                                                  @RequestParam("callId")
                                                                  String callId,
                                                                  @Parameter(description = "When the call was held, ISO-8601")
                                                                  @RequestParam("moment")
                                                                  String moment,
                                                                  @Parameter(description = "Hours after an occurrence's end the"
                                                                      + " call still belongs to it, 0 to 168, 12 when not given")
                                                                  @RequestParam(name = "graceHours", required = false,
                                                                                defaultValue = DEFAULT_GRACE_HOURS)
                                                                  int graceHours) {
    Identity identity = identityManager.getOrCreateUserIdentity(request.getRemoteUser());
    if (identity == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "agenda.conferenceEvent.unauthenticated");
    }
    try {
      Event event = conferenceEventService.getConferenceEvent(providerType,
                                                              callId,
                                                              parse(moment),
                                                              graceHours,
                                                              Long.parseLong(identity.getId()));
      return event == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                           : ResponseEntity.ok()
                                           .cacheControl(CacheControl.noStore())
                                           .body(ConferenceEventEntityBuilder.toEntity(event));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Reads the moment of the call.
   *
   * @param moment ISO-8601 with an offset
   * @return the parsed moment
   * @throws IllegalArgumentException when it cannot be read, so that it
   *           reaches the caller as a 400 with a message code
   */
  private ZonedDateTime parse(String moment) {
    try {
      return ZonedDateTime.parse(moment);
    } catch (DateTimeParseException | NullPointerException e) { // NOSONAR an unreadable moment is a bad request
      throw new IllegalArgumentException(INVALID_MOMENT, e);
    }
  }

}
