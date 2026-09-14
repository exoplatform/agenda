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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionRequestEntity;
import org.exoplatform.agenda.rest.model.CalendarSubscriptionStatusEntity;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionServiceImpl;
import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Manages the user's subscriptions to calendar links (EXO-90278).
 * <p>
 * <b>No rule lives here.</b> Who may read or change a subscription, which links
 * may be read and what is imported are {@link AgendaCalendarSubscriptionService}'s;
 * this resource maps its exceptions to statuses: 404 for a subscription that
 * does not exist, 403 for one that is not the user's, 400 with the refusal's
 * message code, 429 for a refresh asked a moment ago and 409 for one already
 * running. The asking user comes from the session, never from a parameter.
 * Responses carry URLs, which may embed a secret, and are never cached.
 */
@RestController
@Tag(name = "calendar-subscription", description = "Subscriptions to calendar links (iCal / webcal)")
public class AgendaCalendarSubscriptionRest {

  private static final String                     FORBIDDEN = "agenda.calendarSubscription.forbidden";

  private static final String                     NOT_FOUND = "agenda.calendarSubscription.notFound";

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
   * Lists the user's subscriptions.
   *
   * @param request the request
   * @return the subscriptions
   */
  @GetMapping("calendars/subscriptions")
  @Secured("users")
  @Operation(summary = "List the user's calendar subscriptions", method = "GET")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<List<CalendarSubscriptionStatusEntity>> getSubscriptions(HttpServletRequest request) {
    try {
      return uncached(subscriptionService.getSubscriptions(request.getRemoteUser()).stream().map(this::toEntity).toList());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
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
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<Map<String, String>> checkUrl(HttpServletRequest request,
                                                      @RequestBody CalendarSubscriptionRequestEntity body) {
    try {
      String name = subscriptionService.checkUrl(body == null ? null : body.getUrl(), request.getRemoteUser());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Collections.singletonMap("name", name));
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw badRequest(e);
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
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<CalendarSubscriptionStatusEntity> createSubscription(HttpServletRequest request,
                                                                             @RequestBody CalendarSubscriptionRequestEntity body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agenda.calendarSubscription.invalidUrl");
    }
    try {
      return uncached(toEntity(subscriptionService.createSubscription(body.getUrl(),
                                                                      body.getName(),
                                                                      body.getColor(),
                                                                      request.getRemoteUser())));
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw badRequest(e);
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
  public ResponseEntity<CalendarSubscriptionStatusEntity> updateSubscription(HttpServletRequest request,
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
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw badRequest(e);
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
  public ResponseEntity<CalendarSubscriptionStatusEntity> refreshSubscription(HttpServletRequest request,
                                                                              @PathVariable("subscriptionId") long subscriptionId) {
    try {
      return uncached(toEntity(subscriptionService.refreshSubscription(subscriptionId, request.getRemoteUser())));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalStateException e) {
      if (AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON.equals(e.getMessage())) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
      }
      if (AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS.equals(e.getMessage())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
      }
      throw e;
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
  public ResponseEntity<Void> deleteSubscription(HttpServletRequest request, @PathVariable("subscriptionId") long subscriptionId) {
    try {
      subscriptionService.deleteSubscription(subscriptionId, request.getRemoteUser());
      return ResponseEntity.noContent().build();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * A 400 carrying a refusal's message code, never anything else: an unexpected
   * message could carry a URL.
   *
   * @param e the refusal
   * @return the exception to throw
   */
  private ResponseStatusException badRequest(IllegalArgumentException e) {
    String code = StringUtils.startsWith(e.getMessage(), "agenda.") ? e.getMessage() : "agenda.calendarSubscription.invalidRequest";
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
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
