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

import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventSplitEntity;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Splits a recurring event at one of its dates: a "this and upcoming" change.
 * <p>
 * <b>No rule lives here.</b> Who may split, what the continuation keeps and
 * which answers start over are {@link AgendaEventService}'s; this resource
 * reads the user from the session, turns the body into the split the Service
 * takes and maps its refusals to statuses.
 * <p>
 * <b>It answers the continuation's identifier</b>, which the event page then
 * reads from the event resource as it reads any event: that read builds its
 * identities through social's {@code EntityBuilder}, which needs the JAX-RS
 * request context a Spring MVC request does not have.
 * <p>
 * <b>A refusal writes its own body</b>, {@code {"message": "<code>"}}, as
 * {@link AgendaEventAttendeeRest} does, with the date that blocks when a limit
 * is below the Yes answers of one of the series' dates. No exception's text is
 * ever a body: it goes to the log.
 */
@RestController
@Tag(name = "events", description = "Agenda events")
public class AgendaEventSplitRest {

  private static final Log                 LOG          = ExoLogger.getLogger(AgendaEventSplitRest.class);

  private static final String              CODE_PREFIX  = "agenda.";

  private static final String              FORBIDDEN    = "agenda.split.forbidden";

  private static final String              INVALID      = "agenda.split.invalid";

  private static final String              INVALID_DATE = "agenda.split.invalidDate";

  private static final String              FAILED       = "agenda.split.failed";

  private final IdentityManager            identityManager;

  private final AgendaEventService         agendaEventService;

  private final AgendaEventAttendeeService agendaEventAttendeeService;

  /**
   * Builds the resource.
   *
   * @param identityManager resolves the user of the session and the people
   *          the change adds or removes
   * @param agendaEventService holder of every rule of the split
   * @param agendaEventAttendeeService reads the series' attendees, among
   *          whom the people removed are found
   */
  @Autowired
  public AgendaEventSplitRest(IdentityManager identityManager,
                              AgendaEventService agendaEventService,
                              AgendaEventAttendeeService agendaEventAttendeeService) {
    this.identityManager = identityManager;
    this.agendaEventService = agendaEventService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
  }

