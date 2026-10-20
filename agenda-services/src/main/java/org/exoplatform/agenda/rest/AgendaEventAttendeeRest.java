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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.exoplatform.agenda.model.EventAttendeesExport;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Serves the participant list of an event as a CSV file.
 * <p>
 * <b>No rule lives here.</b> Which list, who may export it and what each row
 * holds are {@link AgendaEventAttendeeService}'s; this resource reads the
 * user from the session and their language from their settings, maps the
 * service's refusals to statuses and writes the file's headers. Neither the
 * file nor a refusal is ever stored by a cache: the file carries profile data.
 * <p>
 * <b>A refusal writes its own body</b>, {@code {"message": "<code>"}}, as
 * {@link AgendaCalendarLinkRest} does.
 */
@RestController
@Tag(name = "event-attendees", description = "Participant lists of agenda events")
public class AgendaEventAttendeeRest {

  private static final MediaType            TEXT_CSV     = new MediaType("text", "csv", StandardCharsets.UTF_8);

  private static final String               FORBIDDEN    = "agenda.export.forbidden";

  private static final String               NOT_FOUND    = "agenda.export.eventNotFound";

  private static final String               INVALID      = "agenda.export.invalid";

  private static final String               CODE_PREFIX  = "agenda.export.";

  private final AgendaEventAttendeeService agendaEventAttendeeService;

  private final IdentityManager            identityManager;

  /**
   * Builds the resource.
   *
   * @param agendaEventAttendeeService holder of every rule of the export
   * @param identityManager resolves the user of the session
   */
  @Autowired
  public AgendaEventAttendeeRest(AgendaEventAttendeeService agendaEventAttendeeService, IdentityManager identityManager) {
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.identityManager = identityManager;
  }

  /**
   * Exports the participant list of one date of an event, as the participants
   * drawer shows it, for a user allowed to update the event.
   *
   * @param request the authenticated request
   * @param eventId technical identifier of the event, of a series, or of the
   *          row of one of its dates
   * @param occurrenceId occurrence id of a date of the series, RFC 3339
   * @param timeZoneId the user's zone, which dates the file name
   * @return the CSV file, or a refusal
   */
  @GetMapping("events/{eventId}/attendees/export")
  @Secured("users")
  @Operation(summary = "Export the participant list of an event as a CSV file", method = "GET",
             description = "Answers a CSV file with one row per participant of the date, as the participants drawer lists"
                 + " them: name, email when the platform displays it, and answer. Only someone allowed to update the event"
                 + " may export it, on a confirmed event; never cached.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The CSV file"),
      @ApiResponse(responseCode = "400", description = "Invalid date or zone, or the event is a date poll"),
      @ApiResponse(responseCode = "403", description = "The user may not update the event"),
      @ApiResponse(responseCode = "404", description = "Event not found, or the date was cancelled"),
  })
  public ResponseEntity<?> exportEventAttendees(HttpServletRequest request,
                                                @Parameter(description = "Event technical identifier", required = true)
                                                @PathVariable("eventId")
                                                long eventId,
                                                @Parameter(description = "Occurrence id of a date of the series, RFC 3339")
                                                @RequestParam(name = "occurrenceId", required = false)
                                                String occurrenceId,
                                                @Parameter(description = "IANA time zone of the user, dating the file name")
                                                @RequestParam(name = "timeZoneId", required = false)
                                                String timeZoneId) {
    ZonedDateTime occurrence;
    ZoneId zoneId;
    try {
      occurrence = AgendaDateUtils.parseRFC3339ToZonedDateTime(occurrenceId, ZoneOffset.UTC);
      zoneId = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
    } catch (DateTimeException e) {
      return refusal(HttpStatus.BAD_REQUEST, INVALID);
    }
    Identity user = identityManager.getOrCreateUserIdentity(request.getRemoteUser());
    if (user == null) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
    try {
      EventAttendeesExport export = agendaEventAttendeeService.exportEventAttendees(eventId,
                                                                                   occurrence,
                                                                                   Long.parseLong(user.getId()),
                                                                                   userLocale(request),
                                                                                   zoneId);
      return ResponseEntity.ok()
                           .cacheControl(CacheControl.noStore().cachePrivate())
                           .header(HttpHeaders.CONTENT_DISPOSITION, attachment(export.fileName()))
                           .contentType(TEXT_CSV)
                           .body(export.content().getBytes(StandardCharsets.UTF_8));
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      return refusal(HttpStatus.BAD_REQUEST, StringUtils.startsWith(e.getMessage(), CODE_PREFIX) ? e.getMessage() : INVALID);
    }
  }

  /**
   * @param fileName the file's name
   * @return the header saving the file under its name: an ASCII name for the
   *         clients that read only {@code filename}, the UTF-8 name of RFC
   *         6266 for the others
   */
  static String attachment(String fileName) {
    StringBuilder asciiName = new StringBuilder(fileName.length());
    fileName.codePoints()
            .forEach(c -> asciiName.appendCodePoint(c < 0x20 || c > 0x7E || c == '"' || c == '\\' ? '_' : c));
    String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    return "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName;
  }

  /**
   * @return the language the user chose for the platform, the request's when
   *         their settings give none
   */
  private static Locale userLocale(HttpServletRequest request) {
    String language = Utils.getUserLanguage(request.getRemoteUser());
    return StringUtils.isBlank(language) ? request.getLocale() : LocaleUtils.toLocale(language);
  }

  /**
   * A refusal with its code written in the body, {@code {"message": code}},
   * and never cached.
   *
   * @param status the status
   * @param code the message code
   * @return the response
   */
  private static ResponseEntity<Map<String, String>> refusal(HttpStatus status, String code) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore().cachePrivate()).body(Map.of("message", code));
  }

}
