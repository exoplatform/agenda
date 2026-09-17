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
package org.exoplatform.agenda.storage;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.dao.CalendarShareDAO;
import org.exoplatform.agenda.entity.CalendarShareEntity;
import org.exoplatform.agenda.model.CalendarShare;

import jakarta.persistence.PersistenceException;

/**
 * Maps calendar shares between their rows and {@link CalendarShare}, and
 * caches the two reads every ACL decision relies on (EXO-90357).
 * <p>
 * <b>Two caches, both keyed by one explicit identifier.</b>
 * {@value #BY_VIEWER_CACHE} holds, per reader identity, the identifiers of the
 * calendars shared with them: what every event and calendar read asks, once
 * per read, on the reader's own key — a viewer is never served another's
 * list. {@value #BY_CALENDAR_CACHE} holds, per calendar, its shares: what the
 * owner's drawer and the delivery reads ask. A write evicts exactly the keys it
 * touches, so a revoke makes the refusal immediate; the bulk deletions that
 * cannot name their keys evict the whole cache, which they may — they run on a
 * calendar or a user being deleted.
 * <p>
 * The cached reads are reached through a lazy self-reference from the other
 * methods of this bean: a plain {@code this} call bypasses the proxy and,
 * silently, the cache.
 */
@Component
public class CalendarShareStorage {

  /** Cache of the calendar identifiers shared with a reader, keyed by reader identity id. */
  public static final String   BY_VIEWER_CACHE   = "agenda.calendarShare.byViewer";

  /** Cache of the shares of a calendar, keyed by calendar id. */
  public static final String   BY_CALENDAR_CACHE = "agenda.calendarShare.byCalendar";

  /** Most shares one calendar listing reads. */
  public static final int      MAX_SHARES        = 500;

  private final CalendarShareDAO calendarShareDAO;

  private CalendarShareStorage   self;

  /**
   * Builds the storage.
   *
   * @param calendarShareDAO the repository of share rows
   */
  @Autowired
  public CalendarShareStorage(CalendarShareDAO calendarShareDAO) {
    this.calendarShareDAO = calendarShareDAO;
    this.self = this;
  }

  /**
   * Receives the proxied bean, so that the methods of this class reach the
   * cached reads through the cache rather than through {@code this}.
   *
   * @param self the proxied storage
   */
  @Autowired
  public void setSelf(@Lazy CalendarShareStorage self) {
    this.self = self;
  }

  /**
   * The identifiers of every calendar shared with a reader. Cached per reader;
   * loaded once under concurrent misses.
   *
   * @param viewerIdentityId identity identifier of the reader
   * @return the calendar identifiers, never null
   */
  @Cacheable(cacheNames = BY_VIEWER_CACHE, key = "#viewerIdentityId", sync = true)
  public List<Long> getSharedCalendarIds(long viewerIdentityId) {
    return new ArrayList<>(calendarShareDAO.findCalendarIdsByShareeIdentityId(viewerIdentityId));
  }

  /**
   * Whether a calendar is shared with a reader: one cache hit on the reader's
   * key.
   *
   * @param calendarId technical identifier of the calendar
   * @param viewerIdentityId identity identifier of the reader
   * @return true when a share exists
   */
  public boolean isSharedWith(long calendarId, long viewerIdentityId) {
    return self.getSharedCalendarIds(viewerIdentityId).contains(calendarId);
  }

  /**
   * The shares of a calendar, oldest first. Cached per calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return the shares, never null
   */
  @Cacheable(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId", sync = true)
  public List<CalendarShare> getShares(long calendarId) {
    return calendarShareDAO.findByCalendarIdOrderByCreatedDateAscIdAsc(calendarId, PageRequest.of(0, MAX_SHARES))
                           .stream()
                           .map(this::toModel)
                           .toList();
  }

  /**
   * The share of a calendar with one colleague, read through the calendar's
   * cached shares.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @return a copy of the share, or null when there is none
   */
  public CalendarShare getShare(long calendarId, long shareeIdentityId) {
    return self.getShares(calendarId)
               .stream()
               .filter(share -> share.getShareeIdentityId() == shareeIdentityId)
               .findFirst()
               .map(CalendarShare::clone)
               .orElse(null);
  }

  /**
   * The shares a colleague received, newest first. Not cached: read once per
   * panel load, and the hidden flag on it changes at the colleague's hand.
   *
   * @param shareeIdentityId identity identifier of the colleague
   * @param limit most shares to read
   * @return the shares
   */
  public List<CalendarShare> getSharesOfSharee(long shareeIdentityId, int limit) {
    return calendarShareDAO.findByShareeIdentityIdOrderByCreatedDateDescIdDesc(shareeIdentityId,
                                                                               PageRequest.of(0, Math.max(1, limit)))
                           .stream()
                           .map(this::toModel)
                           .toList();
  }

  /**
   * How many colleagues each calendar of an owner is shared with, from one
   * query.
   *
   * @param ownerIdentityId identity identifier owning the calendars
   * @return sharee counts by calendar identifier, absent when zero
   */
  public Map<Long, Long> countShareesByCalendar(long ownerIdentityId) {
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : calendarShareDAO.countByCalendarOfOwner(ownerIdentityId)) {
      counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }
    return counts;
  }

