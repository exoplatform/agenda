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
package org.exoplatform.agenda.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.storage.CalendarLinkStorage;
import org.exoplatform.agenda.util.CalendarFeedIcsWriter;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Holds every rule of calendar links: who manages one, what its token is, and
 * when it stops answering.
 * <p>
 * <b>Managing</b> (read the status, create or reset, delete) is
 * {@link Utils#canEditCalendar}: the owner of a personal calendar, a manager of
 * the space for a space calendar.
 * <p>
 * <b>Answering</b> is decided again on every fetch, because the URL is
 * presented with no session: a link answers only while its creator still holds
 * the right that let them create it — a space calendar's creator is still a
 * manager of the space, a personal calendar's creator is still its owner — and
 * the creator's account is neither disabled nor deleted. The same predicate
 * sets {@link CalendarLink#isActive()} for the drawer, so the drawer and the
 * feed cannot disagree about a link.
 * <p>
 * <b>Tokens</b>: 32 bytes of {@link SecureRandom}, base64url without padding.
 * Only the SHA-256 digest is stored. A digest with no salt and no stretching is
 * enough for a 256-bit random secret — there is no dictionary to search — and
 * lets the feed find its row by an indexed equality. Neither the token nor its
 * digest is ever logged.
 */
@Service
public class AgendaCalendarLinkServiceImpl implements AgendaCalendarLinkService {

  /** Days of past events the feed publishes. */
  public static final int         PAST_DAYS    = 30;

  /** Days of future events the feed publishes. */
  public static final int         FUTURE_DAYS  = 365;

  /**
   * Most events one document carries, occurrences counted. A safety stop for a
   * calendar with a dense recurring event, not a page: the earliest are kept.
   */
  public static final int         MAX_EVENTS   = 2000;

  /** Bytes of randomness in a token. */
  static final int                TOKEN_BYTES  = 32;

  private static final Log        LOG          = ExoLogger.getLogger(AgendaCalendarLinkServiceImpl.class);

  /** A well-formed token: 43 base64url characters, the encoding of 32 bytes. */
  private static final Pattern    TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

  private final SecureRandom      secureRandom = new SecureRandom();

  private final CalendarLinkStorage calendarLinkStorage;

  private final AgendaCalendarService agendaCalendarService;

  private final AgendaEventService agendaEventService;

  private final IdentityManager   identityManager;

  private final SpaceService      spaceService;

  private Clock                   clock        = Clock.systemUTC();

  /**
   * Builds the service.
   *
   * @param calendarLinkStorage storage of calendar links
   * @param agendaCalendarService reads calendars
   * @param agendaEventService reads the events a feed publishes, re-applying
   *          the calendar ACL for the link's creator
   * @param identityManager resolves users and space identities
   * @param spaceService resolves space management rights
   */
  @Autowired
  public AgendaCalendarLinkServiceImpl(CalendarLinkStorage calendarLinkStorage,
                                       AgendaCalendarService agendaCalendarService,
                                       AgendaEventService agendaEventService,
                                       IdentityManager identityManager,
                                       SpaceService spaceService) {
    this.calendarLinkStorage = calendarLinkStorage;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarLink getCalendarLink(long calendarId, String username) throws ObjectNotFoundException,
                                                                        IllegalAccessException {
    Calendar calendar = getManageableCalendar(calendarId, username);
    CalendarLink link = calendarLinkStorage.getByCalendarId(calendar.getId());
    if (link != null) {
      link.setActive(isAnswering(calendar, link.getCreatorId()));
    }
    return link;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String saveCalendarLink(long calendarId, String username) throws ObjectNotFoundException, IllegalAccessException {
    Calendar calendar = getManageableCalendar(calendarId, username);
    long creatorId = userIdentityId(username);
    String token = newToken();
    calendarLinkStorage.save(calendar.getId(), creatorId, hash(token), Date.from(clock.instant()));
    return token;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarLink(long calendarId, String username) throws ObjectNotFoundException, IllegalAccessException {
    Calendar calendar = getManageableCalendar(calendarId, username);
    calendarLinkStorage.deleteByCalendarId(calendar.getId());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarLinks(long calendarId) {
    if (calendarId > 0) {
      calendarLinkStorage.deleteByCalendarId(calendarId);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Every refusal throws the same exception with the same message, and a
   * malformed token is still digested and looked up before it is refused, so
   * that the answer to an unknown token does not come back faster than the
   * answer to a real one.
   */
  @Override
  public String getCalendarFeed(String token) throws ObjectNotFoundException {
    String presented = StringUtils.defaultString(token);
    String digest = hash(presented);
    CalendarLink link = calendarLinkStorage.getByTokenHash(digest);
    if (link == null || !TOKEN_FORMAT.matcher(presented).matches()
        || !MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),
                                  StringUtils.defaultString(link.getTokenHash()).getBytes(StandardCharsets.US_ASCII))) {
      throw notFound();
    }
    Calendar calendar = agendaCalendarService.getCalendarById(link.getCalendarId());
    if (calendar == null || calendar.isDeleted() || !isAnswering(calendar, link.getCreatorId())) {
      throw notFound();
    }
    ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    List<Event> events = readEvents(calendar, link.getCreatorId(), now);
    return CalendarFeedIcsWriter.write(calendarName(calendar), events, uidHost(), now);
  }

  /**
   * Replaces the clock, for tests pinning the window.
   *
   * @param clock the clock to read the current instant from
   */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * Digests a token the way it is stored.
   *
   * @param token the token, never null
   * @return lowercase hexadecimal SHA-256 digest
   */
  static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available on this JVM", e);
    }
  }

  /**
   * Draws a new token.
   *
   * @return 32 random bytes, base64url without padding
   */
  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Reads a calendar the user may manage the link of.
   *
   * @param calendarId technical identifier of the calendar
   * @param username the user asking
   * @return the calendar
   * @throws ObjectNotFoundException when there is no such calendar, or its
   *           owner no longer exists
   * @throws IllegalAccessException when the user may not manage it
   */
  private Calendar getManageableCalendar(long calendarId, String username) throws ObjectNotFoundException,
                                                                          IllegalAccessException {
    if (calendarId <= 0) {
      throw new IllegalArgumentException("agenda.calendarLink.invalidCalendar");
    }
    long userIdentityId = userIdentityId(username);
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null || calendar.isDeleted()) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " wasn't found");
    }
    if (!Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), userIdentityId)) {
      throw new IllegalAccessException("User " + username + " is not allowed to manage the link of calendar " + calendarId);
    }
    return calendar;
  }

  /**
   * Resolves the identity identifier of an authenticated user.
   *
   * @param username the user
   * @return the identity identifier
   * @throws IllegalAccessException when the user has no usable identity
   */
  private long userIdentityId(String username) throws IllegalAccessException {
    Identity identity = StringUtils.isBlank(username) ? null : identityManager.getOrCreateUserIdentity(username);
    if (!isUsable(identity)) {
      throw new IllegalAccessException("User " + username + " has no usable identity");
    }
    return Long.parseLong(identity.getId());
  }

  /**
   * Whether a link created by this identity still answers for this calendar.
   *
   * @param calendar the calendar the link publishes
   * @param creatorId identity identifier of the link's creator
   * @return true while the creator still holds the right that let them create
   *         the link
   */
  private boolean isAnswering(Calendar calendar, long creatorId) {
    Identity creator = identityManager.getIdentity(String.valueOf(creatorId));
    if (!isUsable(creator) || !creator.isUser()) {
      return false;
    }
    Identity owner = identityManager.getIdentity(String.valueOf(calendar.getOwnerId()));
    if (owner == null || owner.isDeleted()) {
      return false;
    }
    if (owner.isUser()) {
      return creatorId == calendar.getOwnerId()
          && Utils.canAccessCalendar(identityManager, spaceService, calendar.getOwnerId(), creatorId);
    }
    if (owner.isSpace()) {
      return Utils.canEditCalendar(identityManager, spaceService, calendar.getOwnerId(), creatorId);
    }
    return false;
  }

  /**
   * Whether an identity can act: it exists, is not deleted and is enabled.
   *
   * @param identity the identity, may be null
   * @return true when usable
   */
  private boolean isUsable(Identity identity) {
    return identity != null && !identity.isDeleted() && identity.isEnable();
  }

  /**
   * Reads the events of the calendar inside the published window, as its
   * creator sees them.
   *
   * @param calendar the calendar
   * @param creatorId the link's creator, whose calendar ACL is applied again
   * @param now the current instant
   * @return the events, earliest first, at most {@link #MAX_EVENTS}
   * @throws ObjectNotFoundException when the creator is refused the calendar
   */
  private List<Event> readEvents(Calendar calendar, long creatorId, ZonedDateTime now) throws ObjectNotFoundException {
    EventFilter filter = new EventFilter();
    filter.setOwnerIds(List.of(calendar.getOwnerId()));
    filter.setStart(now.minusDays(PAST_DAYS));
    filter.setEnd(now.plusDays(FUTURE_DAYS));
    List<Event> events;
    try {
      events = agendaEventService.getEvents(filter, ZoneOffset.UTC, creatorId);
    } catch (IllegalAccessException e) {
      LOG.debug("The creator of the link of calendar {} is refused its events", calendar.getId());
      throw notFound();
    }
    if (events == null) {
      return List.of();
    }
    return events.stream()
                 .filter(event -> event != null && event.getCalendarId() == calendar.getId() && event.getStart() != null)
                 .sorted(Comparator.comparing(Event::getStart))
                 .limit(MAX_EVENTS)
                 .toList();
  }

  /**
   * The name a subscribing application shows for the calendar.
   *
   * @param calendar the calendar
   * @return its user-defined name, else its derived title
   */
  private String calendarName(Calendar calendar) {
    return StringUtils.isNotBlank(calendar.getName()) ? calendar.getName() : calendar.getTitle();
  }

  /**
   * The host qualifying event identifiers: the deployment's configured domain,
   * or a constant when it cannot be read.
   *
   * @return a host name
   */
  private String uidHost() {
    try {
      String domain = CommonsUtils.getCurrentDomain();
      String host = StringUtils.isBlank(domain) ? null : domain.replaceFirst("^https?://", "").replaceAll("[/:].*$", "");
      return StringUtils.isBlank(host) ? "exo" : host;
    } catch (RuntimeException | LinkageError e) {
      return "exo";
    }
  }

  /**
   * The one refusal every dead link answers with.
   *
   * @return the exception
   */
  private ObjectNotFoundException notFound() {
    return new ObjectNotFoundException("agenda.calendarLink.notFound");
  }

}
