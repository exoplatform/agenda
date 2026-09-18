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

import java.util.HashMap;
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

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ChannelShares;
import org.exoplatform.agenda.rest.model.CalendarShareeEntity;
import org.exoplatform.agenda.rest.model.SharedCalendarEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaCalendarShareService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Sharing a personal calendar with colleagues, and reading the calendars
 * shared with oneself (EXO-90357).
 * <p>
 * <b>No rule lives here.</b> Who may share, with whom, and what a sharee reads
 * are {@link AgendaCalendarShareService}'s; this resource maps its exceptions
 * to statuses — not found, then forbidden, then bad request — and names the
 * people its answers carry. The asking user comes from the session, never from
 * a parameter.
 */
@RestController
@Tag(name = "calendar-share", description = "Personal calendars shared with colleagues")
public class AgendaCalendarShareRest {

  private static final String              FORBIDDEN = "agenda.share.forbidden";

  private static final String              NOT_FOUND = "agenda.share.calendarNotFound";

  private final AgendaCalendarShareService calendarShareService;

  private final AgendaCalendarService      agendaCalendarService;

  private final IdentityManager            identityManager;

  /**
   * Builds the resource.
   *
   * @param calendarShareService holder of every sharing rule
   * @param agendaCalendarService reads the calendars a listing names
   * @param identityManager names the people an answer carries
   */
  @Autowired
  public AgendaCalendarShareRest(AgendaCalendarShareService calendarShareService,
                                 AgendaCalendarService agendaCalendarService,
                                 IdentityManager identityManager) {
    this.calendarShareService = calendarShareService;
    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
  }

