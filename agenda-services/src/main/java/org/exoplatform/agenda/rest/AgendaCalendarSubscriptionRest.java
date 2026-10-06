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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionRequestEntity;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionStatusEntity;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionServiceImpl;
import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Manages subscriptions to calendar links: the user's own (EXO-90278), and a
 * space's for its managers, named by {@code ownerId} (EXO-90373).
 * <p>
 * <b>No rule lives here.</b> Who may read or change a subscription, which links
 * may be read and what is imported are {@link AgendaCalendarSubscriptionService}'s;
 * this resource maps its exceptions to statuses: 404 for a subscription or an
 * owner that does not exist, 403 for one the user may not manage, 400 with the refusal's
 * message code, 429 for a refresh asked a moment ago or too many reads at once,
 * and 409 for a refresh already running. The asking user comes from the session,
 * never from a parameter. Responses carry URLs, which may embed a secret, and
 * are never cached.
 * <p>
 * <b>A refusal writes its own body</b>, {@code {"message": "<code>"}}: it is not
 * left to Spring's error page, whose message the platform does not include
 * ({@code server.error.include-message} is no longer bound by Spring Boot 4), so
 * the drawer would only ever see a generic error.
 */
@RestController
@Tag(name = "calendar-subscription", description = "Subscriptions to calendar links (iCal / webcal)")
public class AgendaCalendarSubscriptionRest {

  private static final String                     FORBIDDEN = "agenda.calendarSubscription.forbidden";

  private static final String                     NOT_FOUND = "agenda.calendarSubscription.notFound";

  private static final String                     OWNER_NOT_FOUND = "agenda.calendarSubscription.ownerNotFound";

  private final AgendaCalendarSubscriptionService subscriptionService;

