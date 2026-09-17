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
package org.exoplatform.agenda.dao;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.agenda.entity.CalendarSubscriptionEntity;

/**
 * Reads and writes calendar subscriptions (EXO-90278).
 * <p>
 * The refresh job runs on every node of a cluster, so the rows it works on are
 * shared between nodes, and its writes carry their correctness in their WHERE
 * clause, as email-connector's {@code EmailSyncStateDAO} does: a claim lands only
 * on a due row nobody holds — or whose holder's claim went stale — and the row
 * count it returns is the answer; an outcome is recorded, and a claim released,
 * only by the node holding it.
 */
public interface CalendarSubscriptionDAO extends JpaRepository<CalendarSubscriptionEntity, Long> {

  /**
   * Finds the subscription of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return the subscription, or null when the calendar has none
   */
  CalendarSubscriptionEntity findByCalendarId(long calendarId);

  /**
   * Finds the subscription of an owner to a URL.
   *
   * @param urlKey SHA-256 of the owner and the normalized URL
   * @return the subscription, or null when none
   */
  CalendarSubscriptionEntity findByUrlKey(String urlKey);

  /**
   * Finds the subscriptions of an owner: a user's personal ones, or a space's
   * (EXO-90373).
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param pageable the page, whose sort orders them
   * @return the subscriptions, never null
   */
  List<CalendarSubscriptionEntity> findByOwnerIdentityId(long ownerIdentityId, Pageable pageable);

  /**
   * The calendars an owner's subscriptions fill, so that a space's subscribed
   * calendars can take the space's colour (EXO-90373).
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param pageable the page
   * @return technical identifiers of the calendars, the oldest subscription
   *         first
   */
  @Query("SELECT s.calendarId FROM AgendaCalendarSubscription s WHERE s.ownerIdentityId = :ownerIdentityId ORDER BY s.id ASC")
  List<Long> findCalendarIdsByOwnerIdentityId(@Param("ownerIdentityId") long ownerIdentityId, Pageable pageable);

  /**
   * The subscriptions due for a refresh that nobody holds a live claim on, the
   * one waiting longest first.
   *
   * @param now a subscription whose next refresh is at or before this instant is due
   * @param staleBefore a claim taken strictly before this instant no longer counts
   * @param pageable the batch
   * @return technical identifiers of the due subscriptions
   */
  @Query("SELECT s.id FROM AgendaCalendarSubscription s WHERE s.nextRefreshDate <= :now"
      + " AND (s.claimedDate IS NULL OR s.claimedDate < :staleBefore) ORDER BY s.nextRefreshDate ASC, s.id ASC")
  List<Long> findDueIds(@Param("now") Date now, @Param("staleBefore") Date staleBefore, Pageable pageable);