  /**
   * The colleagues a calendar is shared with, and the shares of it that exist
   * on a channel's server without an eXo record.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @return the shares under {@code shares}, the external ones under
   *         {@code externalShares}
   */
  @GetMapping("calendars/{calendarId}/shares")
  @Secured("users")
  @Operation(summary = "List the colleagues a calendar is shared with", method = "GET",
             description = "Owner only. Answers the eXo shares, each naming the colleague and the channel carrying it,"
                 + " the shares a channel's server holds that eXo does not record, and whether the calendar receives copies"
                 + " of the owner's eXo meetings, which a share would expose.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user does not own the calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<Map<String, Object>> getShares(HttpServletRequest request,
                                                       @PathVariable("calendarId") long calendarId) {
    try {
      Map<String, Identity> identities = new HashMap<>();
      // The channels first, and once (EXO-90385): a read-only grant they hold
      // for a colleague is recorded as a share on the way, and the list of
      // shares must hold it. The same ask answers whether sharing exposes the
      // owner's eXo meetings (EXO-90345), which the drawer asks a confirmation
      // for — asking it separately made every channel talk to its server twice
      ChannelShares channelShares = calendarShareService.getChannelShares(calendarId, request.getRemoteUser());
      List<CalendarShareeEntity> shares = calendarShareService.getShares(calendarId, request.getRemoteUser())
                                                              .stream()
                                                              .map(share -> toShareeEntity(share, identities))
                                                              .toList();
      Map<String, Object> body = new HashMap<>();
      body.put("shares", shares);
      body.put("externalShares", channelShares.shares());
      body.put("meetingCopies", channelShares.meetingCopies());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Shares a calendar with a colleague.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @param body {@code {"username": "..."}}
   * @return the share, naming the channel that also carries it when one does
   */
  @PostMapping("calendars/{calendarId}/shares")
  @Secured("users")
  @Operation(summary = "Share a calendar with a colleague", method = "POST",
             description = "Owner only. Records the share in eXo, then asks every delivery channel to carry it; the record"
                 + " stands whatever the channels answer. A failed delivery is logged server-side and leaves the share"
                 + " eXo-only; the answer carries no warning.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Calendar shared"),
      @ApiResponse(responseCode = "400", description = "Unknown, disabled or self sharee, or invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user does not own the calendar, or it cannot be shared"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<CalendarShareeEntity> share(HttpServletRequest request,
                                                    @PathVariable("calendarId") long calendarId,
                                                    @RequestBody Map<String, String> body) {
    try {
      CalendarShare share = calendarShareService.share(calendarId,
                                                       body == null ? null : body.get("username"),
                                                       CalendarShareLevel.of(body == null ? null : body.get("access")),
                                                       request.getRemoteUser());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toShareeEntity(share, new HashMap<>()));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Changes what a colleague may do with a calendar shared with them
   * (EXO-90378).
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param body {@code {"access": "VIEW"|"EDIT"}}
   * @return no content
   */
  @PutMapping("calendars/{calendarId}/shares/{shareeIdentityId}")
  @Secured("users")
  @Operation(summary = "Change what a colleague may do with a shared calendar", method = "PUT",
             description = "Owner only. VIEW lets the colleague read the calendar, EDIT lets them create, change and"
                 + " delete events in it and nothing more — never move an event out of it, rename, publish, share on or"
                 + " level anyone. The channel carrying the share is asked to match its grant to the new level; a channel"
                 + " that cannot is logged server-side and the eXo level stands, which is what decides every right in eXo."
                 + " Downgrading moves nothing: the events the colleague created stay in the owner's calendar.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Level changed"),
      @ApiResponse(responseCode = "400", description = "Missing or unknown access level, or invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user does not own the calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found, or not shared with that colleague"),
  })
  public ResponseEntity<Void> setLevel(HttpServletRequest request,
                                       @PathVariable("calendarId") long calendarId,
                                       @PathVariable("shareeIdentityId") long shareeIdentityId,
                                       @RequestBody Map<String, String> body) {
    String access = body == null ? null : body.get("access");
    // The level is named, never inferred: an absent or unknown name is a bad
    // request, not a silent VIEW, so that a client typo can never look like a
    // deliberate downgrade
    CalendarShareLevel level = level(access);
    if (level == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AgendaCalendarShareService.LEVEL_MANDATORY);
    }
    try {
      calendarShareService.setLevel(calendarId, shareeIdentityId, level, request.getRemoteUser());
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
   * The level a request names, or null when it names none this version knows.
   * Unlike {@link CalendarShareLevel#of(String)}, which reads an unknown stored
   * value as the narrower level, a request that cannot be understood is
   * refused rather than narrowed.
   *
   * @param access the level as the client wrote it, may be null
   * @return the level, or null when the name is blank or unknown
   */
  private CalendarShareLevel level(String access) {
    if (StringUtils.isBlank(access)) {
      return null;
    }
    for (CalendarShareLevel candidate : CalendarShareLevel.values()) {
      if (candidate.name().equalsIgnoreCase(access.trim())) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Stops sharing a calendar with a colleague.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @return no content
   */
  @DeleteMapping("calendars/{calendarId}/shares/{shareeIdentityId}")
  @Secured("users")
  @Operation(summary = "Stop sharing a calendar with a colleague", method = "DELETE",
             description = "Owner only. Withdraws the share from the channel carrying it, then deletes the record; the"
                 + " record goes even when the channel could not withdraw. Unsharing a colleague without a share succeeds.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Share deleted"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier"),
      @ApiResponse(responseCode = "403", description = "The user does not own the calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar not found"),
  })
  public ResponseEntity<Void> unshare(HttpServletRequest request,
                                      @PathVariable("calendarId") long calendarId,
                                      @PathVariable("shareeIdentityId") long shareeIdentityId) {
    try {
      calendarShareService.unshare(calendarId, shareeIdentityId, request.getRemoteUser());
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
   * Removes a share that exists on a channel's server without an eXo record.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @param channelId the channel that listed the share
   * @param externalId the channel's identifier of the share
   * @return no content
   */
  @DeleteMapping("calendars/{calendarId}/external-shares/{channelId}/{externalId}")
  @Secured("users")
  @Operation(summary = "Remove a share held on a channel's server", method = "DELETE",
             description = "Owner only. The channel removes the grant on its server; a server that refuses, or cannot be"
                 + " reached, answers 409 with the code agenda.share.notRemoved and the grant stands where it was.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Share removed"),
      @ApiResponse(responseCode = "400", description = "Invalid calendar identifier or missing share identifier"),
      @ApiResponse(responseCode = "403", description = "The user does not own the calendar"),
      @ApiResponse(responseCode = "404", description = "Calendar or channel not found"),
      @ApiResponse(responseCode = "409", description = "The channel could not remove the share on its server"),
  })
  public ResponseEntity<Void> removeExternalShare(HttpServletRequest request,
                                                  @PathVariable("calendarId") long calendarId,
                                                  @PathVariable("channelId") String channelId,
                                                  @PathVariable("externalId") String externalId) {
    try {
      calendarShareService.removeExternalShare(calendarId, channelId, externalId, request.getRemoteUser());
      return ResponseEntity.noContent().build();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalStateException e) {
      // The server holding the grant is what stands in the way, not the
      // request: a conflict with its state, named so the drawer can word it
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  /**
   * How many colleagues each of the user's calendars is shared with: what
   * draws the shared sign on the personal calendar rows with one request.
   *
   * @param request the authenticated request
   * @return sharee counts by calendar identifier, absent when zero
   */
  @GetMapping("calendars/share-counts")
  @Secured("users")
  @Operation(summary = "Count the colleagues each own calendar is shared with", method = "GET",
             description = "Answers, for the user's own calendars, how many colleagues each is shared with; a calendar"
                 + " shared with nobody is absent.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<Map<Long, Long>> getShareCounts(HttpServletRequest request) {
    try {
      return ResponseEntity.ok()
                           .cacheControl(CacheControl.noStore())
                           .body(calendarShareService.countShareesByCalendar(request.getRemoteUser()));
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * The calendars shared with the user, hidden ones included.
   *
   * @param request the authenticated request
   * @return the calendars, newest share first
   */
  @GetMapping("calendars/shared-with-me")
  @Secured("users")
  @Operation(summary = "List the calendars shared with the user", method = "GET",
             description = "Answers each calendar with its owner and colour, whether the user hid it, and the channel also"
                 + " carrying it, so a client can leave out what that channel already lists.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
  })
  public ResponseEntity<List<SharedCalendarEntity>> getSharedWithMe(HttpServletRequest request) {
    try {
      Map<String, Identity> identities = new HashMap<>();
      List<SharedCalendarEntity> entities = calendarShareService.getSharedWithMe(request.getRemoteUser())
                                                                .stream()
                                                                .map(share -> toSharedCalendarEntity(share, identities))
                                                                .filter(entity -> entity != null)
                                                                .toList();
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entities);
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * Hides, or shows again, a calendar shared with the user.
   *
   * @param request the authenticated request
   * @param calendarId technical identifier of the calendar
   * @param body {@code {"hidden": true|false}}
   * @return no content
   */
  @PutMapping("calendars/shared-with-me/{calendarId}/hidden")
  @Secured("users")
  @Operation(summary = "Hide or show again a calendar shared with the user", method = "PUT",
             description = "The sharee's own choice: the share is never deleted, and the calendar is shown again from the"
                 + " Hidden calendars of the agenda settings.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Choice recorded"),
      @ApiResponse(responseCode = "400", description = "Missing hidden flag"),
      @ApiResponse(responseCode = "403", description = "The user has no usable identity"),
      @ApiResponse(responseCode = "404", description = "The calendar is not shared with the user"),
  })
  public ResponseEntity<Void> setHidden(HttpServletRequest request,
                                        @PathVariable("calendarId") long calendarId,
                                        @RequestBody Map<String, Object> body) {
    Object hidden = body == null ? null : body.get("hidden");
    if (!(hidden instanceof Boolean)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agenda.share.hiddenMandatory");
    }
    try {
      calendarShareService.setHidden(calendarId, request.getRemoteUser(), (Boolean) hidden);
      return ResponseEntity.noContent().build();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
  }

  /**
   * Renders a share for its owner, naming the colleague.
   *
   * @param share the share
   * @param identities identities already read during this request
   * @return the entity
   */
  private CalendarShareeEntity toShareeEntity(CalendarShare share, Map<String, Identity> identities) {
    CalendarShareeEntity entity = new CalendarShareeEntity();
    entity.setCalendarId(share.getCalendarId());
    entity.setShareeIdentityId(share.getShareeIdentityId());
    entity.setCreatedDate(share.getCreatedDate());
    entity.setSource(share.getSource() == null ? null : share.getSource().name());
    entity.setAccess(share.getLevel().name());
    entity.setDeliveredTo(share.getDeliveredTo());
    Identity sharee = identity(String.valueOf(share.getShareeIdentityId()), identities);
    if (sharee != null) {
      entity.setUsername(sharee.getRemoteId());
      entity.setDisplayName(displayName(sharee));
      entity.setAvatarUrl(avatarUrl(sharee));
      entity.setDisabled(sharee.isDeleted() || !sharee.isEnable());
    } else {
      entity.setDisabled(true);
    }
    return entity;
  }

  /**
   * Renders a share for its sharee, naming the calendar and its owner.
   *
   * @param share the share
   * @param identities identities already read during this request
   * @return the entity, or null when the calendar cannot be read any more
   */
  private SharedCalendarEntity toSharedCalendarEntity(CalendarShare share, Map<String, Identity> identities) {
    Calendar calendar = agendaCalendarService.getCalendarById(share.getCalendarId());
    if (calendar == null || calendar.isDeleted()) {
      return null;
    }
    SharedCalendarEntity entity = new SharedCalendarEntity();
    entity.setCalendarId(calendar.getId());
    entity.setName(StringUtils.isNotBlank(calendar.getName()) ? calendar.getName() : calendar.getTitle());
    entity.setDescription(calendar.getDescription());
    entity.setColor(calendar.getColor());
    entity.setOwnerId(calendar.getOwnerId());
    entity.setSharedDate(share.getCreatedDate());
    entity.setAccess(share.getLevel().name());
    entity.setDeliveredTo(share.getDeliveredTo());
    entity.setDeliveryRef(share.getDeliveryRef());
    entity.setHidden(share.isHidden());
    Identity owner = identity(String.valueOf(calendar.getOwnerId()), identities);
    if (owner != null) {
      entity.setOwnerUsername(owner.getRemoteId());
      entity.setOwnerDisplayName(displayName(owner));
      entity.setOwnerAvatarUrl(avatarUrl(owner));
    }
    return entity;
  }

  /**
   * A user's full name.
   *
   * @param identity the identity
   * @return the full name, or the username when the profile cannot say
   */
  private String displayName(Identity identity) {
    Profile profile = identity.getProfile();
    String fullName = profile == null ? null : profile.getFullName();
    return StringUtils.isNotBlank(fullName) ? fullName : identity.getRemoteId();
  }

  /**
   * A user's avatar.
   *
   * @param identity the identity
   * @return the avatar URL, null when the profile cannot say
   */
  private String avatarUrl(Identity identity) {
    Profile profile = identity.getProfile();
    return profile == null ? null : profile.getAvatarUrl();
  }

  /**
   * Reads an identity once per request.
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

}