  /**
   * Writes a new share. Two owners' clicks — or one owner's double click —
   * recording the same share at the same moment both find no row, and the
   * unique key refuses the second insert; that refusal is not an error, the
   * row that won is returned. The insert is flushed so the refusal surfaces
   * here on every engine, and caught as Spring's translated exception and as
   * the raw JPA one, as {@code CalendarLinkStorage} does.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param grantedById identity identifier of the user recording the share
   * @param source where the record comes from
   * @param deliveredTo the channel carrying it, may be null
   * @param deliveryRef the channel's reference, may be null
   * @param createdDate when the share is created
   * @return the stored share
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, key = "#shareeIdentityId"),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId") })
  public CalendarShare save(long calendarId, // NOSONAR
                            long shareeIdentityId,
                            long grantedById,
                            CalendarShareSource source,
                            String deliveredTo,
                            String deliveryRef,
                            Date createdDate) {
    CalendarShareEntity entity = calendarShareDAO.findByCalendarIdAndShareeIdentityId(calendarId, shareeIdentityId);
    if (entity != null) {
      return toModel(entity);
    }
    entity = new CalendarShareEntity();
    entity.setCalendarId(calendarId);
    entity.setShareeIdentityId(shareeIdentityId);
    entity.setGrantedById(grantedById);
    entity.setSource(source);
    entity.setDeliveredTo(deliveredTo);
    entity.setDeliveryRef(deliveryRef);
    entity.setCreatedDate(createdDate);
    try {
      return toModel(calendarShareDAO.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | PersistenceException e) {
      entity = calendarShareDAO.findByCalendarIdAndShareeIdentityId(calendarId, shareeIdentityId);
      if (entity == null) {
        throw e;
      }
      return toModel(entity);
    }
  }

  /**
   * Records which channel carries a share, and under which reference; null
   * both to record that none does.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param deliveredTo the channel, or null
   * @param deliveryRef the channel's reference, or null
   * @return the updated share, or null when there is no such share
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, key = "#shareeIdentityId"),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId") })
  public CalendarShare setDelivery(long calendarId, long shareeIdentityId, String deliveredTo, String deliveryRef) {
    CalendarShareEntity entity = calendarShareDAO.findByCalendarIdAndShareeIdentityId(calendarId, shareeIdentityId);
    if (entity == null) {
      return null;
    }
    entity.setDeliveredTo(deliveredTo);
    entity.setDeliveryRef(deliveryRef);
    return toModel(calendarShareDAO.save(entity));
  }

  /**
   * Records the sharee's choice to hide the calendar, or to show it again.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param hidden whether hidden
   * @return the updated share, or null when there is no such share
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, key = "#shareeIdentityId"),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId") })
  public CalendarShare setHidden(long calendarId, long shareeIdentityId, boolean hidden) {
    CalendarShareEntity entity = calendarShareDAO.findByCalendarIdAndShareeIdentityId(calendarId, shareeIdentityId);
    if (entity == null) {
      return null;
    }
    entity.setHidden(hidden);
    return toModel(calendarShareDAO.save(entity));
  }

  /**
   * Deletes the share of a calendar with one colleague.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @return true when a share was deleted
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, key = "#shareeIdentityId"),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId") })
  public boolean delete(long calendarId, long shareeIdentityId) {
    CalendarShareEntity entity = calendarShareDAO.findByCalendarIdAndShareeIdentityId(calendarId, shareeIdentityId);
    if (entity == null) {
      return false;
    }
    calendarShareDAO.delete(entity);
    calendarShareDAO.flush();
    return true;
  }

  /**
   * Deletes every share of a calendar. The sharees are not read first, so
   * every reader's cached list is dropped.
   *
   * @param calendarId technical identifier of the calendar
   * @return how many shares went
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, allEntries = true),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, key = "#calendarId") })
  public int deleteByCalendarId(long calendarId) {
    return calendarShareDAO.deleteByCalendarId(calendarId);
  }

  /**
   * Deletes every share a colleague received, and every share of the
   * calendars they own.
   *
   * @param identityId identity identifier of the user
   * @return how many shares went
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, allEntries = true),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, allEntries = true) })
  public int deleteOfUser(long identityId) {
    return calendarShareDAO.deleteByShareeIdentityId(identityId) + calendarShareDAO.deleteByCalendarOwner(identityId);
  }

  /**
   * Forgets that a channel carries the shares of an owner's calendars.
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param channelId the channel
   * @return how many shares were cleared
   */
  @Caching(evict = {
      @CacheEvict(cacheNames = BY_VIEWER_CACHE, allEntries = true),
      @CacheEvict(cacheNames = BY_CALENDAR_CACHE, allEntries = true) })
  public int clearDelivery(long ownerIdentityId, String channelId) {
    List<CalendarShareEntity> entities = calendarShareDAO.findByCalendarOwnerAndDeliveredTo(ownerIdentityId, channelId);
    for (CalendarShareEntity entity : entities) {
      entity.setDeliveredTo(null);
      entity.setDeliveryRef(null);
    }
    calendarShareDAO.saveAll(entities);
    return entities.size();
  }

  /**
   * Maps a row to its model; the delivery warning is the service's to set.
   *
   * @param entity the row, may be null
   * @return the model, or null for no row
   */
  private CalendarShare toModel(CalendarShareEntity entity) {
    if (entity == null) {
      return null;
    }
    return new CalendarShare(entity.getId() == null ? 0 : entity.getId(),
                             entity.getCalendarId(),
                             entity.getShareeIdentityId(),
                             entity.getGrantedById(),
                             entity.getCreatedDate() == null ? 0 : entity.getCreatedDate().getTime(),
                             entity.getSource(),
                             entity.getDeliveredTo(),
                             entity.getDeliveryRef(),
                             entity.isHidden(),
                             null);
  }

}