  /**
   * The job's claim: lands only on a subscription still due and held by nobody
   * live, so of every node trying at once exactly one sees a row count of one.
   *
   * @param id technical identifier of the subscription
   * @param node the claiming node
   * @param now the claim's stamp; the row must be due at this instant
   * @param staleBefore a claim taken strictly before this instant may be taken over
   * @return one when the caller now holds the claim, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.claimedBy = :node, s.claimedDate = :now WHERE s.id = :id"
      + " AND s.nextRefreshDate <= :now AND (s.claimedDate IS NULL OR s.claimedDate < :staleBefore)")
  int claimDue(@Param("id") long id, @Param("node") String node, @Param("now") Date now, @Param("staleBefore") Date staleBefore);

  /**
   * The owner's claim, for a refresh asked now: the same as the job's, due or not.
   *
   * @param id technical identifier of the subscription
   * @param node the claiming node
   * @param now the claim's stamp
   * @param staleBefore a claim taken strictly before this instant may be taken over
   * @return one when the caller now holds the claim, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.claimedBy = :node, s.claimedDate = :now WHERE s.id = :id"
      + " AND (s.claimedDate IS NULL OR s.claimedDate < :staleBefore)")
  int claim(@Param("id") long id, @Param("node") String node, @Param("now") Date now, @Param("staleBefore") Date staleBefore);

  /**
   * Records a successful refresh and releases the claim — only by the node that
   * holds it, and only while the subscription still points at the URL that was
   * read: an edit of the URL during the refresh leaves the row to the next one.
   *
   * @param id technical identifier of the subscription
   * @param node the node holding the claim
   * @param urlKey the key of the URL that was read
   * @param etag the entity tag read
   * @param lastModified the Last-Modified header read
   * @param contentHash digest of the body imported
   * @param refreshMinutes refresh interval the feed advertised, null for none
   * @param attempt when the refresh ran
   * @param truncated whether only part of the feed was imported
   * @param nextRefreshDate when the next refresh is due
   * @return one when recorded, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.etag = :etag, s.lastModified = :lastModified,"
      + " s.contentHash = :contentHash, s.refreshMinutes = :refreshMinutes, s.lastSuccessDate = :attempt,"
      + " s.lastAttemptDate = :attempt, s.lastError = NULL, s.truncated = :truncated, s.nextRefreshDate = :nextRefreshDate,"
      + " s.claimedBy = NULL, s.claimedDate = NULL WHERE s.id = :id AND s.claimedBy = :node AND s.urlKey = :urlKey")
  int recordSuccess(@Param("id") long id, // NOSONAR the statement names every column it writes
                    @Param("node") String node,
                    @Param("urlKey") String urlKey,
                    @Param("etag") String etag,
                    @Param("lastModified") String lastModified,
                    @Param("contentHash") String contentHash,
                    @Param("refreshMinutes") Integer refreshMinutes,
                    @Param("attempt") Date attempt,
                    @Param("truncated") boolean truncated,
                    @Param("nextRefreshDate") Date nextRefreshDate);

  /**
   * Records a failed refresh and releases the claim, only by the node holding
   * it. The last good copy, its validators and its digest are left as they
   * were.
   *
   * @param id technical identifier of the subscription
   * @param node the node holding the claim
   * @param attempt when the refresh ran
   * @param lastError message code of the failure
   * @param nextRefreshDate when the next attempt is due
   * @return one when recorded, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.lastAttemptDate = :attempt, s.lastError = :lastError,"
      + " s.nextRefreshDate = :nextRefreshDate, s.claimedBy = NULL, s.claimedDate = NULL"
      + " WHERE s.id = :id AND s.claimedBy = :node")
  int recordFailure(@Param("id") long id,
                    @Param("node") String node,
                    @Param("attempt") Date attempt,
                    @Param("lastError") String lastError,
                    @Param("nextRefreshDate") Date nextRefreshDate);

  /**
   * Releases a claim without recording anything, only by the node holding it.
   *
   * @param id technical identifier of the subscription
   * @param node the node holding the claim
   * @return one when released, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.claimedBy = NULL, s.claimedDate = NULL WHERE s.id = :id AND s.claimedBy = :node")
  int release(@Param("id") long id, @Param("node") String node);

  /**
   * Forgets the validators and the digest of the last read, so that the next
   * read that answers imports again whatever it answers — once the imported
   * events were purged (EXO-90373).
   *
   * @param id technical identifier of the subscription
   * @return one when the row exists, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.etag = NULL, s.lastModified = NULL, s.contentHash = NULL WHERE s.id = :id")
  int forgetContent(@Param("id") long id);

  /**
   * Points a subscription at a new URL and makes it due at once, forgetting the
   * validators and the digest of the previous URL.
   *
   * @param id technical identifier of the subscription
   * @param urlEncrypted the new URL encrypted
   * @param urlKey the key of the new URL
   * @param now the instant it becomes due
   * @return one when the row exists, zero otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AgendaCalendarSubscription s SET s.urlEncrypted = :urlEncrypted, s.urlKey = :urlKey, s.etag = NULL,"
      + " s.lastModified = NULL, s.contentHash = NULL, s.nextRefreshDate = :now WHERE s.id = :id")
  int updateUrl(@Param("id") long id,
                @Param("urlEncrypted") String urlEncrypted,
                @Param("urlKey") String urlKey,
                @Param("now") Date now);

}
