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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.agenda.rest.model.CalendarLinkStatusEntity;
import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.agenda.util.CalendarFeedIcsWriter;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Manages the private iCal link of a calendar, and serves the document that
 * link publishes.
 * <p>
 * <b>The feed is the one handler of this WAR reachable without a session.</b>
 * The portal's security chain lets any request, anonymous included, through to
 * {@code /rest/} and leaves the decision to method security; every other handler
 * carries {@code @Secured("users")}, the feed deliberately none — its token is
 * its credential. {@code AgendaRestSecurityTest} pins that it stays the only
 * one.
 * <p>
 * <b>No rule lives here.</b> Who may manage a link and when a link answers are
 * {@link AgendaCalendarLinkService}'s; this resource maps its exceptions to
 * statuses and renders its answers. The asking user comes from the session,
 * never from a parameter.
 */
@RestController
@Tag(name = "calendar-link", description = "Private read-only iCal links of calendars")
public class AgendaCalendarLinkRest {

  /** Path of the feed under the WAR's REST root, token and extension appended. */
  public static final String              FEED_PATH     = "/rest/ical/";

  /** Extension of the feed URL: many calendar applications expect it. */
  public static final String              FEED_EXTENSION = ".ics";

  private static final Log                LOG           = ExoLogger.getLogger(AgendaCalendarLinkRest.class);

  private static final MediaType          TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

  private final AgendaCalendarLinkService calendarLinkService;

  private final IdentityManager           identityManager;

  /**
   * Builds the resource.
   *
   * @param calendarLinkService holder of every calendar link rule
   * @param identityManager used to name the creator of a link
   */
  @Autowired
  public AgendaCalendarLinkRest(AgendaCalendarLinkService calendarLinkService, IdentityManager identityManager) {
    this.calendarLinkService = calendarLinkService;
    this.identityManager = identityManager;
  }