  /**
   * Builds the resource.
   *
   * @param subscriptionService holder of every subscription rule
   */
  @Autowired
  public AgendaCalendarSubscriptionRest(AgendaCalendarSubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  /**
   * Lists the subscriptions of an owner: the user's own when none is named, a
   * space's for one of its managers otherwise.
   *
   * @param request the request
   * @param ownerId identity identifier of the owner, or null for the user
   * @return the subscriptions
   */
  @GetMapping("calendars/subscriptions")
  @Secured("users")
  @Operation(summary = "List the calendar subscriptions of the user, or of a space the user manages", method = "GET")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity, or does not manage the owner"),
      @ApiResponse(responseCode = "404", description = "The owner does not exist"),
  })
  public ResponseEntity<?> getSubscriptions(HttpServletRequest request,
                                            @Parameter(description = "Identity identifier of the space, none for the user's own")
                                            @RequestParam(name = "ownerId", required = false)
                                            Long ownerId) {
    try {
      List<CalendarSubscription> subscriptions = ownerId == null ? subscriptionService.getSubscriptions(request.getRemoteUser())
                                                                 : subscriptionService.getSubscriptions(ownerId,
                                                                                                        request.getRemoteUser());
      return uncached(subscriptions.stream().map(this::toEntity).toList());
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, OWNER_NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * Checks that a link can be subscribed to, and answers the calendar's own name.
   *
   * @param request the request
   * @param body the link
   * @return {@code {"name": ...}}, the name null when the calendar names none
   */
  @PostMapping("calendars/subscriptions/check")
  @Secured("users")
  @Operation(summary = "Check a calendar link", method = "POST",
             description = "Reads the link once, stores nothing, and answers the calendar's own name.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "The link serves a calendar"),
      @ApiResponse(responseCode = "400", description = "The link is refused or serves no calendar; the body carries the reason code"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity, or does not manage the owner"),
      @ApiResponse(responseCode = "404", description = "The owner does not exist"),
  })
  public ResponseEntity<Map<String, String>> checkUrl(HttpServletRequest request,
                                                      @RequestBody CalendarSubscriptionRequestEntity body) {
    try {
      String url = body == null ? null : body.getUrl();
      Long ownerId = body == null ? null : body.getOwnerId();
      String name = ownerId == null ? subscriptionService.checkUrl(url, request.getRemoteUser())
                                    : subscriptionService.checkUrl(url, ownerId, request.getRemoteUser());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Collections.singletonMap("name", name));
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, OWNER_NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      return badRequest(e);
    } catch (IllegalStateException e) {
      return refused(e);
    }
  }

  /**
   * Subscribes the user to a link.
   *
   * @param request the request
   * @param body the link, the name and the colour
   * @return the subscription
   */
  @PostMapping("calendars/subscriptions")
  @Secured("users")
  @Operation(summary = "Subscribe to a calendar link", method = "POST",
             description = "Reads the link, creates a read-only calendar and imports its events.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Subscribed"),
      @ApiResponse(responseCode = "400", description = "The link is refused, already subscribed or serves no calendar"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity, or does not manage the owner"),
      @ApiResponse(responseCode = "404", description = "The owner does not exist"),
  })
  public ResponseEntity<?> createSubscription(HttpServletRequest request,
                                                                             @RequestBody CalendarSubscriptionRequestEntity body) {
    if (body == null) {
      return refusal(HttpStatus.BAD_REQUEST, "agenda.calendarSubscription.invalidUrl");
    }
    try {
      CalendarSubscription created = body.getOwnerId() == null ? subscriptionService.createSubscription(body.getUrl(),
                                                                                                        body.getName(),
                                                                                                        body.getColor(),
                                                                                                        request.getRemoteUser())
                                                               : subscriptionService.createSubscription(body.getUrl(),
                                                                                                        body.getName(),
                                                                                                        body.getColor(),
                                                                                                        body.getOwnerId(),
                                                                                                        request.getRemoteUser());
      return uncached(toEntity(created));
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, OWNER_NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      return badRequest(e);
    } catch (IllegalStateException e) {
      return refused(e);
    }
  }

  /**
   * Changes the name, the colour or the link of a subscription.
   *
   * @param request the request
   * @param subscriptionId technical identifier of the subscription
   * @param body the fields to change, blank ones kept
   * @return the subscription as it now stands
   */
  @PutMapping("calendars/subscriptions/{subscriptionId}")
  @Secured("users")
  @Operation(summary = "Change a calendar subscription", method = "PUT")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Changed"),
      @ApiResponse(responseCode = "400", description = "A value is refused; the body carries the reason code"),
      @ApiResponse(responseCode = "403", description = "Not the user's subscription"),
      @ApiResponse(responseCode = "404", description = "Subscription not found"),
  })
  public ResponseEntity<?> updateSubscription(HttpServletRequest request,
                                                                             @PathVariable("subscriptionId") long subscriptionId,
                                                                             @RequestBody CalendarSubscriptionRequestEntity body) {
    CalendarSubscriptionRequestEntity values = body == null ? new CalendarSubscriptionRequestEntity() : body;
    try {
      return uncached(toEntity(subscriptionService.updateSubscription(subscriptionId,
                                                                      values.getUrl(),
                                                                      values.getName(),
                                                                      values.getColor(),
                                                                      request.getRemoteUser())));
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      return badRequest(e);
    } catch (IllegalStateException e) {
      return refused(e);
    }
  }

  /**
   * Refreshes a subscription now.
   *
   * @param request the request
   * @param subscriptionId technical identifier of the subscription
   * @return the subscription after the refresh, a failure recorded on it
   */
  @PostMapping("calendars/subscriptions/{subscriptionId}/refresh")
  @Secured("users")
  @Operation(summary = "Refresh a calendar subscription now", method = "POST",
             description = "A failure of the link is not an error of the request: it is answered as the subscription's last error.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Refreshed, or its failure recorded"),
      @ApiResponse(responseCode = "403", description = "Not the user's subscription"),
      @ApiResponse(responseCode = "404", description = "Subscription not found"),
      @ApiResponse(responseCode = "409", description = "A refresh is running"),
      @ApiResponse(responseCode = "429", description = "Refreshed a moment ago"),
  })
  public ResponseEntity<?> refreshSubscription(HttpServletRequest request,
                                                                              @PathVariable("subscriptionId") long subscriptionId) {
    try {
      return uncached(toEntity(subscriptionService.refreshSubscription(subscriptionId, request.getRemoteUser())));
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalStateException e) {
      return refused(e);
    }
  }

  /**
   * Unsubscribes: removes the subscription, its calendar and its events.
   *
   * @param request the request
   * @param subscriptionId technical identifier of the subscription
   * @return no content
   */
  @DeleteMapping("calendars/subscriptions/{subscriptionId}")
  @Secured("users")
  @Operation(summary = "Unsubscribe from a calendar link", method = "DELETE")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Unsubscribed"),
      @ApiResponse(responseCode = "403", description = "Not the user's subscription"),
      @ApiResponse(responseCode = "404", description = "Subscription not found"),
  })
  public ResponseEntity<?> deleteSubscription(HttpServletRequest request, @PathVariable("subscriptionId") long subscriptionId) {
    try {
      subscriptionService.deleteSubscription(subscriptionId, request.getRemoteUser());
      return ResponseEntity.noContent().build();
    } catch (ObjectNotFoundException e) {
      return refusal(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      return refusal(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * The answer to a refusal the service answers as a state: 429 for a refresh
   * asked a moment ago or a user already reading as many links as allowed, 409
   * for a refresh already running.
   *
   * @param e the state
   * @return the refusal
   * @throws IllegalStateException the state itself, when it is not a refusal
   */
  private static ResponseEntity<Map<String, String>> refused(IllegalStateException e) {
    if (AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON.equals(e.getMessage())
        || AgendaCalendarSubscriptionServiceImpl.TOO_MANY_READS.equals(e.getMessage())) {
      return refusal(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
    if (AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS.equals(e.getMessage())) {
      return refusal(HttpStatus.CONFLICT, e.getMessage());
    }
    throw e;
  }

  /**
   * A 400 carrying a refusal's message code, never anything else: an unexpected
   * message could carry a URL.
   *
   * @param e the refusal
   * @return the refusal
   */
  private static ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    String code = StringUtils.startsWith(e.getMessage(), "agenda.") ? e.getMessage() : "agenda.calendarSubscription.invalidRequest";
    return refusal(HttpStatus.BAD_REQUEST, code);
  }

  /**
   * A refusal with its code written in the body, {@code {"message": code}}, and
   * never cached.
   *
   * @param status the status
   * @param code the message code
   * @return the response
   */
  private static ResponseEntity<Map<String, String>> refusal(HttpStatus status, String code) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", code));
  }

  /**
   * A response that is never cached.
   *
   * @param <T> the body type
   * @param body the body
   * @return the response
   */
  private static <T> ResponseEntity<T> uncached(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  /**
   * Maps a subscription.
   *
   * @param subscription the subscription as the service returned it
   * @return the entity
   */
  private CalendarSubscriptionStatusEntity toEntity(CalendarSubscription subscription) {
    CalendarSubscriptionStatusEntity entity = new CalendarSubscriptionStatusEntity();
    entity.setId(subscription.getId());
    entity.setCalendarId(subscription.getCalendarId());
    entity.setOwnerId(subscription.getOwnerIdentityId());
    entity.setCreatorId(subscription.getUserIdentityId());
    entity.setCreatorUsername(subscription.getCreatorUsername());
    entity.setCreatorFullName(subscription.getCreatorFullName());
    entity.setName(subscription.getName());
    entity.setColor(subscription.getColor());
    entity.setUrl(subscription.getUrl());
    entity.setLastSuccessDate(subscription.getLastSuccessDate());
    entity.setLastAttemptDate(subscription.getLastAttemptDate());
    entity.setNextRefreshDate(subscription.getNextRefreshDate());
    entity.setLastError(subscription.getLastError());
    entity.setTruncated(subscription.isTruncated());
    entity.setCreatedDate(subscription.getCreatedDate());
    return entity;
  }

}
