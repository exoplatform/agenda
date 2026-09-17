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

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.dao.CalendarSubscriptionDAO;
import org.exoplatform.agenda.dao.CalendarSubscriptionEventDAO;
import org.exoplatform.agenda.entity.CalendarSubscriptionEntity;
import org.exoplatform.agenda.entity.CalendarSubscriptionEventEntity;
import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.model.CalendarSubscriptionEvent;

import jakarta.persistence.PersistenceException;

/**
 * Maps calendar subscriptions and the occurrences they imported between their
 * rows and their models (EXO-90278). No cache: the job reads a row right after
 * claiming it, and must read what the claim just wrote.
 */
@Component
public class CalendarSubscriptionStorage {

  /**
   * Most occurrence rows read for one subscription: comfortably above what an
   * import keeps, so that the rows of occurrences a feed dropped are read too.
   */
  public static final int                    MAX_EVENT_ROWS = 10000;

  private final CalendarSubscriptionDAO      subscriptionDAO;

  private final CalendarSubscriptionEventDAO subscriptionEventDAO;

  /**
   * Builds the storage.
   *
   * @param subscriptionDAO the subscription rows
   * @param subscriptionEventDAO the occurrence rows
   */
  @Autowired
  public CalendarSubscriptionStorage(CalendarSubscriptionDAO subscriptionDAO, CalendarSubscriptionEventDAO subscriptionEventDAO) {
    this.subscriptionDAO = subscriptionDAO;
    this.subscriptionEventDAO = subscriptionEventDAO;
  }

  /**
   * Reads a subscription.
   *
   * @param id technical identifier
   * @return the subscription, or null
   */
  public CalendarSubscription getById(long id) {
    return toModel(subscriptionDAO.findById(id).orElse(null));
  }

  /**
   * Reads the subscription of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return the subscription, or null
   */
  public CalendarSubscription getByCalendarId(long calendarId) {
    return toModel(subscriptionDAO.findByCalendarId(calendarId));
  }

  /**
   * Reads an owner's subscription to a URL.
   *
   * @param urlKey the key of the owner and the URL
   * @return the subscription, or null
   */
  public CalendarSubscription getByUrlKey(String urlKey) {
    return toModel(subscriptionDAO.findByUrlKey(urlKey));
  }

  /**
   * Reads an owner's subscriptions, oldest first: a user's personal ones, or a
   * space's (EXO-90373).
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param limit most subscriptions read
   * @return the subscriptions
   */
  public List<CalendarSubscription> getByOwner(long ownerIdentityId, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return subscriptionDAO.findByOwnerIdentityId(ownerIdentityId,
                                                 PageRequest.of(0, limit, Sort.by("createdDate").ascending().and(Sort.by("id"))))
                          .stream()
                          .map(this::toModel)
                          .toList();
  }

  /**
   * Reads the calendars an owner's subscriptions fill (EXO-90373).
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param limit most calendars read
   * @return technical identifiers of the calendars
   */
  public List<Long> getCalendarIdsByOwner(long ownerIdentityId, int limit) {
    return limit <= 0 ? List.of() : subscriptionDAO.findCalendarIdsByOwnerIdentityId(ownerIdentityId, PageRequest.of(0, limit));
  }