  /**
   * Tells whether a calendar has a link, who created it, when, and whether it
   * still answers — never the URL.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return the status of the calendar's link
   */
  @GetMapping("calendars/{calendarId}/link")
  @Secured("users")
  @Operation(summary = "Get the status of a calendar's private link", method = "GET",
             description = "Answers whether the calendar has a link, its creator and creation date, and whether it still"
                 + " answers. The URL is never returned here.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user may not manage the link of this calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public CalendarLinkStatusEntity getCalendarLink(HttpServletRequest request, @PathVariable("calendarId") long calendarId) {
    try {
      return toEntity(calendarId, calendarLinkService.getCalendarLink(calendarId, request.getRemoteUser()), null);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agenda.calendarLink.calendarNotFound");
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "agenda.calendarLink.forbidden");
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Creates the link of a calendar, replacing the one it has: the previous URL
   * stops answering at once. The answer carries the new URL, the only time it
   * is ever returned.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return the status of the new link, with its URL
   */
  @PostMapping("calendars/{calendarId}/link")
  @Secured("users")
  @Operation(summary = "Create or reset a calendar's private link", method = "POST",
             description = "Creates the link, replacing any existing one, and answers its URL once.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Link created"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user may not manage the link of this calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public CalendarLinkStatusEntity saveCalendarLink(HttpServletRequest request, @PathVariable("calendarId") long calendarId) {
    try {
      String token = calendarLinkService.saveCalendarLink(calendarId, request.getRemoteUser());
      CalendarLink link = calendarLinkService.getCalendarLink(calendarId, request.getRemoteUser());
      return toEntity(calendarId, link, feedUrl(request, token));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agenda.calendarLink.calendarNotFound");
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "agenda.calendarLink.forbidden");
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Deletes the link of a calendar.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return no content
   */
  @DeleteMapping("calendars/{calendarId}/link")
  @Secured("users")
  @Operation(summary = "Delete a calendar's private link", method = "DELETE",
             description = "Deletes the link; its URL stops answering at once. Deleting a missing link succeeds.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Link deleted"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user may not manage the link of this calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<Void> deleteCalendarLink(HttpServletRequest request, @PathVariable("calendarId") long calendarId) {
    try {
      calendarLinkService.deleteCalendarLink(calendarId, request.getRemoteUser());
      return ResponseEntity.noContent().build();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agenda.calendarLink.calendarNotFound");
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "agenda.calendarLink.forbidden");
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Serves the calendar a link publishes, to a calendar application with no eXo
   * session. Deliberately not {@code @Secured}: the token is the credential.
   * <p>
   * Every link that opens nothing — unknown, replaced, deleted, or whose creator
   * lost their right — gets the same bodiless 404. The token never reaches a
   * log line or a response.
   *
   * @param token the token presented in the URL
   * @param ifNoneMatch the entity tag the client already holds, if any
   * @return the iCalendar document, a 304 when unchanged, or a 404
   */
  @GetMapping("ical/{token}.ics")
  @Operation(summary = "Get the iCalendar document a private calendar link publishes", method = "GET",
             description = "Anonymous: the token in the path is the credential. Answers text/calendar.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The calendar document"),
      @ApiResponse(responseCode = "304", description = "Unchanged since the entity tag the client holds"),
      @ApiResponse(responseCode = "404", description = "The link opens nothing"),
  })
  public ResponseEntity<byte[]> getCalendarFeed(@PathVariable("token") String token,
                                                @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
                                                String ifNoneMatch) {
    String document;
    try {
      document = calendarLinkService.getCalendarFeed(token);
    } catch (ObjectNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
    } catch (RuntimeException e) {
      // Logged without the request: its path carries the token.
      LOG.warn("A calendar link document could not be rendered", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore()).build();
    }
    byte[] body = document.getBytes(StandardCharsets.UTF_8);
    String entityTag = "\"" + sha256(body) + "\"";
    ResponseEntity.BodyBuilder builder = StringUtils.equals(ifNoneMatch, entityTag) ? ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                                                                    : ResponseEntity.ok();
    builder.eTag(entityTag)
           .cacheControl(CacheControl.maxAge(CalendarFeedIcsWriter.REFRESH_INTERVAL.dividedBy(16)).cachePrivate())
           .header("X-Robots-Tag", "noindex, nofollow")
           .header("Referrer-Policy", "no-referrer")
           .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calendar.ics\"");
    if (StringUtils.equals(ifNoneMatch, entityTag)) {
      return builder.build();
    }
    return builder.contentType(TEXT_CALENDAR).body(body);
  }

  /**
   * Renders a link's status.
   *
   * @param calendarId the calendar asked about
   * @param link the link, null when the calendar has none
   * @param url the link's URL, only right after its creation
   * @return the entity
   */
  private CalendarLinkStatusEntity toEntity(long calendarId, CalendarLink link, String url) {
    if (link == null) {
      return new CalendarLinkStatusEntity(calendarId, false, false, 0, null, 0, null);
    }
    return new CalendarLinkStatusEntity(calendarId,
                                        true,
                                        link.isActive(),
                                        link.getCreatorId(),
                                        creatorName(link.getCreatorId()),
                                        link.getCreatedDate(),
                                        url);
  }

  /**
   * Names the creator of a link.
   *
   * @param creatorId identity identifier of the creator
   * @return the display name, null when it cannot be resolved
   */
  private String creatorName(long creatorId) {
    Identity identity = identityManager.getIdentity(String.valueOf(creatorId));
    return identity == null || identity.getProfile() == null ? null : identity.getProfile().getFullName();
  }

  /**
   * Composes the URL of a link: the deployment's configured domain, else the
   * one the request came in on, then this WAR's context and the feed path.
   *
   * @param request the request that created the link
   * @param token the new token
   * @return the absolute URL
   */
  private String feedUrl(HttpServletRequest request, String token) {
    String domain;
    try {
      domain = CommonsUtils.getCurrentDomain();
    } catch (RuntimeException | LinkageError e) {
      domain = null;
    }
    if (StringUtils.isBlank(domain)) {
      domain = request.getScheme() + "://" + request.getServerName()
          + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
    }
    return StringUtils.removeEnd(domain, "/") + request.getContextPath() + FEED_PATH + token + FEED_EXTENSION;
  }

  /**
   * Digests a response body for its entity tag.
   *
   * @param body the body
   * @return lowercase hexadecimal SHA-256
   */
  private static String sha256(byte[] body) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available on this JVM", e);
    }
  }

}
