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

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.model.CalendarSubscriptionEvent;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.search.AgendaIndexingServiceConnector;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.storage.CalendarSubscriptionStorage;
import org.exoplatform.agenda.util.CalendarFeedException;
import org.exoplatform.agenda.util.CalendarFeedFetcher;
import org.exoplatform.agenda.util.CalendarFeedFetcher.FeedResponse;
import org.exoplatform.agenda.util.CalendarFeedParser;
import org.exoplatform.agenda.util.CalendarFeedParser.ImportedEvent;
import org.exoplatform.agenda.util.CalendarFeedParser.ParsedCalendar;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

/**
 * Holds every rule of calendar subscriptions (EXO-90278), personal or of a
 * space (EXO-90373).
 * <p>
 * <b>Owners.</b> A subscription belongs to the owner of the calendar it fills: a
 * user, who alone manages it, or a space, whose real managers manage it whoever
 * added it. A space's calendar is a storage detail: it takes the space's colour,
 * its events are attended by the space, and every reader of the space's
 * calendar reads them.
 * <p>
 * <b>Stored events.</b> A subscription fills a calendar of its own,
 * flagged as a subscription, with one agenda event per occurrence of the feed in
 * the window — so imported events appear in every view, the search and the
 * preview like any other. They are written through {@link AgendaEventStorage},
 * never through {@link AgendaEventService}: no creation, update or deletion is
 * broadcast, so nothing imported sends an invitation or a notification, computes
 * a reminder, earns points, or reaches a connector's listeners; the search index
 * is kept in step directly. The calendar flag is what makes them read-only
 * ({@code AgendaEventServiceImpl#canUpdateEvent}) and what keeps the calendar out
 * of the listings a connector binds remote collections from.
 * <p>
 * <b>Reading a link.</b> A link of this very eXo — its public domain and the
 * path of a published calendar — is read in the process, through
 * {@link AgendaCalendarLinkService}, with the token checks the anonymous feed
 * applies; and refused when the calendar it publishes is one the user already
 * sees — for a space, when it is one of the space's own. Every other link is
 * read by {@link CalendarFeedFetcher}, whose guard
 * decides which addresses the platform may reach.
 * <p>
 * <b>Refreshing.</b> A refresh is claimed in the database before it runs, by the
 * scheduled job on every node and by the owner's "refresh now" alike, so a
 * single node reads a feed at a time. It sends the validators of the last read,
 * skips a body identical to the last one imported, rewrites only the occurrences
 * that changed and removes those the feed dropped. A failure keeps the last good
 * copy and records its message code.
 * <p>
 * <b>Secrets.</b> Feed URLs often embed one; a URL is stored encrypted with the
 * platform codec, compared through a digest, returned to whoever manages the
 * subscription only, and never logged.
 */
@Service
public class AgendaCalendarSubscriptionServiceImpl implements AgendaCalendarSubscriptionService {

  /** Days of past occurrences imported. */
  public static final int       PAST_DAYS               = 30;

  /** Days of future occurrences imported. */
  public static final int       FUTURE_DAYS             = 365;

  /** Most occurrences imported per subscription. */
  public static final int       MAX_EVENTS              = 2000;

  /** Most subscriptions a user holds. */
  public static final int       MAX_SUBSCRIPTIONS       = 50;

  /** Refresh interval of a feed that advertises none. */
  public static final Duration  DEFAULT_REFRESH         = Duration.ofHours(4);

  /** Shortest refresh interval honoured. */
  public static final Duration  MIN_REFRESH             = Duration.ofHours(1);

  /** Longest refresh interval honoured. */
  public static final Duration  MAX_REFRESH             = Duration.ofHours(24);

  /** Wait before retrying a failed refresh. */
  public static final Duration  RETRY_AFTER_FAILURE     = Duration.ofHours(1);

  /** Wait before retrying a subscription that cannot be refreshed at all. */
  public static final Duration  RETRY_AFTER_DEAD_END    = Duration.ofHours(24);

  /**
   * How long a withdrawn link of this eXo keeps its last imported copy: past
   * it, the imported events are removed and the subscription keeps its error
   * (EXO-90373).
   */
  public static final Duration  WITHDRAWN_LINK_PURGE    = Duration.ofDays(7);

  /** Shortest time between two refreshes asked by the owner. */
  public static final Duration  MANUAL_REFRESH_INTERVAL = Duration.ofMinutes(5);

  /** Age after which a refresh claim is taken over. */
  public static final Duration  CLAIM_STALE             = Duration.ofMinutes(30);

  /** Longest calendar name. */
  public static final int       MAX_NAME                = 200;

  /** The user already subscribes to this URL. */
  public static final String    ALREADY_SUBSCRIBED      = CalendarFeedException.CODE_PREFIX + "alreadySubscribed";

  /** The user holds the most subscriptions allowed. */
  public static final String    TOO_MANY_SUBSCRIPTIONS  = CalendarFeedException.CODE_PREFIX + "tooManySubscriptions";

  /** The stored URL cannot be decrypted: the platform codec key was replaced. */
  public static final String    URL_UNREADABLE          = CalendarFeedException.CODE_PREFIX + "urlUnreadable";

  /** A refresh was asked a moment ago. */
  public static final String    REFRESH_TOO_SOON        = CalendarFeedException.CODE_PREFIX + "refreshTooSoon";

  /** A refresh is running. */
  public static final String    REFRESH_IN_PROGRESS     = CalendarFeedException.CODE_PREFIX + "refreshInProgress";

  /** A refresh failed for a reason of the platform's. */
  public static final String    REFRESH_FAILED          = CalendarFeedException.CODE_PREFIX + "refreshFailed";

  /** The owner's account is disabled or deleted. */
  public static final String    USER_DISABLED           = CalendarFeedException.CODE_PREFIX + "userDisabled";

  /** A name longer than allowed. */
  public static final String    NAME_TOO_LONG           = CalendarFeedException.CODE_PREFIX + "nameTooLong";

  /** A colour that is not {@code #RRGGBB}. */
  public static final String    INVALID_COLOR           = CalendarFeedException.CODE_PREFIX + "invalidColor";

  /** The user already has as many links being read as allowed. */
  public static final String    TOO_MANY_READS          = CalendarFeedException.CODE_PREFIX + "tooManyReads";

  /**
   * Most links one user may have read at the same time on one node, through
   * checking, subscribing, editing and refreshing: a user looping on slow hosts
   * would otherwise hold the connections the scheduled refresh and every other
   * user share.
   */
  public static final int       MAX_READS_PER_USER      = 2;

  /** Bundle key of the text of the activity announcing a space's subscription. */
  static final String           SPACE_ACTIVITY_LABEL    = "agenda.calendarSubscription.spaceActivity";

  /** The text of that activity when the bundle cannot be read. */
  static final String           SPACE_ACTIVITY_DEFAULT  = "Added the calendar <b>{0}</b> to the agenda of this space.";

  /** Path of a published calendar's feed on this eXo, token and extension appended. */
  static final String           OWN_FEED_PATH           = "/agenda/rest/ical/";

  private static final String   FEED_EXTENSION          = ".ics";

  private static final Pattern  COLOR                   = Pattern.compile("^#[0-9a-fA-F]{6}$");

