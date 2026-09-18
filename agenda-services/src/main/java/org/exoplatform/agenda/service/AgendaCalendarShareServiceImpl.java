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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ChannelDelivery;
import org.exoplatform.agenda.model.ChannelShares;
import org.exoplatform.agenda.model.ExternalShare;
import org.exoplatform.agenda.plugin.CalendarShareChannelPlugin;
import org.exoplatform.agenda.storage.CalendarShareStorage;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Holds every rule of calendar sharing (EXO-90357): who shares what with whom,
 * what a sharee may read, and how a delivery channel is composed with the eXo
 * record.
 * <p>
 * <b>Sharing</b> is the owner's: a personal calendar — its owner is a user,
 * not a space — that is not a subscription (EXO-90278). A space calendar is
 * read by its members already, and sharing one is a later decision. The sharee
 * is a usable user of this deployment other than the owner.
 * <p>
 * <b>Delivery</b>: the record is written first and stands whatever the
 * channels answer. Each registered {@link CalendarShareChannelPlugin} is asked
 * to carry the share; the first that delivers is recorded on the row, a
 * failure is logged at WARN and leaves the row undelivered — the owner is told
 * nothing and nothing is retried on its own — and a channel that says the
 * share is none of its business leaves the row eXo-only. A grant a channel
 * holds for a colleague that eXo has no record of is recorded silently, as an
 * adopted share, when the owner lists their shares. The plugins are collected
 * from the Spring context at the moment they are asked, so an add-on booting
 * after agenda is found all the same — the idiom of
 * {@link AgendaRemoteCopyService}.
 * <p>
 * <b>Revoking</b> withdraws from the channel first, then deletes the row
 * whatever the channel answered: a grant the channel could not remove shows
 * up under the external shares, with its own removal, rather than keeping a
 * colleague on the eXo record the owner asked to end.
 */
@Service
public class AgendaCalendarShareServiceImpl implements AgendaCalendarShareService {

  /** Most shares one "shared with me" listing reads. */
  public static final int         MAX_SHARED_WITH_ME = 200;

  private static final Log        LOG                = ExoLogger.getLogger(AgendaCalendarShareServiceImpl.class);

  private final CalendarShareStorage  calendarShareStorage;

  private final AgendaCalendarService agendaCalendarService;

  private final IdentityManager       identityManager;

  private final ListenerService       listenerService;

  private final ApplicationContext    applicationContext;

  private Clock                       clock              = Clock.systemUTC();

