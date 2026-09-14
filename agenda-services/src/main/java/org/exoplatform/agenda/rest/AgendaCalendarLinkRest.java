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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.agenda.rest.model.CalendarLinkStatusEntity;
import org.exoplatform.agenda.service.AgendaCalendarLinkService;
import org.exoplatform.agenda.service.AgendaCalendarService;
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
  public static final String              FEED_PATH      = "/rest/ical/";

  /** Extension of the feed URL: many calendar applications expect it. */
  public static final String              FEED_EXTENSION = ".ics";

  /** The kind of a calendar a user owns. */
  public static final String              PERSONAL       = "PERSONAL";

  /** The kind of a calendar a space owns. */
  public static final String              SPACE          = "SPACE";

  private static final Log                LOG            = ExoLogger.getLogger(AgendaCalendarLinkRest.class);

  private static final MediaType          TEXT_CALENDAR  = new MediaType("text", "calendar", StandardCharsets.UTF_8);

  private static final String             FORBIDDEN      = "agenda.calendarLink.forbidden";

  private static final String             NOT_FOUND      = "agenda.calendarLink.calendarNotFound";

  private final AgendaCalendarLinkService calendarLinkService;

  private final AgendaCalendarService     agendaCalendarService;

  private final IdentityManager           identityManager;

  /**
   * Builds the resource.
   *
   * @param calendarLinkService holder of every calendar link rule
   * @param agendaCalendarService reads the calendars a listing names
   * @param identityManager used to name the creator of a link and the owner of
   *          a calendar
   */
  @Autowired
  public AgendaCalendarLinkRest(AgendaCalendarLinkService calendarLinkService,
                                AgendaCalendarService agendaCalendarService,
                                IdentityManager identityManager) {
    this.calendarLinkService = calendarLinkService;
    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
  }

  /**
   * Lists the link of every calendar the user may manage one for and that has
   * one, working or stopped — so a page draws every calendar's publishing state
   * with one request. Never cached: an entry can carry a working capability URL.
   *
   * @param request the authenticated request
   * @return the links, each with its calendar's title and kind
   */
  @GetMapping("calendars/links")
  @Secured("users")
  @Operation(summary = "List the private links of the calendars the user manages", method = "GET",
             description = "Answers one entry per calendar that has a link, among the user's own calendars and the calendars"
                 + " of the spaces they manage, with the same status as the per-calendar read plus the calendar's title and"
                 + " kind.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<List<CalendarLinkStatusEntity>> getCalendarLinks(HttpServletRequest request) {
    try {
      Map<String, Identity> identities = new HashMap<>();
      List<CalendarLinkStatusEntity> entities = calendarLinkService.getCalendarLinks(request.getRemoteUser())
                                                                   .stream()
                                                                   .map(link -> listed(request, link, identities))
                                                                   .toList();
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entities);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * Tells whether a calendar has a link, who created it, when, whether it still
   * answers, and — while it answers and can be displayed — its URL. Only someone
   * allowed to manage the link gets an answer at all, and the answer is never
   * cached: its body can carry a working capability URL, and the portal's
   * security chain writes no cache header of its own.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return the status of the calendar's link
   */
  @GetMapping("calendars/{calendarId}/link")
  @Secured("users")
  @Operation(summary = "Get the status of a calendar's private link", method = "GET",
             description = "Answers whether the calendar has a link, its creator and creation date, whether it still"
                 + " answers, and its URL while it answers and can be displayed. Owner or space managers only.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user may not manage the link of this calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<CalendarLinkStatusEntity> getCalendarLink(HttpServletRequest request,
                                                                  @PathVariable("calendarId") long calendarId) {
    try {
      return uncached(toEntity(request,
                               calendarId,
                               calendarLinkService.getCalendarLink(calendarId, request.getRemoteUser()),
                               new HashMap<>()));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Creates the link of a calendar, replacing the one it has: the previous URL
   * stops answering at once. The answer carries the new URL, and is never
   * cached.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return the status of the new link, with its URL
   */
  @PostMapping("calendars/{calendarId}/link")
  @Secured("users")
  @Operation(summary = "Create or reset a calendar's private link", method = "POST",
             description = "Creates the link, replacing any existing one, and answers its URL.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Link created"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user may not manage the link of this calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<CalendarLinkStatusEntity> saveCalendarLink(HttpServletRequest request,
                                                                   @PathVariable("calendarId") long calendarId) {
    try {
      calendarLinkService.saveCalendarLink(calendarId, request.getRemoteUser());
      return uncached(toEntity(request,
                               calendarId,
                               calendarLinkService.getCalendarLink(calendarId, request.getRemoteUser()),
                               new HashMap<>()));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
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
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
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
   * <p>
   * The 304 is Spring MVC's, not this method's: for a GET answering an
   * {@code ETag}, {@code HttpEntityMethodProcessor} compares it with the
   * request's {@code If-None-Match} and drops the body itself.
   * {@code AgendaCalendarLinkRestTest} pins the behaviour through the dispatcher.
   *
   * @param token the token presented in the URL
   * @return the iCalendar document, or a 404
   */
  @GetMapping("ical/{token}.ics")
  @Operation(summary = "Get the iCalendar document a private calendar link publishes", method = "GET",
             description = "Anonymous: the token in the path is the credential. Answers text/calendar.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The calendar document"),
      @ApiResponse(responseCode = "304", description = "Unchanged since the entity tag the client holds"),
      @ApiResponse(responseCode = "404", description = "The link opens nothing"),
  })
  public ResponseEntity<byte[]> getCalendarFeed(@PathVariable("token") String token) {
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
    return ResponseEntity.ok()
                         .eTag(entityTag)
                         .cacheControl(CacheControl.maxAge(CalendarFeedIcsWriter.REFRESH_INTERVAL.dividedBy(16)).cachePrivate())
                         .header("X-Robots-Tag", "noindex, nofollow")
                         .header("Referrer-Policy", "no-referrer")
                         .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calendar.ics\"")
                         .contentType(TEXT_CALENDAR)
                         .body(body);
  }

  /**
   * Answers a link's status with {@code Cache-Control: no-store}, so that a URL
   * it carries stays out of browser and intermediary caches.
   *
   * @param entity the status
   * @return the response
   */
  private ResponseEntity<CalendarLinkStatusEntity> uncached(CalendarLinkStatusEntity entity) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entity);
  }

  /**
   * Renders a listed link: its status, and the title and kind of its calendar.
   *
   * @param request the request, whose context the URL is built on
   * @param link the link, read by the service for a manager
   * @param identities identities already read during this request
   * @return the entity
   */
  private CalendarLinkStatusEntity listed(HttpServletRequest request, CalendarLink link, Map<String, Identity> identities) {
    CalendarLinkStatusEntity entity = toEntity(request, link.getCalendarId(), link, identities);
    Calendar calendar = agendaCalendarService.getCalendarById(link.getCalendarId());
    if (calendar == null) {
      return entity;
    }
    entity.setCalendarTitle(StringUtils.isNotBlank(calendar.getName()) ? calendar.getName() : calendar.getTitle());
    entity.setSystemCalendar(calendar.isSystem() && StringUtils.isBlank(calendar.getName()));
    Identity owner = identity(String.valueOf(calendar.getOwnerId()), identities);
    if (owner != null && owner.isSpace()) {
      entity.setCalendarKind(SPACE);
      entity.setSpaceDisplayName(displayName(owner.getId(), identities));
    } else {
      entity.setCalendarKind(PERSONAL);
    }
    return entity;
  }

  /**
   * Renders a link's status. The URL is composed only from the token the
   * service hands back, which it does only for an answering link it could
   * decrypt, to someone allowed to manage it.
   *
   * @param request the request, whose context the URL is built on
   * @param calendarId the calendar asked about
   * @param link the link, null when the calendar has none
   * @param identities identities already read during this request
   * @return the entity
   */
  private CalendarLinkStatusEntity toEntity(HttpServletRequest request,
                                            long calendarId,
                                            CalendarLink link,
                                            Map<String, Identity> identities) {
    CalendarLinkStatusEntity entity = new CalendarLinkStatusEntity();
    entity.setCalendarId(calendarId);
    if (link == null) {
      return entity;
    }
    String url = link.isActive() && StringUtils.isNotBlank(link.getToken()) ? feedUrl(request, link.getToken()) : null;
    entity.setExists(true);
    entity.setActive(link.isActive());
    entity.setDisplayable(url != null);
    entity.setCreatorId(link.getCreatorId());
    entity.setCreatorName(displayName(String.valueOf(link.getCreatorId()), identities));
    entity.setCreatedDate(link.getCreatedDate());
    entity.setUrl(url);
    return entity;
  }

  /**
   * Names an identity — a user's full name, a space's display name.
   *
   * @param identityId identity identifier
   * @param identities identities already read during this request
   * @return the display name, null when it cannot be resolved
   */
  private String displayName(String identityId, Map<String, Identity> identities) {
    Identity identity = identity(identityId, identities);
    return identity == null || identity.getProfile() == null ? null : identity.getProfile().getFullName();
  }

  /**
   * Reads an identity once per request, however many links name it — as
   * creator, as owner, or both.
   *
   * @param identityId identity identifier
   * @param identities identities already read during this request
   * @return the identity, null when it does not exist
   */
  private Identity identity(String identityId, Map<String, Identity> identities) {
    if (!identities.containsKey(identityId)) {
      identities.put(identityId, identityManager.getIdentity(identityId));
    }
    return identities.get(identityId);
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