  private static final Log      LOG                     = ExoLogger.getLogger(AgendaCalendarSubscriptionServiceImpl.class);

  private final CalendarSubscriptionStorage subscriptionStorage;

  private final AgendaCalendarService       agendaCalendarService;

  private final AgendaEventStorage          agendaEventStorage;

  private final AgendaEventAttendeeStorage  attendeeStorage;

  private final AgendaCalendarLinkService   calendarLinkService;

  private final Map<Long, Integer>          readsInFlight           = new ConcurrentHashMap<>();

  private final CalendarFeedFetcher         feedFetcher;

  private final IdentityManager             identityManager;

  private final SpaceService                spaceService;

  private final CodecInitializer            codecInitializer;

  private final IndexingService             indexingService;

  private final ActivityManager             activityManager;

  private final String                      node;

  private Clock                             clock                   = Clock.systemUTC();

  private BiFunction<String, String, String> labelResolver          = AgendaCalendarSubscriptionServiceImpl::translatedLabel;

  /**
   * Builds the service.
   *
   * @param subscriptionStorage the subscription rows
   * @param agendaCalendarService creates, reads and deletes the calendars
   * @param agendaEventStorage writes the imported events without broadcasting
   * @param attendeeStorage writes the owner as the attendee of an imported event
   * @param calendarLinkService reads the links of this eXo
   * @param feedFetcher reads every other link
   * @param identityManager resolves users
   * @param spaceService resolves space memberships
   * @param codecInitializer the platform codec URLs are encrypted with
   * @param indexingService keeps the search index in step with the imported
   *          events
   * @param activityManager posts the activity announcing a space's
   *          subscription
   */
  @Autowired
  public AgendaCalendarSubscriptionServiceImpl(CalendarSubscriptionStorage subscriptionStorage, // NOSONAR
                                               AgendaCalendarService agendaCalendarService,
                                               AgendaEventStorage agendaEventStorage,
                                               AgendaEventAttendeeStorage attendeeStorage,
                                               AgendaCalendarLinkService calendarLinkService,
                                               CalendarFeedFetcher feedFetcher,
                                               IdentityManager identityManager,
                                               SpaceService spaceService,
                                               CodecInitializer codecInitializer,
                                               IndexingService indexingService,
                                               ActivityManager activityManager) {
    this.subscriptionStorage = subscriptionStorage;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventStorage = agendaEventStorage;
    this.attendeeStorage = attendeeStorage;
    this.calendarLinkService = calendarLinkService;
    this.feedFetcher = feedFetcher;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.codecInitializer = codecInitializer;
    this.indexingService = indexingService;
    this.activityManager = activityManager;
    this.node = StringUtils.left(ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID().toString().substring(0, 8),
                                 128);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CalendarSubscription> getSubscriptions(String username) throws IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    return subscriptionStorage.getByOwner(userIdentityId, MAX_SUBSCRIPTIONS).stream().map(this::forManager).toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CalendarSubscription> getSubscriptions(long ownerIdentityId, String username) throws ObjectNotFoundException,
                                                                                            IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    Identity owner = manageableOwner(ownerIdentityId, userIdentityId, username);
    return subscriptionStorage.getByOwner(Long.parseLong(owner.getId()), MAX_SUBSCRIPTIONS).stream().map(this::forManager).toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canManageSubscriptions(long ownerIdentityId, String username) {
    try {
      manageableOwner(ownerIdentityId, userIdentityId(username), username);
      return true;
    } catch (ObjectNotFoundException | IllegalAccessException e) {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarSubscription getSubscription(long subscriptionId, String username) throws ObjectNotFoundException,
                                                                                    IllegalAccessException {
    return forManager(manageable(subscriptionId, username));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String checkUrl(String url, String username) throws IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    return check(url, identityManager.getIdentity(String.valueOf(userIdentityId)), userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String checkUrl(String url, long ownerIdentityId, String username) throws ObjectNotFoundException, IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    return check(url, manageableOwner(ownerIdentityId, userIdentityId, username), userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarSubscription createSubscription(String url, String name, String color, String username) throws IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    return create(url, name, color, identityManager.getIdentity(String.valueOf(userIdentityId)), userIdentityId, username);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarSubscription createSubscription(String url,
                                                 String name,
                                                 String color,
                                                 long ownerIdentityId,
                                                 String username) throws ObjectNotFoundException, IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    return create(url, name, color, manageableOwner(ownerIdentityId, userIdentityId, username), userIdentityId, username);
  }

  /**
   * Reads a link once for an owner, storing nothing.
   *
   * @param url the URL as typed
   * @param owner the owner the link would be subscribed for
   * @param userIdentityId the user asking, whose read permits are used
   * @return the calendar's own name, or null
   */
  private String check(String url, Identity owner, long userIdentityId) {
    try {
      URI uri = feedFetcher.getGuard().normalize(url);
      return readForUser(uri, userIdentityId, owner, clock.instant()).parsed().name();
    } catch (CalendarFeedException e) {
      throw new IllegalArgumentException(e.getCode());
    }
  }

  /**
   * Subscribes an owner the user may manage to a link: reads it, creates the
   * owner's calendar flagged as a subscription, stores the subscription claimed
   * and imports its events. A space's calendar takes the space's colour, and a
   * space's new subscription is announced in its stream.
   *
   * @param url the URL as typed
   * @param name the name given, may be blank
   * @param color the colour given, may be blank; ignored for a space
   * @param owner the owner, already checked as manageable by the user
   * @param userIdentityId the user asking, recorded as who added it
   * @param username the user asking
   * @return the subscription as its managers read it
   * @throws IllegalAccessException when the calendar cannot be created for the
   *           owner by the user
   */
  private CalendarSubscription create(String url,
                                      String name,
                                      String color,
                                      Identity owner,
                                      long userIdentityId,
                                      String username) throws IllegalAccessException {
    long ownerIdentityId = Long.parseLong(owner.getId());
    String cleanName = cleanName(name);
    String cleanColor = cleanColor(color);
    Instant now = clock.instant();
    URI uri;
    FeedResponse feed;
    ParsedCalendar parsed;
    String urlKey;
    try {
      uri = feedFetcher.getGuard().normalize(url);
      urlKey = urlKey(ownerIdentityId, uri);
      if (subscriptionStorage.getByUrlKey(urlKey) != null) {
        throw new IllegalArgumentException(ALREADY_SUBSCRIBED);
      }
      if (subscriptionStorage.getByOwner(ownerIdentityId, MAX_SUBSCRIPTIONS).size() >= MAX_SUBSCRIPTIONS) {
        throw new IllegalArgumentException(TOO_MANY_SUBSCRIPTIONS);
      }
      ReadFeed answer = readForUser(uri, userIdentityId, owner, now);
      feed = answer.feed();
      parsed = answer.parsed();
    } catch (CalendarFeedException e) {
      throw new IllegalArgumentException(e.getCode());
    }

    Calendar calendar = new Calendar();
    calendar.setOwnerId(ownerIdentityId);
    calendar.setName(cleanName == null ? defaultName(parsed.name(), uri) : cleanName);
    // A space's subscribed calendar is never shown as a calendar of its own: its
    // events read as the space's, in the space's colour (EXO-90373)
    calendar.setColor(owner.isSpace() ? spaceColor(ownerIdentityId, username) : cleanColor);
    calendar.setSubscription(true);
    calendar = agendaCalendarService.createCalendar(calendar, username);

    CalendarSubscription created;
    try {
      CalendarSubscription subscription = new CalendarSubscription();
      subscription.setCalendarId(calendar.getId());
      subscription.setUserIdentityId(userIdentityId);
      subscription.setOwnerIdentityId(ownerIdentityId);
      subscription.setUrlEncrypted(encrypt(uri.toString()));
      subscription.setUrlKey(urlKey);
      subscription.setCreatedDate(now.toEpochMilli());
      subscription.setNextRefreshDate(now.plus(DEFAULT_REFRESH).toEpochMilli());
      // Created claimed: the job must not read the feed while its first import runs
      subscription.setClaimedBy(node);
      subscription.setClaimedDate(now.toEpochMilli());
      created = subscriptionStorage.create(subscription);
    } catch (RuntimeException e) {
      // No subscription row points at the calendar: nothing would ever list or
      // remove it, since the owner listings and the calendar API leave it out
      deleteCalendarQuietly(calendar.getId());
      throw e;
    }
    if (created == null) {
      deleteCalendarQuietly(calendar.getId());
      throw new IllegalArgumentException(ALREADY_SUBSCRIBED);
    }
    try {
      apply(created, feed, parsed, now);
    } catch (RuntimeException e) {
      // The subscription exists and its failure is recorded on it: its managers
      // see it with its warning, a refresh retries it, and removing it removes it
      LOG.warn("The first import of calendar subscription {} failed", created.getId(), e);
    }
    if (owner.isSpace()) {
      announceInSpace(owner, userIdentityId, username, calendar.getName(), created.getId());
    }
    return forManager(subscriptionStorage.getById(created.getId()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarSubscription updateSubscription(long subscriptionId,
                                                 String url,
                                                 String name,
                                                 String color,
                                                 String username) throws ObjectNotFoundException, IllegalAccessException {
    CalendarSubscription subscription = manageable(subscriptionId, username);
    Calendar calendar = agendaCalendarService.getCalendarById(subscription.getCalendarId());
    if (calendar == null) {
      throw new ObjectNotFoundException("The calendar of subscription " + subscriptionId + " wasn't found");
    }
    String cleanName = cleanName(name);
    // A space's subscribed calendar follows the space's colour (EXO-90373)
    String cleanColor = isSpace(subscription.getOwnerIdentityId()) ? null : cleanColor(color);
    Instant now = clock.instant();
    URI newUri = null;
    String newKey = null;
    FeedResponse feed = null;
    ParsedCalendar parsed = null;
    if (StringUtils.isNotBlank(url)) {
      try {
        URI uri = feedFetcher.getGuard().normalize(url);
        String key = urlKey(subscription.getOwnerIdentityId(), uri);
        if (!key.equals(subscription.getUrlKey())) {
          if (subscriptionStorage.getByUrlKey(key) != null) {
            throw new IllegalArgumentException(ALREADY_SUBSCRIBED);
          }
          ReadFeed answer = readForUser(uri,
                                        userIdentityId(username),
                                        identityManager.getIdentity(String.valueOf(subscription.getOwnerIdentityId())),
                                        now);
          feed = answer.feed();
          parsed = answer.parsed();
          newUri = uri;
          newKey = key;
        }
      } catch (CalendarFeedException e) {
        throw new IllegalArgumentException(e.getCode());
      }
    }
    if (cleanName != null || cleanColor != null) {
      calendar.setName(cleanName == null ? calendar.getName() : cleanName);
      calendar.setColor(cleanColor == null ? calendar.getColor() : cleanColor);
      agendaCalendarService.updateCalendar(calendar);
    }
    if (newUri != null) {
      if (!subscriptionStorage.updateUrl(subscriptionId, encrypt(newUri.toString()), newKey, Date.from(now))) {
        throw new IllegalArgumentException(ALREADY_SUBSCRIBED);
      }
      if (subscriptionStorage.claim(subscriptionId, node, Date.from(now), staleBefore(now))) {
        // The new link was just read: its answer is imported rather than read again
        apply(subscriptionStorage.getById(subscriptionId), feed, parsed, now);
      }
    }
    return forManager(subscriptionStorage.getById(subscriptionId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarSubscription refreshSubscription(long subscriptionId, String username) throws ObjectNotFoundException,
                                                                                        IllegalAccessException {
    CalendarSubscription subscription = manageable(subscriptionId, username);
    Instant now = clock.instant();
    if (subscription.getLastAttemptDate() > 0
        && now.toEpochMilli() - subscription.getLastAttemptDate() < MANUAL_REFRESH_INTERVAL.toMillis()) {
      throw new IllegalStateException(REFRESH_TOO_SOON);
    }
    // The permit is the user's who asks: the abuse it bounds is one person
    // looping, whichever subscription they loop on
    long readerIdentityId = userIdentityId(username);
    if (!acquireRead(readerIdentityId)) {
      throw new IllegalStateException(TOO_MANY_READS);
    }
    try {
      if (!subscriptionStorage.claim(subscriptionId, node, Date.from(now), staleBefore(now))) {
        throw new IllegalStateException(REFRESH_IN_PROGRESS);
      }
      refreshClaimed(subscriptionStorage.getById(subscriptionId), now);
    } finally {
      releaseRead(readerIdentityId);
    }
    return forManager(subscriptionStorage.getById(subscriptionId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteSubscription(long subscriptionId, String username) throws ObjectNotFoundException, IllegalAccessException {
    removeSubscription(manageable(subscriptionId, username));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteCalendarSubscription(long calendarId) {
    if (calendarId <= 0) {
      return;
    }
    CalendarSubscription subscription = subscriptionStorage.getByCalendarId(calendarId);
    if (subscription == null) {
      return;
    }
    List<CalendarSubscriptionEvent> rows = subscriptionStorage.getEvents(subscription.getId());
    subscriptionStorage.delete(subscription.getId());
    rows.forEach(row -> unindex(row.getEventId()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getSubscriptionCalendarIds(List<Long> ownerIdentityIds) {
    if (ownerIdentityIds == null || ownerIdentityIds.isEmpty()) {
      return List.of();
    }
    // One statement for every owner, not one per owner: this is asked on the
    // listing path, where the owners are the reader's identity and every space
    // they belong to, and once per attendee by the availability reader
    List<Long> owners = ownerIdentityIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
    if (owners.isEmpty()) {
      return List.of();
    }
    return subscriptionStorage.getCalendarIdsByOwners(owners, MAX_SUBSCRIPTIONS * owners.size())
                              .stream()
                              .distinct()
                              .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void followSpaceColor(Calendar calendar) {
    if (calendar == null || calendar.getId() <= 0 || calendar.isSubscription() || StringUtils.isBlank(calendar.getColor())
        || !isSpace(calendar.getOwnerId())) {
      return;
    }
    for (Long calendarId : subscriptionStorage.getCalendarIdsByOwner(calendar.getOwnerId(), MAX_SUBSCRIPTIONS)) {
      Calendar subscribed = calendarId == null ? null : agendaCalendarService.getCalendarById(calendarId);
      if (subscribed == null || !subscribed.isSubscription() || calendar.getColor().equalsIgnoreCase(subscribed.getColor())) {
        continue;
      }
      subscribed.setColor(calendar.getColor());
      try {
        // Saved without a permission check, keeping the flag: the storage evicts
        // the cached calendar, and the update it broadcasts is a subscribed
        // calendar's, which this method leaves alone
        agendaCalendarService.updateCalendar(subscribed);
      } catch (ObjectNotFoundException e) {
        LOG.debug("The subscribed calendar {} was deleted meanwhile", calendarId);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int refreshDueSubscriptions(int batchSize) {
    Instant now = clock.instant();
    int refreshed = 0;
    for (Long subscriptionId : subscriptionStorage.getDueIds(Date.from(now), staleBefore(now), batchSize)) {
      if (!subscriptionStorage.claimDue(subscriptionId, node, Date.from(now), staleBefore(now))) {
        continue;
      }
      try {
        CalendarSubscription subscription = subscriptionStorage.getById(subscriptionId);
        if (subscription != null) {
          refreshClaimed(subscription, clock.instant());
          refreshed++;
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.warn("Calendar subscription {} could not be refreshed", subscriptionId, e);
        subscriptionStorage.release(subscriptionId, node);
      }
    }
    return refreshed;
  }

  /**
   * Replaces the clock, for the tests.
   *
   * @param clock the clock
   */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * @return this node's identity in the refresh claims
   */
  String getNode() {
    return node;
  }

  /**
   * Refreshes a subscription this node holds the claim of, recording the outcome
   * and releasing the claim whatever happens.
   *
   * @param subscription the subscription as read after the claim
   * @param now the instant of the refresh
   */
  private void refreshClaimed(CalendarSubscription subscription, Instant now) {
    Date attempt = Date.from(now);
    Identity owner = identityManager.getIdentity(String.valueOf(subscription.getOwnerIdentityId()));
    if (owner != null && owner.isDeleted()) {
      // A deleted user or space keeps nothing refreshing into a calendar nobody
      // can read any more: the subscription goes with its calendar and events
      // (EXO-90373). An identity merely not found is not taken for a deletion.
      removeSubscription(subscription);
      return;
    }
    if (owner == null || owner.isUser() && !owner.isEnable()) {
      recordFailure(subscription, attempt, USER_DISABLED, now.plus(RETRY_AFTER_DEAD_END));
      return;
    }
    URI uri = storedUri(subscription);
    if (uri == null) {
      recordFailure(subscription, attempt, URL_UNREADABLE, now.plus(RETRY_AFTER_DEAD_END));
      return;
    }
    try {
      FeedResponse feed = read(uri, subscription.getEtag(), subscription.getLastModified(), owner);
      apply(subscription, feed, null, now);
    } catch (CalendarFeedException e) {
      LOG.debug("Calendar subscription {} could not be read: {}", subscription.getId(), e.getReason());
      // The copy is purged once, not on every hourly retry: forgetContent
      // nulls the content hash, and nothing but a successful read writes it
      // again, so the hash is the record that the purge already ran. Without
      // this test the predicate below stays true for ever - a failure never
      // moves the last-success date it measures from - and every retry would
      // re-issue the same writes for the life of the instance.
      if (subscription.getContentHash() != null && isWithdrawnForLong(subscription, e, now)) {
        purgeImported(subscription);
      }
      recordFailure(subscription, attempt, e.getCode(), now.plus(RETRY_AFTER_FAILURE));
    }
  }

  /**
   * Whether a failed read is a link of this eXo that answers nothing, and from
   * which nothing has been imported for longer than
   * {@link #WITHDRAWN_LINK_PURGE}.
   * <p>
   * The window is measured from the <b>last successful import</b> — or from
   * the subscription's creation, when there never was one — and not from the
   * first failure: no first-failure date is kept on the row. So this is "the
   * copy is a week stale and the link answers nothing", not "the link has been
   * withdrawn for a week". The two differ for an instance that was down for
   * the week, where the first attempt after the outage can purge; the copy is
   * a week stale there too, and a link published again is imported in full,
   * {@link #purgeImported} having forgotten what was read.
   * <p>
   * Only a link of this eXo qualifies: {@code LINK_NOT_FOUND} is raised by the
   * in-process branch of {@link #read}, from this platform's own database, and
   * never by the fetcher. An external feed that answers 404 fails with an HTTP
   * code and keeps its copy however long it fails.
   *
   * @param subscription the subscription
   * @param failure why the read failed
   * @param now the instant of the refresh
   * @return true when its imported copy is to be removed
   */
  private static boolean isWithdrawnForLong(CalendarSubscription subscription, CalendarFeedException failure, Instant now) {
    if (!(CalendarFeedException.CODE_PREFIX + CalendarFeedException.LINK_NOT_FOUND).equals(failure.getCode())) {
      return false;
    }
    long lastGood = subscription.getLastSuccessDate() > 0 ? subscription.getLastSuccessDate() : subscription.getCreatedDate();
    return lastGood > 0 && now.toEpochMilli() - lastGood >= WITHDRAWN_LINK_PURGE.toMillis();
  }

  /**
   * Removes the events a subscription imported, and forgets what was read, so
   * that a link published again is imported again in full.
   *
   * @param subscription the subscription, claimed by this node
   */
  private void purgeImported(CalendarSubscription subscription) {
    for (CalendarSubscriptionEvent row : subscriptionStorage.getEvents(subscription.getId())) {
      Event stored = agendaEventStorage.getEventById(row.getEventId());
      if (stored != null && stored.getCalendarId() == subscription.getCalendarId()) {
        agendaEventStorage.deleteEventById(row.getEventId());
      }
      unindex(row.getEventId());
      subscriptionStorage.deleteEvent(row.getId());
    }
    subscriptionStorage.forgetContent(subscription.getId());
  }

  /**
   * Removes a subscription, its calendar and every event it imported, with no
   * permission check.
   *
   * @param subscription the subscription
   */
  private void removeSubscription(CalendarSubscription subscription) {
    List<CalendarSubscriptionEvent> rows = subscriptionStorage.getEvents(subscription.getId());
    subscriptionStorage.delete(subscription.getId());
    deleteCalendarQuietly(subscription.getCalendarId());
    rows.forEach(row -> unindex(row.getEventId()));
  }

  /**
   * Records a failed refresh; when the row no longer matches this node's claim,
   * the claim is released all the same, so that nothing stays claimed until it
   * goes stale.
   *
   * @param subscription the subscription
   * @param attempt when the refresh ran
   * @param code message code of the failure
   * @param next when the next attempt is due
   */
  private void recordFailure(CalendarSubscription subscription, Date attempt, String code, Instant next) {
    if (!subscriptionStorage.recordFailure(subscription.getId(), node, attempt, code, Date.from(next))) {
      subscriptionStorage.release(subscription.getId(), node);
    }
  }

  /**
   * A link read and its document parsed.
   *
   * @param feed what was read
   * @param parsed the document read
   */
  private record ReadFeed(FeedResponse feed, ParsedCalendar parsed) {
  }

  /**
   * Reads a link on behalf of a user and parses what it serves, at most
   * {@link #MAX_READS_PER_USER} at a time for that user on this node. The permit
   * is held across the parse too: parsing a document costs CPU as reading it
   * costs a connection.
   *
   * @param uri the normalized URL
   * @param userIdentityId the user asking, whose permits are used
   * @param owner the owner the link is read for, a user or a space
   * @param now the instant the window is computed from
   * @return what was read and parsed
   * @throws CalendarFeedException with the reason nothing usable was read
   */
  private ReadFeed readForUser(URI uri, long userIdentityId, Identity owner, Instant now) throws CalendarFeedException {
    if (!acquireRead(userIdentityId)) {
      throw new IllegalStateException(TOO_MANY_READS);
    }
    try {
      FeedResponse feed = read(uri, null, null, owner);
      return new ReadFeed(feed, parse(feed.body(), now));
    } finally {
      releaseRead(userIdentityId);
    }
  }

  /**
   * Takes one of a user's read permits.
   *
   * @param userIdentityId the user
   * @return false when the user holds them all
   */
  private boolean acquireRead(long userIdentityId) {
    // counted inside the map's own atomic update: a permit given back and its
    // entry removed never races a permit taken on the entry being removed
    boolean[] acquired = { false };
    readsInFlight.compute(userIdentityId, (id, held) -> {
      int count = held == null ? 0 : held;
      if (count >= MAX_READS_PER_USER) {
        return held;
      }
      acquired[0] = true;
      return count + 1;
    });
    return acquired[0];
  }

  /**
   * Gives a read permit back.
   *
   * @param userIdentityId the user
   */
  private void releaseRead(long userIdentityId) {
    readsInFlight.computeIfPresent(userIdentityId, (id, held) -> held <= 1 ? null : held - 1);
  }

  /**
   * Imports what a read answered and records the outcome, releasing the claim.
   * An answer that nothing changed, or a body identical to the last one imported,
   * rewrites nothing.
   *
   * @param subscription the subscription, claimed by this node
   * @param feed what the read answered
   * @param parsed the body already parsed, or null to parse it here
   * @param now the instant of the refresh
   * @throws RuntimeException after recording a failure, when the import failed
   *           for a reason of the platform's
   */
  private void apply(CalendarSubscription subscription, FeedResponse feed, ParsedCalendar parsed, Instant now) {
    Date attempt = Date.from(now);
    try {
      if (feed.notModified()) {
        recordSuccess(subscription, feed.etag(), feed.lastModified(), subscription.getContentHash(), refreshMinutesOf(subscription),
                      subscription.isTruncated(), now);
        return;
      }
      String contentHash = CalendarFeedParserAccess.sha256(feed.body());
      if (parsed == null && contentHash.equals(subscription.getContentHash())) {
        recordSuccess(subscription, feed.etag(), feed.lastModified(), contentHash, refreshMinutesOf(subscription),
                      subscription.isTruncated(), now);
        return;
      }
      ParsedCalendar document = parsed == null ? parse(feed.body(), now) : parsed;
      importEvents(subscription, document, now);
      recordSuccess(subscription, feed.etag(), feed.lastModified(), contentHash, refreshMinutes(document.refreshInterval()),
                    document.truncated(), now);
    } catch (CalendarFeedException e) {
      LOG.debug("Calendar subscription {} could not be read: {}", subscription.getId(), e.getReason());
      recordFailure(subscription, attempt, e.getCode(), now.plus(RETRY_AFTER_FAILURE));
    } catch (RuntimeException e) {
      recordFailure(subscription, attempt, REFRESH_FAILED, now.plus(RETRY_AFTER_FAILURE));
      throw e;
    }
  }

  /**
   * Records a successful refresh.
   *
   * @param subscription the subscription
   * @param etag the entity tag answered
   * @param lastModified the Last-Modified answered
   * @param contentHash the digest of the body imported
   * @param refreshMinutes the interval the feed advertises, null for none
   * @param truncated whether part of the feed was left out
   * @param now the instant of the refresh
   */
  private void recordSuccess(CalendarSubscription subscription,
                             String etag,
                             String lastModified,
                             String contentHash,
                             Integer refreshMinutes,
                             boolean truncated,
                             Instant now) {
    Duration interval = refreshMinutes == null ? DEFAULT_REFRESH : Duration.ofMinutes(refreshMinutes);
    boolean recorded = subscriptionStorage.recordSuccess(subscription.getId(),
                                                         node,
                                                         subscription.getUrlKey(),
                                                         etag,
                                                         lastModified,
                                                         contentHash,
                                                         refreshMinutes,
                                                         Date.from(now),
                                                         truncated,
                                                         Date.from(now.plus(interval)));
    if (!recorded) {
      // The URL changed during the read (updateUrl made the row due at once), or
      // the claim went stale: the outcome belongs to nobody, the claim is still
      // this node's to give back
      subscriptionStorage.release(subscription.getId(), node);
    }
  }

  /**
   * Writes the occurrences of a document into the subscription's calendar: new
   * ones created, changed ones rewritten, dropped ones removed. A personal
   * subscription's occurrences are attended by their owner; a space's are
   * attended by nobody (EXO-90373).
   *
   * @param subscription the subscription
   * @param document the document read
   * @param now the instant of the refresh
   */
  private void importEvents(CalendarSubscription subscription, ParsedCalendar document, Instant now) {
    boolean userOwned = subscription.getOwnerIdentityId() == subscription.getUserIdentityId()
        || !isSpace(subscription.getOwnerIdentityId());
    Map<String, CalendarSubscriptionEvent> existing = new HashMap<>();
    for (CalendarSubscriptionEvent row : subscriptionStorage.getEvents(subscription.getId())) {
      existing.putIfAbsent(row.getEventKey(), row);
    }
    Set<String> seen = new HashSet<>();
    for (ImportedEvent imported : document.events()) {
      String key = CalendarFeedParserAccess.sha256((subscription.getId() + "|" + imported.key()).getBytes(StandardCharsets.UTF_8));
      if (!seen.add(key)) {
        continue;
      }
      String contentHash = imported.contentHash();
      CalendarSubscriptionEvent row = existing.get(key);
      if (row != null && contentHash.equals(row.getContentHash())) {
        continue;
      }
      Event stored = row == null ? null : agendaEventStorage.getEventById(row.getEventId());
      long eventId;
      if (stored != null && stored.getCalendarId() == subscription.getCalendarId()) {
        fill(stored, imported);
        stored.setModifierId(subscription.getUserIdentityId());
        stored.setUpdated(ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
        eventId = agendaEventStorage.updateEvent(stored).getId();
      } else {
        eventId = agendaEventStorage.createEvent(newEvent(subscription, imported, now)).getId();
        if (userOwned) {
          // A personal subscription: the owner attends the event, as the author
          // of an event made in agenda does — the default "my events" view and
          // the timeline list events through the attendee table, and an event
          // nobody attends never appears there. ACCEPTED, so it is never a
          // pending invitation. Written through the storage: its attendee
          // broadcast has no listener, and no invitation or notification is
          // sent.
          //
          // A space's subscription writes NO attendee row (EXO-90373). An
          // attendee row naming the space says "the space's members attend this
          // meeting", and the platform reads it that way wherever it meets it:
          // an attendee-keyed listing expands to {user} + {user's spaces}
          // (AgendaEventServiceImpl#getEvents), so caldav's seeding of "the
          // meetings they attend" listed every imported occurrence for every
          // connected member and wrote a copy of it into their personal CalDAV
          // account — which then came back as a remote event of theirs, drawn
          // beside the space's own copy. The feed's events are the space's, not
          // its members' commitments: they show wherever a space event with no
          // attendee shows, the space's agenda and the "All events" view of a
          // member who selected the space.
          attendeeStorage.saveEventAttendee(new EventAttendee(0, eventId, subscription.getOwnerIdentityId(), EventAttendeeResponse.ACCEPTED),
                                            eventId);
        }
      }
      reindex(eventId);
      CalendarSubscriptionEvent saved = row == null ? new CalendarSubscriptionEvent(0, subscription.getId(), eventId, key, contentHash)
                                                    : row;
      saved.setEventId(eventId);
      saved.setContentHash(contentHash);
      subscriptionStorage.saveEvent(saved);
    }
    for (CalendarSubscriptionEvent row : existing.values()) {
      if (!seen.contains(row.getEventKey())) {
        Event stored = agendaEventStorage.getEventById(row.getEventId());
        if (stored != null && stored.getCalendarId() == subscription.getCalendarId()) {
          agendaEventStorage.deleteEventById(row.getEventId());
        }
        unindex(row.getEventId());
        subscriptionStorage.deleteEvent(row.getId());
      }
    }
  }

  /**
   * A new agenda event for an occurrence: created by the owner, confirmed, with
   * no attendee, no reminder and no conference.
   *
   * @param subscription the subscription
   * @param imported the occurrence
   * @param now the instant of the import
   * @return the event to create
   */
  private Event newEvent(CalendarSubscription subscription, ImportedEvent imported, Instant now) {
    Event event = new Event();
    event.setCalendarId(subscription.getCalendarId());
    event.setCreatorId(subscription.getUserIdentityId());
    event.setCreated(ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
    fill(event, imported);
    return event;
  }

  /**
   * Copies an occurrence's fields onto an event.
   *
   * @param event the event
   * @param imported the occurrence
   */
  private void fill(Event event, ImportedEvent imported) {
    event.setSummary(imported.summary());
    event.setDescription(CalendarFeedParser.descriptionMarkup(imported.description()));
    event.setLocation(imported.location());
    event.setAllDay(imported.allDay());
    event.setStart(imported.start());
    event.setEnd(imported.end());
    event.setTimeZoneId(imported.timeZone());
    // Free, whatever the feed says: busy time is read from the events a user
    // attends, and someone else's calendar, a holiday list or a team feed is not
    // the user's own commitment (PO decision to revisit)
    event.setAvailability(EventAvailability.FREE);
    // Stated rather than left to the service: this path writes through the
    // storage directly, so nothing defaults it. An imported occurrence of
    // someone else's calendar is published like any other event of the
    // subscriber's — the feed it came from decided nothing about that
    // (EXO-90322)
    event.setVisibility(EventVisibility.DEFAULT);
    // Never TENTATIVE: in agenda that status is a date poll
    event.setStatus(EventStatus.CONFIRMED);
    event.setAllowAttendeeToUpdate(false);
    event.setAllowAttendeeToInvite(false);
  }

  /**
   * Reads a link: in the process when it is a link of this eXo, over HTTP
   * otherwise.
   *
   * @param uri the normalized URL
   * @param etag the entity tag to send, or null
   * @param lastModified the date to send, or null
   * @param owner the owner the link is read for, a user or a space
   * @return what was read
   * @throws CalendarFeedException with the reason nothing usable was read
   */
  private FeedResponse read(URI uri, String etag, String lastModified, Identity owner) throws CalendarFeedException {
    String token = ownFeedToken(uri);
    if (token == null) {
      return feedFetcher.fetch(uri, etag, lastModified);
    }
    try {
      long calendarId = calendarLinkService.getFeedCalendarId(token);
      Calendar linked = agendaCalendarService.getCalendarById(calendarId);
      long ownerIdentityId = Long.parseLong(owner.getId());
      if (owner.isSpace()) {
        // A space may subscribe to another space's calendar, or a colleague's,
        // never to its own: its members already read it (EXO-90373)
        if (linked != null && linked.getOwnerId() == ownerIdentityId) {
          throw new CalendarFeedException(CalendarFeedException.OWN_SPACE_CALENDAR);
        }
      } else {
        // the user's own calendar is told apart from one seen through a space:
        // the owner is the one most likely to paste the link they published
        if (linked != null && linked.getOwnerId() == ownerIdentityId) {
          throw new CalendarFeedException(CalendarFeedException.OWN_CALENDAR);
        }
        if (linked != null && Utils.canAccessCalendar(identityManager, spaceService, linked.getOwnerId(), ownerIdentityId)) {
          throw new CalendarFeedException(CalendarFeedException.ALREADY_IN_AGENDA);
        }
      }
      return new FeedResponse(false, calendarLinkService.getCalendarFeed(token).getBytes(StandardCharsets.UTF_8), null, null);
    } catch (ObjectNotFoundException e) {
      throw new CalendarFeedException(CalendarFeedException.LINK_NOT_FOUND);
    }
  }

  /**
   * The token of a link of this eXo: same host and port as the platform's public
   * domain, and the path of a published calendar's feed.
   *
   * @param uri the normalized URL
   * @return the token, or null when the URL is not such a link
   */
  String ownFeedToken(URI uri) {
    String domain;
    try {
      domain = CommonsUtils.getCurrentDomain();
    } catch (RuntimeException | LinkageError e) {
      return null;
    }
    if (StringUtils.isBlank(domain)) {
      return null;
    }
    URI base;
    try {
      base = new URI(domain.trim());
    } catch (URISyntaxException e) {
      return null;
    }
    if (base.getHost() == null || !base.getHost().equalsIgnoreCase(uri.getHost()) || effectivePort(base) != effectivePort(uri)) {
      return null;
    }
    String path = uri.getRawPath();
    if (path == null || !path.startsWith(OWN_FEED_PATH) || !path.endsWith(FEED_EXTENSION)) {
      return null;
    }
    String token = path.substring(OWN_FEED_PATH.length(), path.length() - FEED_EXTENSION.length());
    return token.isEmpty() || token.contains("/") ? null : token;
  }

  /**
   * The port a URL reaches.
   *
   * @param uri the URL
   * @return its port, or its scheme's default
   */
  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
  }

  /**
   * Parses a document in the import window.
   *
   * @param body the body
   * @param now the current instant
   * @return the document
   * @throws CalendarFeedException when it is not a readable calendar
   */
  private ParsedCalendar parse(byte[] body, Instant now) throws CalendarFeedException {
    return CalendarFeedParser.parse(body, now.minus(Duration.ofDays(PAST_DAYS)), now.plus(Duration.ofDays(FUTURE_DAYS)), MAX_EVENTS);
  }

  /**
   * The URL of a stored subscription, decrypted and checked again against the
   * deployment's current rules.
   *
   * @param subscription the subscription
   * @return the URL, or null when it cannot be decrypted or is no longer allowed
   */
  private URI storedUri(CalendarSubscription subscription) {
    String url = decrypt(subscription);
    if (url == null) {
      return null;
    }
    try {
      return feedFetcher.getGuard().normalize(url);
    } catch (CalendarFeedException e) {
      return null;
    }
  }

  /**
   * A subscription as whoever manages it reads it: the URL decrypted, the name
   * and colour of its calendar, who added it, and none of the stored secrets or
   * validators.
   *
   * @param subscription the stored subscription
   * @return the same subscription, filled for its managers
   */
  private CalendarSubscription forManager(CalendarSubscription subscription) {
    if (subscription == null) {
      return null;
    }
    subscription.setUrl(decrypt(subscription));
    if (subscription.getUrl() == null && subscription.getLastError() == null) {
      subscription.setLastError(URL_UNREADABLE);
    }
    Calendar calendar = agendaCalendarService.getCalendarById(subscription.getCalendarId());
    if (calendar != null) {
      subscription.setName(calendar.getName());
      subscription.setColor(calendar.getColor());
    }
    Identity creator = identityManager.getIdentity(String.valueOf(subscription.getUserIdentityId()));
    if (creator != null) {
      subscription.setCreatorUsername(creator.getRemoteId());
      subscription.setCreatorFullName(creator.getProfile() == null ? creator.getRemoteId() : creator.getProfile().getFullName());
    }
    subscription.setUrlEncrypted(null);
    subscription.setUrlKey(null);
    subscription.setEtag(null);
    subscription.setLastModified(null);
    subscription.setContentHash(null);
    subscription.setClaimedBy(null);
    return subscription;
  }

  /**
   * A subscription the user may manage: their own, or one of a space they are a
   * real manager of (EXO-90373).
   *
   * @param subscriptionId technical identifier
   * @param username the user
   * @return the stored subscription
   * @throws ObjectNotFoundException when none exists
   * @throws IllegalAccessException when the user may not manage it
   */
  private CalendarSubscription manageable(long subscriptionId, String username) throws ObjectNotFoundException, IllegalAccessException {
    long userIdentityId = userIdentityId(username);
    CalendarSubscription subscription = subscriptionId <= 0 ? null : subscriptionStorage.getById(subscriptionId);
    if (subscription == null) {
      throw new ObjectNotFoundException("Calendar subscription " + subscriptionId + " wasn't found");
    }
    Identity owner = identityManager.getIdentity(String.valueOf(subscription.getOwnerIdentityId()));
    if (owner == null || !canManage(owner, userIdentityId)) {
      throw new IllegalAccessException("User " + username + " may not manage calendar subscription " + subscriptionId);
    }
    return subscription;
  }

  /**
   * The owner of subscriptions, when the user may manage them.
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param userIdentityId identity identifier of the user
   * @param username the user
   * @return the owner's identity
   * @throws ObjectNotFoundException when the owner does not exist or was deleted
   * @throws IllegalAccessException when the user may not manage its
   *           subscriptions
   */
  private Identity manageableOwner(long ownerIdentityId, long userIdentityId, String username) throws ObjectNotFoundException,
                                                                                               IllegalAccessException {
    Identity owner = ownerIdentityId <= 0 ? null : identityManager.getIdentity(String.valueOf(ownerIdentityId));
    if (owner == null || owner.isDeleted()) {
      throw new ObjectNotFoundException("Identity " + ownerIdentityId + " wasn't found");
    }
    if (!canManage(owner, userIdentityId)) {
      throw new IllegalAccessException("User " + username + " may not manage the calendar subscriptions of identity " + ownerIdentityId);
    }
    return owner;
  }

  /**
   * Whether a user may manage an owner's subscriptions: a user's are their own
   * alone; a space's are its real managers', a member holding the manager role
   * ({@link Utils#canPublishCalendar}), never a super-manager who is not one nor
   * a redactor — the rule publishing a space calendar follows, since adding a
   * calendar to a space is managing the space's agenda. Any other kind of owner
   * has none.
   *
   * @param owner the owner's identity
   * @param userIdentityId identity identifier of the user
   * @return true when the user may manage them
   */
  private boolean canManage(Identity owner, long userIdentityId) {
    long ownerIdentityId = Long.parseLong(owner.getId());
    if (owner.isUser()) {
      return ownerIdentityId == userIdentityId;
    } else if (owner.isSpace()) {
      return Utils.canPublishCalendar(identityManager, spaceService, ownerIdentityId, userIdentityId);
    }
    return false;
  }

  /**
   * Whether an identity is a space.
   *
   * @param identityId identity identifier
   * @return true for an existing space identity
   */
  private boolean isSpace(long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity != null && identity.isSpace();
  }

  /**
   * The colour of a space's calendar, the one its settings show and its events
   * are drawn in: the first calendar the space's listing returns, or the colour
   * the space's calendar will take when it is created.
   *
   * @param spaceIdentityId identity identifier of the space
   * @param username a manager of the space
   * @return the colour, or null to let the calendar service pick one
   * @throws IllegalAccessException when the user may not read the space's
   *           calendars
   */
  private String spaceColor(long spaceIdentityId, String username) throws IllegalAccessException {
    // A space normally has one calendar, and then the two rules agree. With
    // several, this takes the first the owner listing returns while
    // followSpaceColor takes whichever was last saved, so the colour a new
    // subscription is born with may differ from the one it converges to on the
    // next save of a calendar of that space. Deliberate: making them one rule
    // would mean reading the space's system calendar on every calendar save
    // platform-wide, on a broadcast path, to settle a case the product does
    // not create.
    List<Calendar> calendars = agendaCalendarService.getCalendarsByOwnerIds(List.of(spaceIdentityId), username);
    Calendar calendar = calendars == null || calendars.isEmpty() ? agendaCalendarService.createCalendarInstance(spaceIdentityId)
                                                                 : calendars.get(0);
    return calendar == null ? null : calendar.getColor();
  }

  /**
   * Posts, as the manager who added it, one activity in the space's stream
   * saying a calendar was added to its agenda (EXO-90373): the members are told
   * there rather than by a notification. A failure is logged and never undoes
   * the subscription.
   *
   * @param space the space's identity
   * @param creatorIdentityId identity identifier of the manager
   * @param username the manager
   * @param calendarName the name of the calendar added
   * @param subscriptionId technical identifier of the subscription, for the log
   */
  private void announceInSpace(Identity space, long creatorIdentityId, String username, String calendarName, long subscriptionId) {
    if (activityManager == null) {
      return;
    }
    try {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setUserId(String.valueOf(creatorIdentityId));
      activity.setTitle(spaceActivityTitle(username, calendarName));
      activityManager.saveActivityNoReturn(space, activity);
    } catch (RuntimeException e) {
      LOG.warn("The space stream could not be told that calendar subscription {} was added", subscriptionId, e);
    }
  }

  /**
   * The text of the space activity, in the language of the manager who posts
   * it, the calendar name escaped: the stream renders the title as markup.
   *
   * @param username the manager
   * @param calendarName the name of the calendar added
   * @return the text
   */
  String spaceActivityTitle(String username, String calendarName) {
    String pattern = null;
    try {
      pattern = labelResolver.apply(username, SPACE_ACTIVITY_LABEL);
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("The space activity text could not be translated: {}", e.getMessage());
    }
    if (StringUtils.isBlank(pattern) || SPACE_ACTIVITY_LABEL.equals(pattern) || !pattern.contains("{0}")) {
      pattern = SPACE_ACTIVITY_DEFAULT;
    }
    return StringUtils.replace(pattern, "{0}", escapeMarkup(StringUtils.defaultString(calendarName)));
  }

  /**
   * A label of agenda's bundle in the language of a user, as the platform
   * resolves both.
   *
   * @param username the user
   * @param key the bundle key
   * @return the label, or the key when the bundle has none
   */
  private static String translatedLabel(String username, String key) {
    return Utils.getResourceBundleLabel(LocaleUtils.toLocale(Utils.getUserLanguage(username)), key);
  }

  /**
   * Replaces how labels are translated, for the tests, which run with no
   * container to read bundles from.
   *
   * @param labelResolver the user and the key to the label
   */
  void setLabelResolver(BiFunction<String, String, String> labelResolver) {
    this.labelResolver = labelResolver;
  }

  /**
   * Escapes the characters that change how markup is read.
   *
   * @param text the text
   * @return the escaped text
   */
  private static String escapeMarkup(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }

  /**
   * The identity identifier of an enabled user.
   *
   * @param username the user
   * @return the identifier
   * @throws IllegalAccessException when the user has no enabled identity
   */
  private long userIdentityId(String username) throws IllegalAccessException {
    Identity identity = StringUtils.isBlank(username) ? null : identityManager.getOrCreateUserIdentity(username);
    if (identity == null || identity.isDeleted() || !identity.isEnable()) {
      throw new IllegalAccessException("User " + username + " has no usable identity");
    }
    return Long.parseLong(identity.getId());
  }

  /**
   * A name as given: trimmed, blank as none.
   *
   * @param name the name
   * @return the name, or null
   */
  private static String cleanName(String name) {
    String trimmed = StringUtils.trimToNull(name);
    if (trimmed != null && trimmed.length() > MAX_NAME) {
      throw new IllegalArgumentException(NAME_TOO_LONG);
    }
    return trimmed;
  }

  /**
   * A colour as given: {@code #RRGGBB}, blank as none.
   *
   * @param color the colour
   * @return the colour, or null
   */
  private static String cleanColor(String color) {
    String trimmed = StringUtils.trimToNull(color);
    if (trimmed != null && !COLOR.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(INVALID_COLOR);
    }
    return trimmed;
  }

  /**
   * The name of a calendar the user did not name: the feed's own, else the host.
   *
   * @param feedName the feed's name
   * @param uri the URL
   * @return the name
   */
  private static String defaultName(String feedName, URI uri) {
    return StringUtils.left(StringUtils.isNotBlank(feedName) ? feedName : StringUtils.lowerCase(uri.getHost(), Locale.ENGLISH), MAX_NAME);
  }

  /**
   * The key of an owner's subscription to a URL: for a personal subscription
   * the owner is the user, so the keys stored before a space could subscribe
   * are unchanged (EXO-90373).
   *
   * @param ownerIdentityId the owner, a user or a space
   * @param uri the normalized URL
   * @return lowercase hexadecimal SHA-256
   */
  static String urlKey(long ownerIdentityId, URI uri) {
    return CalendarFeedParserAccess.sha256((ownerIdentityId + "\n" + uri).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The interval a stored subscription's feed advertised.
   *
   * @param subscription the subscription
   * @return the minutes, null for none
   */
  private static Integer refreshMinutesOf(CalendarSubscription subscription) {
    return subscription.getRefreshMinutes() > 0 ? subscription.getRefreshMinutes() : null;
  }

  /**
   * The interval a feed advertises, bounded.
   *
   * @param interval the advertised interval, may be null
   * @return minutes between {@link #MIN_REFRESH} and {@link #MAX_REFRESH}, null
   *         when none is advertised
   */
  static Integer refreshMinutes(Duration interval) {
    if (interval == null) {
      return null;
    }
    long minutes = Math.max(MIN_REFRESH.toMinutes(), Math.min(MAX_REFRESH.toMinutes(), interval.toMinutes()));
    return (int) minutes;
  }

  /**
   * The instant before which a claim is stale.
   *
   * @param now the current instant
   * @return the date
   */
  private static Date staleBefore(Instant now) {
    return Date.from(now.minus(CLAIM_STALE));
  }

  /**
   * Encrypts a URL with the platform codec; a URL that cannot be encrypted is not
   * stored at all.
   *
   * @param url the URL
   * @return the encrypted URL
   */
  private String encrypt(String url) {
    try {
      return codecInitializer.getCodec().encode(url);
    } catch (TokenServiceInitializationException e) {
      throw new IllegalStateException("The platform codec is not available: no calendar subscription can be stored", e);
    }
  }

  /**
   * Decrypts a stored URL.
   *
   * @param subscription the subscription
   * @return the URL, or null when it cannot be decrypted
   */
  private String decrypt(CalendarSubscription subscription) {
    if (StringUtils.isBlank(subscription.getUrlEncrypted())) {
      return null;
    }
    try {
      return codecInitializer.getCodec().decode(subscription.getUrlEncrypted());
    } catch (TokenServiceInitializationException | RuntimeException e) {
      LOG.warn("The URL of calendar subscription {} can no longer be decrypted ({})",
               subscription.getId(),
               e.getClass().getSimpleName());
      return null;
    }
  }

  /**
   * Deletes a calendar, and the events it holds, when it still exists.
   *
   * @param calendarId technical identifier
   */
  private void deleteCalendarQuietly(long calendarId) {
    try {
      if (agendaCalendarService.getCalendarById(calendarId) != null) {
        agendaCalendarService.deleteCalendarById(calendarId);
      }
    } catch (ObjectNotFoundException e) {
      LOG.debug("Calendar {} was already deleted", calendarId);
    }
  }

  /**
   * Asks the search index to read an event again.
   *
   * @param eventId technical identifier
   */
  private void reindex(long eventId) {
    if (indexingService != null) {
      indexingService.reindex(AgendaIndexingServiceConnector.INDEX, String.valueOf(eventId));
    }
  }

  /**
   * Asks the search index to forget an event.
   *
   * @param eventId technical identifier
   */
  private void unindex(long eventId) {
    if (indexingService != null) {
      indexingService.unindex(AgendaIndexingServiceConnector.INDEX, String.valueOf(eventId));
    }
  }

  /**
   * Digests shared with the parser.
   */
  private static final class CalendarFeedParserAccess {

    /**
     * Not instantiable.
     */
    private CalendarFeedParserAccess() {
    }

    /**
     * SHA-256 of bytes.
     *
     * @param bytes the bytes
     * @return lowercase hexadecimal digest
     */
    static String sha256(byte[] bytes) {
      try {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 is not available on this JVM", e);
      }
    }
  }

}