  /**
   * Splits a recurring event at one of its dates.
   *
   * @param request the authenticated request
   * @param eventId technical identifier of the recurring event
   * @param timeZoneId the zone the user read the event in
   * @param confirmClose whether the user confirmed closing the event while
   *          people wait for a seat
   * @param splitEntity the change
   * @return {@code {"id": <the continuing series>}}, or a refusal
   */
  @PostMapping(path = "events/{eventId}/split", consumes = MediaType.APPLICATION_JSON_VALUE,
               produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("users")
  @Operation(summary = "Splits a recurring event at one of its dates: a 'this and upcoming' change", method = "POST",
             description = "Creates the series that continues the recurring event from the date given, with the fields the user"
                 + " edited, in the event's calendar; moves the dates customised from that date on under it; and ends the event"
                 + " the day before. When no date moves, every participant keeps their answers and their places on the waiting"
                 + " list; when one does, every answer but the user's starts over. Everything is written at once, or nothing."
                 + " Answers the identifier of the continuing series, which the event resource reads.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The identifier of the continuing series: {\"id\": <id>}"),
      @ApiResponse(responseCode = "400",
                   description = "Invalid input: a date that is not one of the event's after its first, an invalid limit, a"
                       + " limit below the Yes answers of a date, a closing of the event while people wait that is not"
                       + " confirmed, a person from outside the platform on an open continuation or on a date that stays"
                       + " open, or a date or a zone that does not parse. The body carries the message code, and the date for"
                       + " a limit below the Yes answers"),
      @ApiResponse(responseCode = "403", description = "The user may not split the event"),
      @ApiResponse(responseCode = "404", description = "Event not found"),
      @ApiResponse(responseCode = "500", description = "The split failed; the body carries a fixed message code"),
  })
  public ResponseEntity<Object> splitEvent(HttpServletRequest request,
                                           @Parameter(description = "Technical identifier of the recurring event", required = true)
                                           @PathVariable("eventId")
                                           long eventId,
                                           @Parameter(description = "IANA time zone identifier the event was read in. A change"
                                               + " given in that zone, or in none, keeps the event's own zone, and a weekly rule"
                                               + " naming the one weekday, or a monthly or yearly rule naming no weekday and the one"
                                               + " day of the month, that its first date falls on in that zone names the one that"
                                               + " date falls on in the event's zone; any other rule the change gives keeps the"
                                               + " days it names. The dates of a change given in no zone are read in that zone, or"
                                               + " in UTC when it is missing")
                                           @RequestParam(name = "timeZoneId", required = false)
                                           String timeZoneId,
                                           @Parameter(description = "Whether the user confirmed closing the event while people"
                                               + " are waiting for a seat")
                                           @RequestParam(name = "confirmClose", defaultValue = "false")
                                           boolean confirmClose,
                                           @Parameter(description = "The change", required = true)
                                           @RequestBody(required = false)
                                           EventSplitEntity splitEntity) {
    if (eventId <= 0) {
      return refusal(HttpStatus.BAD_REQUEST, AgendaExceptionType.EVENT_ID_MANDATORY.getMessage());
    }
    if (splitEntity == null) {
      return refusal(HttpStatus.BAD_REQUEST, AgendaExceptionType.EVENT_MANDATORY.getMessage());
    }
    Identity user = identityManager.getOrCreateUserIdentity(request.getRemoteUser());
    if (user == null) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
    try {
      Event continuation = RestUtils.splitEvent(identityManager,
                                                agendaEventService,
                                                agendaEventAttendeeService,
                                                eventId,
                                                splitEntity,
                                                timeZoneId,
                                                confirmClose,
                                                Long.parseLong(user.getId()));
      return ResponseEntity.ok(Map.of("id", continuation.getId()));
    } catch (AgendaException e) {
      LOG.debug("Split of event {} refused", eventId, e);
      AgendaExceptionType type = e.getAgendaExceptionType();
      return refusal(type == AgendaExceptionType.EVENT_NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST, type.getMessage());
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' attempts to split a non authorized event {}", request.getRemoteUser(), eventId, e);
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (CapacityBelowAcceptedException e) {
      LOG.debug("Split of event {} refused: a limit below the Yes answers of a date", eventId, e);
      return e.getDate() == null ? refusal(HttpStatus.BAD_REQUEST, e.getMessage())
                                 : refusal(HttpStatus.BAD_REQUEST, e.getMessage(), e.getDate().toString());
    } catch (IllegalArgumentException e) {
      LOG.debug("Invalid split of event {}", eventId, e);
      return refusal(HttpStatus.BAD_REQUEST, StringUtils.startsWith(e.getMessage(), CODE_PREFIX) ? e.getMessage() : INVALID);
    } catch (DateTimeException e) {
      LOG.debug("Invalid split date or zone of event {}", eventId, e);
      return refusal(HttpStatus.BAD_REQUEST, INVALID_DATE);
    } catch (RuntimeException e) {
      LOG.warn("Error splitting event {}", eventId, e);
      return refusal(HttpStatus.INTERNAL_SERVER_ERROR, FAILED);
    }
  }

  /**
   * A body that cannot be read as a change is refused with a code, never
   * with the parser's text.
   *
   * @param e the parser's failure
   * @return the refusal
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> unreadableBody(HttpMessageNotReadableException e) {
    LOG.debug("Unreadable split of an event", e);
    return refusal(HttpStatus.BAD_REQUEST, INVALID);
  }

  /**
   * @param status the status
   * @param code the message code
   * @return a refusal with its code written in the body,
   *         {@code {"message": code}}
   */
  private static ResponseEntity<Object> refusal(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(Map.of("message", code));
  }

  /**
   * @param status the status
   * @param code the message code
   * @param date the date that blocks, ISO-8601
   * @return a refusal with its code and the date that blocks written in the
   *         body, {@code {"message": code, "date": date}}
   */
  private static ResponseEntity<Object> refusal(HttpStatus status, String code, String date) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("message", code);
    body.put("date", date);
    return ResponseEntity.status(status).body(body);
  }

}