  /**
   * Builds the service.
   *
   * @param calendarShareStorage storage of the share records
   * @param agendaCalendarService reads calendars
   * @param identityManager resolves users
   * @param listenerService the platform event bus, told of every share and
   *          revoke so that notifications and channels can react
   * @param applicationContext the Spring context of this WAR, from which the
   *          delivery channels are collected when asked
   */
  @Autowired
  public AgendaCalendarShareServiceImpl(CalendarShareStorage calendarShareStorage,
                                        AgendaCalendarService agendaCalendarService,
                                        IdentityManager identityManager,
                                        ListenerService listenerService,
                                        ApplicationContext applicationContext) {
    this.calendarShareStorage = calendarShareStorage;
    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
    this.listenerService = listenerService;
    this.applicationContext = applicationContext;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarShare share(long calendarId,
                             String shareeUsername,
                             CalendarShareLevel level,
                             String ownerUsername) throws ObjectNotFoundException, IllegalAccessException {
    long ownerIdentityId = userIdentityId(ownerUsername);
    Calendar calendar = getOwnedCalendar(calendarId, ownerIdentityId, ownerUsername);
    Identity sharee = StringUtils.isBlank(shareeUsername) ? null : identityManager.getOrCreateUserIdentity(shareeUsername);
    long shareeIdentityId = validSharee(sharee, ownerIdentityId);
    CalendarShare existing = calendarShareStorage.getShare(calendar.getId(), shareeIdentityId);
    // An existing record keeps the level it was levelled at: sharing again is a
    // retry of the delivery (EXO-90357) and must not widen a right on its own —
    // setLevel is the one call that changes a level (EXO-90378)
    CalendarShare share = existing != null ? existing
                                           : calendarShareStorage.save(calendar.getId(),
                                                                       shareeIdentityId,
                                                                       ownerIdentityId,
                                                                       level == null ? CalendarShareLevel.VIEW : level,
                                                                       CalendarShareSource.EXO,
                                                                       null,
                                                                       null,
                                                                       Date.from(clock.instant()));
    if (existing == null) {
      broadcast(CALENDAR_SHARED_EVENT, share, ownerIdentityId);
    }
    return StringUtils.isBlank(share.getDeliveredTo()) ? deliver(share, ownerUsername) : share;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarShare setLevel(long calendarId,
                                long shareeIdentityId,
                                CalendarShareLevel level,
                                String ownerUsername) throws ObjectNotFoundException, IllegalAccessException {
    if (level == null) {
      throw new IllegalArgumentException(LEVEL_MANDATORY);
    }
    long ownerIdentityId = userIdentityId(ownerUsername);
    Calendar calendar = getOwnedCalendar(calendarId, ownerIdentityId, ownerUsername);
    CalendarShare existing = calendarShareStorage.getShare(calendar.getId(), shareeIdentityId);
    if (existing == null) {
      throw new ObjectNotFoundException("Calendar " + calendarId + " is not shared with identity " + shareeIdentityId);
    }
    CalendarShare share = existing.getLevel() == level ? existing
                                                       : calendarShareStorage.setLevel(calendar.getId(),
                                                                                       shareeIdentityId,
                                                                                       level);
    if (share == null) {
      throw new ObjectNotFoundException("Calendar " + calendarId + " is not shared with identity " + shareeIdentityId);
    }
    if (existing.getLevel() != level) {
      broadcast(CALENDAR_SHARE_LEVEL_CHANGED_EVENT, share, ownerIdentityId);
    }
    // The channels are asked whatever the eXo record did: a level change is a
    // fresh chance to deliver a share that never was delivered, and a delivered
    // share must have its grant reconciled to the level it now carries
    return deliver(share, ownerUsername);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void unshare(long calendarId, long shareeIdentityId, String ownerUsername) throws ObjectNotFoundException,
                                                                                    IllegalAccessException {
    long ownerIdentityId = userIdentityId(ownerUsername);
    Calendar calendar = getOwnedCalendar(calendarId, ownerIdentityId, ownerUsername);
    CalendarShare share = calendarShareStorage.getShare(calendar.getId(), shareeIdentityId);
    if (share == null) {
      return;
    }
    withdraw(share, ownerUsername);
    calendarShareStorage.delete(calendar.getId(), shareeIdentityId);
    broadcast(CALENDAR_UNSHARED_EVENT, share, ownerIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CalendarShare> getShares(long calendarId, String ownerUsername) throws ObjectNotFoundException,
                                                                                IllegalAccessException {
    Calendar calendar = getOwnedCalendar(calendarId, userIdentityId(ownerUsername), ownerUsername);
    return calendarShareStorage.getShares(calendar.getId()).stream().map(CalendarShare::clone).toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ExternalShare> getExternalShares(long calendarId, String ownerUsername) throws ObjectNotFoundException,
                                                                                        IllegalAccessException {
    return getChannelShares(calendarId, ownerUsername).shares();
  }

  /**
   * {@inheritDoc}
   * <p>
   * One pass over the channels, one ask each (EXO-90385): a channel answers
   * the grants its server holds and the meeting-copies flag out of the same
   * read, so the drawer no longer makes every channel talk to its server
   * twice. A channel that throws contributes nothing and no warning, exactly
   * as the two separate asks did — the list was empty and the flag false.
   */
  @Override
  public ChannelShares getChannelShares(long calendarId, String ownerUsername) throws ObjectNotFoundException,
                                                                                 IllegalAccessException {
    long ownerIdentityId = userIdentityId(ownerUsername);
    Calendar calendar = getOwnedCalendar(calendarId, ownerIdentityId, ownerUsername);
    List<Long> recorded = new ArrayList<>(calendarShareStorage.getShares(calendar.getId())
                                                              .stream()
                                                              .map(CalendarShare::getShareeIdentityId)
                                                              .toList());
    List<ExternalShare> external = new ArrayList<>();
    boolean meetingCopies = false;
    for (CalendarShareChannelPlugin channel : channels()) {
      try {
        ChannelShares answer = channel.listShares(calendar.getId(), ownerUsername, recorded);
        if (answer == null) {
          continue;
        }
        // Any channel saying so is enough: the warning is about this calendar
        // holding the copies, whichever channel writes them
        meetingCopies = meetingCopies || answer.meetingCopies();
        if (answer.shares() == null) {
          continue;
        }
        for (ExternalShare share : answer.shares()) {
          if (share == null || recorded.contains(share.getShareeIdentityId())) {
            continue;
          }
          // The channel's own, qualified id when it gave one — caldav:<serverId>
          // — so the row names the server the grant is on and a removal
          // reaches the same channel by prefix; the bare id otherwise
          if (StringUtils.isBlank(share.getChannelId())) {
            share.setChannelId(channelId(channel));
          }
          if (record(calendar, share, ownerIdentityId)) {
            recorded.add(share.getShareeIdentityId());
          } else {
            external.add(share);
          }
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.warn("Channel {} could not list the external shares of calendar {}", channel.getClass().getName(), calendarId, e);
      }
    }
    return new ChannelShares(external, meetingCopies);
  }

  /**
   * Records, as an adopted share, a read-only grant a channel holds for a
   * colleague of this deployment — the "one list, recorded silently" rule:
   * a share made on the calendar server is a share like any other. The
   * server is not touched, the record is idempotent through the storage's
   * unique key, and nobody is notified: the colleague already had access,
   * and a listing must be safe to repeat. A colleague who cannot be shared
   * with — gone, disabled, the owner — is left to the channel's list.
   *
   * @param calendar the calendar
   * @param share the channel's share
   * @param ownerIdentityId the owner
   * @return true when the share is now an eXo record, false when it stays
   *         outside eXo
   */
  private boolean record(Calendar calendar, ExternalShare share, long ownerIdentityId) {
    CalendarShareLevel level = adoptableLevel(share);
    if (share.getShareeIdentityId() <= 0 || level == null || StringUtils.isBlank(share.getDeliveryRef())) {
      return false;
    }
    try {
      long shareeId = validSharee(identityManager.getIdentity(String.valueOf(share.getShareeIdentityId())), ownerIdentityId);
      CalendarShare recorded = calendarShareStorage.save(calendar.getId(),
                                                         shareeId,
                                                         ownerIdentityId,
                                                         level,
                                                         CalendarShareSource.ADOPTED,
                                                         share.getChannelId(),
                                                         share.getDeliveryRef(),
                                                         Date.from(clock.instant()));
      return recorded != null;
    } catch (RuntimeException e) {
      LOG.debug("The share of calendar {} with {} held on {} is not recorded in eXo: {}",
                calendar.getId(),
                share.getShareeIdentityId(),
                share.getChannelId(),
                e.getMessage());
      return false;
    }
  }

  /**
   * The level a grant held on a channel's server can be adopted as
   * (EXO-90378), null for a grant eXo does not write and therefore never
   * adopts.
   * <p>
   * A read-only grant is a {@code VIEW} share, as it was in EXO-90357. A grant
   * the channel reports as {@code EDIT} is one of exactly the shape eXo writes
   * for an edit share — the channel is what knows that shape, and it says so
   * here rather than handing over privileges agenda would have to read.
   * Anything else stays outside eXo, listed to the owner as access held
   * elsewhere: adopting a grant eXo cannot reproduce would make a record whose
   * level lies about what the server allows.
   *
   * @param share the grant as the channel listed it
   * @return the level to record it at, or null to leave it outside eXo
   */
  private static CalendarShareLevel adoptableLevel(ExternalShare share) {
    if (share.isReadOnly()) {
      return CalendarShareLevel.VIEW;
    }
    return CalendarShareLevel.EDIT.name().equalsIgnoreCase(share.getAccess()) ? CalendarShareLevel.EDIT : null;
  }

  /**
   * {@inheritDoc}
   * <p>
   * A channel that throws is read as copying nothing: the confirmation is a
   * courtesy to the owner, not a gate, and a channel that cannot answer must
   * not stop the share.
   */
  @Override
  public boolean holdsMeetingCopies(long calendarId, String ownerUsername) throws ObjectNotFoundException, IllegalAccessException {
    Calendar calendar = getOwnedCalendar(calendarId, userIdentityId(ownerUsername), ownerUsername);
    for (CalendarShareChannelPlugin channel : channels()) {
      try {
        if (channel.holdsMeetingCopies(calendar.getId(), ownerUsername)) {
          return true;
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("Channel {} could not say whether calendar {} holds meeting copies; it is read as not", channel.getClass().getName(), calendarId, e);
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * A channel that answers false — the server refused, or could not be
   * reached — is an {@link IllegalStateException} bearing
   * {@link #SHARE_NOT_REMOVED}: the request was well formed and allowed, the
   * state on the server is what stands in the way.
   */
  @Override
  public void removeExternalShare(long calendarId,
                                  String channelId,
                                  String externalId,
                                  String ownerUsername) throws ObjectNotFoundException, IllegalAccessException {
    Calendar calendar = getOwnedCalendar(calendarId, userIdentityId(ownerUsername), ownerUsername);
    CalendarShareChannelPlugin channel = channel(channelId);
    if (channel == null) {
      throw new ObjectNotFoundException(NO_CHANNEL);
    }
    if (StringUtils.isBlank(externalId)) {
      throw new IllegalArgumentException(SHARE_NOT_FOUND);
    }
    if (!channel.removeExternalShare(calendar.getId(), externalId, ownerUsername)) {
      LOG.debug("Channel {} could not remove the share {} of calendar {}", channelId, externalId, calendarId);
      throw new IllegalStateException(SHARE_NOT_REMOVED);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, Long> countShareesByCalendar(String ownerUsername) throws IllegalAccessException {
    return calendarShareStorage.countShareesByCalendar(userIdentityId(ownerUsername));
  }

  /**
   * {@inheritDoc}
   * <p>
   * A share whose calendar is gone, or whose owner is gone or no longer a user,
   * is left out rather than drawn: the calendar cache answers the check.
   */
  @Override
  public List<CalendarShare> getSharedWithMe(String username) throws IllegalAccessException {
    long shareeIdentityId = userIdentityId(username);
    List<CalendarShare> shares = new ArrayList<>();
    for (CalendarShare share : calendarShareStorage.getSharesOfSharee(shareeIdentityId, MAX_SHARED_WITH_ME)) {
      Calendar calendar = agendaCalendarService.getCalendarById(share.getCalendarId());
      if (calendar != null && !calendar.isDeleted() && isUsable(identityManager.getIdentity(String.valueOf(calendar.getOwnerId())))) {
        shares.add(share);
      }
    }
    return shares;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setHidden(long calendarId, String username, boolean hidden) throws ObjectNotFoundException,
                                                                          IllegalAccessException {
    long shareeIdentityId = userIdentityId(username);
    if (calendarShareStorage.setHidden(calendarId, shareeIdentityId, hidden) == null) {
      throw new ObjectNotFoundException(SHARE_NOT_FOUND);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSharedWith(long calendarId, long viewerIdentityId) {
    return calendarId > 0 && viewerIdentityId > 0 && calendarShareStorage.isSharedWith(calendarId, viewerIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CalendarShareLevel getShareLevel(long calendarId, long viewerIdentityId) {
    return calendarId <= 0 || viewerIdentityId <= 0 ? null : calendarShareStorage.getShareLevel(calendarId, viewerIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, CalendarShareLevel> getShareLevels(long viewerIdentityId) {
    return viewerIdentityId <= 0 ? Map.of() : calendarShareStorage.getShareLevels(viewerIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getSharedCalendarIds(long viewerIdentityId) {
    return viewerIdentityId > 0 ? new ArrayList<>(calendarShareStorage.getSharedCalendarIds(viewerIdentityId)) : List.of();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteShares(long calendarId) {
    if (calendarId > 0) {
      calendarShareStorage.deleteByCalendarId(calendarId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteSharesOfUser(long identityId) {
    if (identityId > 0) {
      calendarShareStorage.deleteOfUser(identityId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clearDelivery(long ownerIdentityId, String channelId) {
    if (ownerIdentityId > 0 && StringUtils.isNotBlank(channelId)) {
      calendarShareStorage.clearDelivery(ownerIdentityId, channelId);
    }
  }

  /**
   * Replaces the clock, for tests pinning dates.
   *
   * @param clock the clock to read the current instant from
   */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * Asks every channel to carry a share and records the first that does.
   * Delivery is invisible to the owner: a channel that fails, or throws, is
   * logged at WARN with the owner, the colleague, the calendar and the cause,
   * and the record stands with {@code deliveredTo} null, so a delivery can be
   * attempted later. Nothing is retried on its own.
   *
   * @param share the record
   * @param ownerUsername the owner
   * @return the record, updated with its delivery when one channel took it
   */
  private CalendarShare deliver(CalendarShare share, String ownerUsername) {
    for (CalendarShareChannelPlugin channel : channels()) {
      ChannelDelivery delivery;
      try {
        delivery = channel.deliver(share, ownerUsername);
      } catch (RuntimeException | LinkageError e) {
        LOG.warn("Channel {} failed to carry the share of calendar {} by {} with colleague {}; the share stands in eXo, not delivered",
                 channel.getClass().getName(),
                 share.getCalendarId(),
                 ownerUsername,
                 share.getShareeIdentityId(),
                 e);
        continue;
      }
      if (delivery == null || delivery.getStatus() == ChannelDelivery.Status.NOT_APPLICABLE) {
        continue;
      }
      if (delivery.getStatus() == ChannelDelivery.Status.DELIVERED) {
        String channelId = StringUtils.defaultIfBlank(delivery.getChannelId(), channelId(channel));
        // A channel that carried the share at a narrower level than the record
        // asked for (EXO-90378) is not a failure: the colleague edits in eXo,
        // and the server simply shows them less. Logged, never surfaced — the
        // owner is told nothing about a delivery, at any level.
        if (delivery.getDeliveredLevel() != null && delivery.getDeliveredLevel() != share.getLevel()) {
          LOG.warn("Channel {} carried the share of calendar {} by {} with colleague {} at level {} rather than {};"
              + " the eXo level stands and decides every right in eXo",
                   channelId,
                   share.getCalendarId(),
                   ownerUsername,
                   share.getShareeIdentityId(),
                   delivery.getDeliveredLevel(),
                   share.getLevel());
        }
        CalendarShare delivered = calendarShareStorage.setDelivery(share.getCalendarId(),
                                                                   share.getShareeIdentityId(),
                                                                   channelId,
                                                                   delivery.getDeliveryRef());
        return delivered == null ? share : delivered;
      }
      LOG.warn("Channel {} did not carry the share of calendar {} by {} with colleague {}: {}; the share stands in eXo, not delivered",
               channelId(channel),
               share.getCalendarId(),
               ownerUsername,
               share.getShareeIdentityId(),
               StringUtils.defaultIfBlank(delivery.getFailureCode(), "DELIVERY_FAILED"));
    }
    return share;
  }

  /**
   * Withdraws a share from the channel that carries it, if any. A channel
   * that answers false or throws does not stop the revoke.
   *
   * @param share the record
   * @param ownerUsername the owner
   */
  private void withdraw(CalendarShare share, String ownerUsername) {
    if (StringUtils.isBlank(share.getDeliveredTo())) {
      return;
    }
    CalendarShareChannelPlugin channel = channel(share.getDeliveredTo());
    if (channel == null) {
      LOG.debug("No channel {} to withdraw the share of calendar {} with {} from; the record is deleted anyway",
                share.getDeliveredTo(),
                share.getCalendarId(),
                share.getShareeIdentityId());
      return;
    }
    try {
      if (!channel.withdraw(share, ownerUsername)) {
        LOG.warn("Channel {} could not withdraw the share of calendar {} with {}; the eXo record is deleted anyway",
                 share.getDeliveredTo(),
                 share.getCalendarId(),
                 share.getShareeIdentityId());
      }
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("Channel {} failed to withdraw the share of calendar {} with {}; the eXo record is deleted anyway",
               share.getDeliveredTo(),
               share.getCalendarId(),
               share.getShareeIdentityId(),
               e);
    }
  }

  /**
   * The registered channels, empty when none is installed or the context
   * cannot be asked.
   *
   * @return the channels, in no particular order
   */
  private Collection<CalendarShareChannelPlugin> channels() {
    try {
      return applicationContext == null ? List.of() : applicationContext.getBeansOfType(CalendarShareChannelPlugin.class).values();
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("The calendar share channels could not be listed; the share stays in eXo only", e);
      return List.of();
    }
  }

  /**
   * The channel bearing an identifier: the one whose id a record's
   * {@code deliveredTo} is, or qualifies — a channel answering {@code caldav}
   * stamps its deliveries {@code caldav:<serverId>} to say which server holds
   * the grant.
   *
   * @param channelId the identifier a record carries
   * @return the channel, or null
   */
  private CalendarShareChannelPlugin channel(String channelId) {
    if (StringUtils.isBlank(channelId)) {
      return null;
    }
    return channels().stream()
                     .filter(channel -> carries(channelId(channel), channelId))
                     .findFirst()
                     .orElse(null);
  }

  /**
   * Whether a channel's id is, or is the prefix of, a delivery's channel id.
   *
   * @param id the channel's id
   * @param deliveredTo the delivery's channel id
   * @return true when the channel carried the delivery
   */
  private static boolean carries(String id, String deliveredTo) {
    return StringUtils.equals(id, deliveredTo) || StringUtils.startsWith(deliveredTo, id + ":");
  }

  /**
   * A channel's identifier, read defensively.
   *
   * @param channel the channel
   * @return its identifier, or its class name when it cannot say
   */
  private String channelId(CalendarShareChannelPlugin channel) {
    try {
      return StringUtils.defaultIfBlank(channel.id(), channel.getClass().getName());
    } catch (RuntimeException | LinkageError e) {
      return channel.getClass().getName();
    }
  }

  /**
   * Reads a calendar the user owns and may share: it exists, its owner is the
   * user — a user, so never a space calendar — and it is not a subscription.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerIdentityId identity identifier of the user asking
   * @param ownerUsername the user asking, for messages
   * @return the calendar
   * @throws ObjectNotFoundException when there is no such calendar
   * @throws IllegalAccessException when the user may not share it
   */
  private Calendar getOwnedCalendar(long calendarId, long ownerIdentityId, String ownerUsername) throws ObjectNotFoundException,
                                                                                                 IllegalAccessException {
    if (calendarId <= 0) {
      throw new IllegalArgumentException(INVALID_CALENDAR);
    }
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null || calendar.isDeleted()) {
      throw new ObjectNotFoundException("Calendar with id " + calendarId + " wasn't found");
    }
    if (calendar.getOwnerId() != ownerIdentityId) {
      throw new IllegalAccessException("User " + ownerUsername + " is not the owner of calendar " + calendarId);
    }
    if (calendar.isSubscription()) {
      throw new IllegalAccessException("Calendar " + calendarId + " is a subscribed calendar and cannot be shared");
    }
    return calendar;
  }

  /**
   * Checks a sharee: a usable user of this deployment, other than the owner.
   *
   * @param sharee the identity, may be null
   * @param ownerIdentityId identity identifier of the owner
   * @return the sharee's identity identifier
   * @throws IllegalArgumentException with the message code of the refusal
   */
  private long validSharee(Identity sharee, long ownerIdentityId) {
    if (sharee == null || !sharee.isUser()) {
      throw new IllegalArgumentException(SHAREE_UNKNOWN);
    }
    if (!isUsable(sharee)) {
      throw new IllegalArgumentException(SHAREE_DISABLED);
    }
    long shareeIdentityId = Long.parseLong(sharee.getId());
    if (shareeIdentityId == ownerIdentityId) {
      throw new IllegalArgumentException(SHAREE_IS_OWNER);
    }
    return shareeIdentityId;
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
    if (!isUsable(identity) || !identity.isUser()) {
      throw new IllegalAccessException("User " + username + " has no usable identity");
    }
    return Long.parseLong(identity.getId());
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
   * Tells the platform of a share recorded or revoked. A listener that fails
   * must not fail the share.
   *
   * @param eventName the event
   * @param share the record
   * @param ownerIdentityId identity identifier of the owner
   */
  private void broadcast(String eventName, CalendarShare share, long ownerIdentityId) {
    try {
      listenerService.broadcast(eventName, share.clone(), ownerIdentityId);
    } catch (Exception e) { // NOSONAR the bus declares Exception
      LOG.warn("Error broadcasting {} for calendar {} and sharee {}", eventName, share.getCalendarId(), share.getShareeIdentityId(), e);
    }
  }

}