  /**
   * Inserts a subscription. The insert is flushed, so that the engine's refusal
   * of a second subscription of the owner to the URL surfaces here.
   *
   * @param subscription the subscription, without identifier
   * @return the stored subscription, or null when a subscription with the same
   *         URL key or calendar already exists
   */
  public CalendarSubscription create(CalendarSubscription subscription) {
    CalendarSubscriptionEntity entity = new CalendarSubscriptionEntity();
    entity.setCalendarId(subscription.getCalendarId());
    entity.setUserIdentityId(subscription.getUserIdentityId());
    entity.setOwnerIdentityId(subscription.getOwnerIdentityId());
    entity.setUrlEncrypted(subscription.getUrlEncrypted());
    entity.setUrlKey(subscription.getUrlKey());
    entity.setNextRefreshDate(date(subscription.getNextRefreshDate()));
    entity.setClaimedBy(subscription.getClaimedBy());
    entity.setClaimedDate(subscription.getClaimedDate() == 0 ? null : date(subscription.getClaimedDate()));
    entity.setCreatedDate(date(subscription.getCreatedDate()));
    try {
      return toModel(subscriptionDAO.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | PersistenceException e) {
      return null;
    }
  }

  /**
   * Points a subscription at a new URL, due at once.
   *
   * @param id technical identifier
   * @param urlEncrypted the new URL encrypted
   * @param urlKey the key of the new URL
   * @param now the instant it becomes due
   * @return false when the owner already subscribes to that URL
   */
  public boolean updateUrl(long id, String urlEncrypted, String urlKey, Date now) {
    try {
      return subscriptionDAO.updateUrl(id, urlEncrypted, urlKey, now) > 0;
    } catch (DataIntegrityViolationException | PersistenceException e) {
      return false;
    }
  }

  /**
   * Lists the subscriptions due and unclaimed.
   *
   * @param now the current instant
   * @param staleBefore a claim older than this no longer counts
   * @param limit the batch
   * @return technical identifiers, the longest waiting first
   */
  public List<Long> getDueIds(Date now, Date staleBefore, int limit) {
    return limit <= 0 ? List.of() : subscriptionDAO.findDueIds(now, staleBefore, PageRequest.of(0, limit));
  }

  /**
   * Claims a due subscription for a node.
   *
   * @param id technical identifier
   * @param node the node
   * @param now the current instant
   * @param staleBefore a claim older than this may be taken over
   * @return true when the node now holds it
   */
  public boolean claimDue(long id, String node, Date now, Date staleBefore) {
    return subscriptionDAO.claimDue(id, node, now, staleBefore) == 1;
  }

  /**
   * Claims a subscription for a node, due or not.
   *
   * @param id technical identifier
   * @param node the node
   * @param now the current instant
   * @param staleBefore a claim older than this may be taken over
   * @return true when the node now holds it
   */
  public boolean claim(long id, String node, Date now, Date staleBefore) {
    return subscriptionDAO.claim(id, node, now, staleBefore) == 1;
  }

  /**
   * Releases a node's claim.
   *
   * @param id technical identifier
   * @param node the node
   * @return true when released
   */
  public boolean release(long id, String node) {
    return subscriptionDAO.release(id, node) == 1;
  }

  /**
   * Records a successful refresh and releases the claim.
   *
   * @param id technical identifier
   * @param node the node holding the claim
   * @param urlKey the key of the URL read
   * @param etag the entity tag read
   * @param lastModified the Last-Modified header read
   * @param contentHash digest of the body
   * @param refreshMinutes interval the feed advertised, null for none
   * @param attempt when the refresh ran
   * @param truncated whether part of the feed was left out
   * @param nextRefreshDate when the next refresh is due
   * @return true when recorded
   */
  public boolean recordSuccess(long id, // NOSONAR every value is a column of the outcome
                               String node,
                               String urlKey,
                               String etag,
                               String lastModified,
                               String contentHash,
                               Integer refreshMinutes,
                               Date attempt,
                               boolean truncated,
                               Date nextRefreshDate) {
    return subscriptionDAO.recordSuccess(id,
                                         node,
                                         urlKey,
                                         etag,
                                         lastModified,
                                         contentHash,
                                         refreshMinutes,
                                         attempt,
                                         truncated,
                                         nextRefreshDate) == 1;
  }

  /**
   * Records a failed refresh and releases the claim.
   *
   * @param id technical identifier
   * @param node the node holding the claim
   * @param attempt when the refresh ran
   * @param lastError message code of the failure
   * @param nextRefreshDate when the next attempt is due
   * @return true when recorded
   */
  public boolean recordFailure(long id, String node, Date attempt, String lastError, Date nextRefreshDate) {
    return subscriptionDAO.recordFailure(id, node, attempt, lastError, nextRefreshDate) == 1;
  }

  /**
   * Deletes a subscription and its occurrence rows; the events are the caller's.
   *
   * @param id technical identifier
   */
  public void delete(long id) {
    subscriptionEventDAO.deleteBySubscriptionId(id);
    subscriptionDAO.findById(id).ifPresent(subscriptionDAO::delete);
  }

  /**
   * Reads the occurrence rows of a subscription.
   *
   * @param subscriptionId technical identifier of the subscription
   * @return the rows, oldest first
   */
  public List<CalendarSubscriptionEvent> getEvents(long subscriptionId) {
    return subscriptionEventDAO.findBySubscriptionId(subscriptionId, PageRequest.of(0, MAX_EVENT_ROWS, Sort.by("id")))
                               .stream()
                               .map(entity -> new CalendarSubscriptionEvent(entity.getId(),
                                                                            entity.getSubscriptionId(),
                                                                            entity.getEventId(),
                                                                            entity.getEventKey(),
                                                                            entity.getContentHash()))
                               .toList();
  }

  /**
   * Inserts or updates an occurrence row.
   *
   * @param event the row; an identifier of 0 inserts
   * @return the row as stored
   */
  public CalendarSubscriptionEvent saveEvent(CalendarSubscriptionEvent event) {
    CalendarSubscriptionEventEntity entity = event.getId() > 0 ? subscriptionEventDAO.findById(event.getId()).orElse(null) : null;
    if (entity == null) {
      entity = new CalendarSubscriptionEventEntity();
    }
    entity.setSubscriptionId(event.getSubscriptionId());
    entity.setEventId(event.getEventId());
    entity.setEventKey(event.getEventKey());
    entity.setContentHash(event.getContentHash());
    entity = subscriptionEventDAO.save(entity);
    return new CalendarSubscriptionEvent(entity.getId(),
                                         entity.getSubscriptionId(),
                                         entity.getEventId(),
                                         entity.getEventKey(),
                                         entity.getContentHash());
  }

  /**
   * Deletes an occurrence row.
   *
   * @param id technical identifier of the row
   */
  public void deleteEvent(long id) {
    subscriptionEventDAO.deleteById(id);
  }

  /**
   * Maps a row.
   *
   * @param entity the row, may be null
   * @return the model, or null
   */
  private CalendarSubscription toModel(CalendarSubscriptionEntity entity) {
    if (entity == null) {
      return null;
    }
    CalendarSubscription subscription = new CalendarSubscription();
    subscription.setId(entity.getId());
    subscription.setCalendarId(entity.getCalendarId());
    subscription.setUserIdentityId(entity.getUserIdentityId());
    subscription.setOwnerIdentityId(entity.getOwnerIdentityId());
    subscription.setUrlEncrypted(entity.getUrlEncrypted());
    subscription.setUrlKey(entity.getUrlKey());
    subscription.setEtag(entity.getEtag());
    subscription.setLastModified(entity.getLastModified());
    subscription.setContentHash(entity.getContentHash());
    subscription.setRefreshMinutes(entity.getRefreshMinutes() == null ? 0 : entity.getRefreshMinutes());
    subscription.setLastSuccessDate(time(entity.getLastSuccessDate()));
    subscription.setLastAttemptDate(time(entity.getLastAttemptDate()));
    subscription.setLastError(entity.getLastError());
    subscription.setTruncated(entity.isTruncated());
    subscription.setNextRefreshDate(time(entity.getNextRefreshDate()));
    subscription.setClaimedBy(entity.getClaimedBy());
    subscription.setClaimedDate(time(entity.getClaimedDate()));
    subscription.setCreatedDate(time(entity.getCreatedDate()));
    return subscription;
  }

  /**
   * A date as epoch milliseconds.
   *
   * @param date the date, may be null
   * @return the milliseconds, 0 for none
   */
  private static long time(Date date) {
    return date == null ? 0 : date.getTime();
  }

  /**
   * Epoch milliseconds as a date.
   *
   * @param time the milliseconds
   * @return the date
   */
  private static Date date(long time) {
    return new Date(time);
  }

}
