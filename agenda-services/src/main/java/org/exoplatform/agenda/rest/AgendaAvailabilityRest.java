/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.rest.model.UserBusyTimeEntity;
import org.exoplatform.agenda.rest.util.AvailabilityEntityBuilder;
import org.exoplatform.agenda.service.AgendaAvailabilityService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads other people's busy time, so that a screen where a meeting is being
 * scheduled can draw it.
 * <p>
 * <strong>This controller adds no gate.</strong> There is exactly one place in
 * the product that decides who may read whose free/busy —
 * {@code AgendaAvailabilityServiceImpl}, over the calendar ACL floor
 * (EXO-89841) and the target's own sharing setting (EXO-89844) — and this
 * resource does nothing but call it and render what it answered. A second
 * check here would be a second thing to keep in step with the first, and the
 * two would drift.
 * <p>
 * <strong>The asking user comes from the session, never from a parameter.</strong>
 * {@code request.getRemoteUser()} is what the container authenticated; an
 * identifier the caller supplies is a claim, and a claim reaching an ACL
 * decision is how an authorisation check stops being one.
 * <p>
 * The answer degrades rather than refusing: a participant whose busy time is
 * not readable is reported as unread, not omitted and not emptied, so the
 * screen can name them. See {@link UserBusyTimeEntity}.
 */
@RestController
@RequestMapping("availability")
@Tag(name = "availability", description = "Reads the busy time of the people a meeting is being scheduled with")
public class AgendaAvailabilityRest {

  private final AgendaAvailabilityService agendaAvailabilityService;

  private final IdentityManager           identityManager;

  /**
   * Builds the resource.
   *
   * @param agendaAvailabilityService the one holder of the free/busy gate
   * @param identityManager used to resolve the authenticated user's identity
   */
  @Autowired
  public AgendaAvailabilityRest(AgendaAvailabilityService agendaAvailabilityService, IdentityManager identityManager) {
    this.agendaAvailabilityService = agendaAvailabilityService;
    this.identityManager = identityManager;
  }

  /**
   * Answers the busy time of each named participant over a window, with the
   * status of each read.
   *
   * @param request the authenticated request, the only source of the asking
   *          user's identity
   * @param identityIds technical identifiers of the participants asked about
   * @param start window start, ISO-8601
   * @param end window end, ISO-8601
   * @param timeZoneId the asking user's own zone, the one the ranges come
   *          back in — the same parameter the events resource takes, so the
   *          grid reads a busy block's wall-clock time the way it reads every
   *          other event on it. Blank means UTC.
   * @return one entry per distinct participant, in the order asked
   */
  @GetMapping
  @Secured("users")
  @Operation(summary = "Get the busy time of a set of users over a window", method = "GET",
             description = "Answers one entry per user: their busy ranges when their availability is disclosed to the"
                 + " authenticated user, and otherwise the reason it is not. Only time ranges are ever returned.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
  })
  public List<UserBusyTimeEntity> getBusyTime(HttpServletRequest request,
                                              @RequestParam("identityIds") List<Long> identityIds,
                                              @RequestParam("start") String start,
                                              @RequestParam("end") String end,
                                              @RequestParam(name = "timeZoneId", required = false) String timeZoneId) {
    Identity currentUserIdentity = identityManager.getOrCreateUserIdentity(request.getRemoteUser());
    if (currentUserIdentity == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "agenda.availability.unauthenticated");
    }
    try {
      return AvailabilityEntityBuilder.toEntities(agendaAvailabilityService.getBusyTime(identityIds,
                                                                                        parse(start),
                                                                                        parse(end),
                                                                                        Long.parseLong(currentUserIdentity.getId())),
                                                  zoneOf(timeZoneId));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Reads the zone the ranges are to be rendered in.
   *
   * @param timeZoneId the zone identifier, may be blank
   * @return the zone, UTC when none was given
   * @throws IllegalArgumentException when the identifier cannot be read, so
   *           that it reaches the caller as a 400 rather than as a 500
   */
  private ZoneId zoneOf(String timeZoneId) {
    if (StringUtils.isBlank(timeZoneId)) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timeZoneId);
    } catch (Exception e) { // NOSONAR an unreadable zone is a bad request, not a failure
      throw new IllegalArgumentException("agenda.availability.timeZoneUnreadable", e);
    }
  }

  /**
   * Reads one bound of the window.
   *
   * @param dateTime the bound, ISO-8601 with an offset
   * @return the parsed instant
   * @throws IllegalArgumentException when the bound cannot be read, so that it
   *           reaches the caller as a 400 with a message code rather than as a
   *           500
   */
  private ZonedDateTime parse(String dateTime) {
    try {
      return ZonedDateTime.parse(dateTime);
    } catch (DateTimeParseException | NullPointerException e) { // NOSONAR a missing bound is a bad request, not a failure
      throw new IllegalArgumentException("agenda.availability.windowMandatory", e);
    }
  }

}
