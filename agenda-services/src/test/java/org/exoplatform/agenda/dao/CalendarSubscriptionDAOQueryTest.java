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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.CalendarSubscriptionEntity;
import org.exoplatform.agenda.entity.CalendarSubscriptionEventEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;

/**
 * Runs the subscription repositories through Hibernate and HSQLDB, on the schema
 * agenda's real changelog builds (EXO-90278): every hand-written statement is
 * executed, the claim is shown to land for one node only, an outcome is recorded
 * only by the claimer on the URL it read, the entity writes only the columns it
 * changed, and the calendar listings leave subscribed calendars out.
 * <p>
 * <b>What this does not verify.</b> The claim race is staged sequentially: two
 * persistence contexts issue the same conditional UPDATE one after the other.
 * That proves the WHERE clause decides; that two concurrent UPDATEs serialize on
 * the row is the engines' row locking, not run here. Index usage on MySQL,
 * PostgreSQL or Oracle is not run either.
 */
class CalendarSubscriptionDAOQueryTest {

  private static final Instant         NOW   = Instant.parse("2026-09-14T08:00:00Z");

  private static final Date            STALE = Date.from(NOW.minus(Duration.ofMinutes(30)));

  private Connection                   keeper;

  private EntityManagerFactory         factory;

  private EntityManager                entityManager;

  private CalendarSubscriptionDAO      dao;

  private CalendarSubscriptionEventDAO eventDao;

  /**
   * Builds the schema with the changelog, then opens the persistence unit on it.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    String url = "jdbc:hsqldb:mem:agenda-subscription-dao-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-calendar-link-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    dao = new JpaRepositoryFactory(entityManager).getRepository(CalendarSubscriptionDAO.class);
    eventDao = new JpaRepositoryFactory(entityManager).getRepository(CalendarSubscriptionEventDAO.class);
  }

  /**
   * Closes everything and drops the database.
   *
   * @throws Exception when it cannot be shut down
   */
  @AfterEach
  void closeDatabase() throws Exception {
    if (entityManager.isOpen()) {
      entityManager.close();
    }
    factory.close();
    try (Statement statement = keeper.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    keeper.close();
  }

  /**
   * A subscription is found by its calendar, by its key and among its user's,
   * oldest first.
   */
  @Test
  void aSubscriptionIsFoundByCalendarKeyAndUser() {
    inTransaction(() -> {
      dao.saveAndFlush(subscription(42, 7, "a", NOW, NOW.minusSeconds(60)));
      dao.saveAndFlush(subscription(43, 7, "b", NOW, NOW.minusSeconds(120)));
      dao.saveAndFlush(subscription(44, 8, "c", NOW, NOW));
    });
    entityManager.clear();

    assertEquals(42, dao.findByCalendarId(42).getCalendarId());
    assertEquals(43, dao.findByUrlKey("b".repeat(64)).getCalendarId());
    assertNull(dao.findByUrlKey("z".repeat(64)));
    assertEquals(List.of(43L, 42L),
                 dao.findByUserIdentityId(7, PageRequest.of(0, 10, Sort.by("createdDate").ascending()))
                    .stream()
                    .map(CalendarSubscriptionEntity::getCalendarId)
                    .toList());
  }

  /**
   * The due list holds the subscriptions due and unclaimed or stale, the longest
   * waiting first, and leaves out a future one and a live claim.
   */
  @Test
  void theDueListLeavesOutFutureRefreshesAndLiveClaims() {
    long[] ids = new long[4];
    inTransaction(() -> {
      ids[0] = dao.saveAndFlush(subscription(1, 7, "a", NOW.minus(Duration.ofHours(1)), NOW)).getId();
      CalendarSubscriptionEntity stale = subscription(2, 7, "b", NOW.minus(Duration.ofHours(2)), NOW);
      stale.setClaimedBy("dead-node");
      stale.setClaimedDate(Date.from(NOW.minus(Duration.ofHours(2))));
      ids[1] = dao.saveAndFlush(stale).getId();
      CalendarSubscriptionEntity live = subscription(3, 7, "c", NOW.minus(Duration.ofHours(3)), NOW);
      live.setClaimedBy("live-node");
      live.setClaimedDate(Date.from(NOW.minus(Duration.ofMinutes(5))));
      ids[2] = dao.saveAndFlush(live).getId();
      ids[3] = dao.saveAndFlush(subscription(4, 7, "d", NOW.plus(Duration.ofHours(1)), NOW)).getId();
    });

    assertEquals(List.of(ids[1], ids[0]), dao.findDueIds(Date.from(NOW), STALE, PageRequest.of(0, 10)));
    assertEquals(List.of(ids[1]), dao.findDueIds(Date.from(NOW), STALE, PageRequest.of(0, 1)), "one batch at a time");
  }

  /**
   * Of two nodes claiming a due subscription, one wins; a stale claim is taken
   * over; a future subscription is not the job's to claim, but its owner's
   * refresh claims it.
   */
  @Test
  void aClaimLandsForOneNodeOnly() {
    long due = inTransaction(() -> dao.saveAndFlush(subscription(1, 7, "a", NOW.minusSeconds(1), NOW)).getId());
    long future = inTransaction(() -> dao.saveAndFlush(subscription(2, 7, "b", NOW.plus(Duration.ofHours(1)), NOW)).getId());

    assertEquals(1, (int) inTransaction(() -> dao.claimDue(due, "node-a", Date.from(NOW), STALE)));
    EntityManager other = factory.createEntityManager();
    CalendarSubscriptionDAO otherDao = new JpaRepositoryFactory(other).getRepository(CalendarSubscriptionDAO.class);
    try {
      other.getTransaction().begin();
      assertEquals(0, otherDao.claimDue(due, "node-b", Date.from(NOW), STALE), "the second node loses");
      other.getTransaction().commit();

      Instant later = NOW.plus(Duration.ofMinutes(31));
      other.getTransaction().begin();
      assertEquals(1, otherDao.claimDue(due, "node-b", Date.from(later), Date.from(later.minus(Duration.ofMinutes(30)))),
                   "a claim older than the stale timeout is taken over");
      other.getTransaction().commit();
    } finally {
      other.close();
    }
    assertEquals(0, (int) inTransaction(() -> dao.claimDue(future, "node-a", Date.from(NOW), STALE)), "not due yet");
    assertEquals(1, (int) inTransaction(() -> dao.claim(future, "node-a", Date.from(NOW), STALE)), "the owner's refresh claims it");
    entityManager.clear();
    assertEquals("node-b", dao.findById(due).orElseThrow().getClaimedBy());
  }

  /**
   * An outcome is recorded only by the node holding the claim and only on the
   * URL it read; recording releases the claim; a failure keeps the validators of
   * the last good read.
   */
  @Test
  void anOutcomeIsRecordedOnlyByTheClaimerOnTheUrlItRead() {
    long id = inTransaction(() -> dao.saveAndFlush(subscription(1, 7, "a", NOW.minusSeconds(1), NOW)).getId());
    inTransaction(() -> dao.claimDue(id, "node-a", Date.from(NOW), STALE));
    Date next = Date.from(NOW.plus(Duration.ofHours(4)));

    assertEquals(0, (int) inTransaction(() -> dao.recordSuccess(id, "node-b", "a".repeat(64), "\"v1\"", null, "h", 60, Date.from(NOW), false, next)),
                 "another node records nothing");
    assertEquals(0, (int) inTransaction(() -> dao.recordSuccess(id, "node-a", "z".repeat(64), "\"v1\"", null, "h", 60, Date.from(NOW), false, next)),
                 "nothing is recorded on a URL changed meanwhile");
    assertEquals(1, (int) inTransaction(() -> dao.recordSuccess(id, "node-a", "a".repeat(64), "\"v1\"", "lm", "h", 60, Date.from(NOW), true, next)));
    entityManager.clear();
    CalendarSubscriptionEntity success = dao.findById(id).orElseThrow();
    assertNull(success.getClaimedBy(), "recording releases the claim");
    assertNull(success.getClaimedDate());
    assertEquals("\"v1\"", success.getEtag());
    assertEquals(60, success.getRefreshMinutes());
    assertTrue(success.isTruncated());
    assertEquals(next, success.getNextRefreshDate());
    assertEquals(Date.from(NOW), success.getLastSuccessDate());

    inTransaction(() -> dao.claim(id, "node-a", Date.from(NOW), STALE));
    assertEquals(0, (int) inTransaction(() -> dao.recordFailure(id, "node-b", Date.from(NOW), "agenda.calendarSubscription.timeout", next)));
    assertEquals(1, (int) inTransaction(() -> dao.recordFailure(id, "node-a", Date.from(NOW), "agenda.calendarSubscription.timeout", next)));
    entityManager.clear();
    CalendarSubscriptionEntity failure = dao.findById(id).orElseThrow();
    assertEquals("agenda.calendarSubscription.timeout", failure.getLastError());
    assertEquals("\"v1\"", failure.getEtag(), "a failure keeps the validators of the last good read");
    assertEquals("h", failure.getContentHash());
    assertNull(failure.getClaimedBy());

    inTransaction(() -> dao.claim(id, "node-a", Date.from(NOW), STALE));
    assertEquals(0, (int) inTransaction(() -> dao.release(id, "node-b")));
    assertEquals(1, (int) inTransaction(() -> dao.release(id, "node-a")));
  }

  /**
   * The engine refuses a Last-Modified wider than its column, which is why the
   * fetcher keeps none longer than {@code CalendarFeedFetcher.MAX_LAST_MODIFIED}.
   */
  @Test
  void aLastModifiedWiderThanItsColumnIsRefusedByTheEngine() {
    long id = inTransaction(() -> dao.saveAndFlush(subscription(1, 7, "a", NOW.minusSeconds(1), NOW)).getId());
    inTransaction(() -> dao.claimDue(id, "node-a", Date.from(NOW), STALE));
    Date next = Date.from(NOW.plus(Duration.ofHours(4)));

    assertEquals(1, (int) inTransaction(() -> dao.recordSuccess(id, "node-a", "a".repeat(64), null, "L".repeat(128), "h", null, Date.from(NOW), false, next)));
    inTransaction(() -> dao.claim(id, "node-a", Date.from(NOW.plusSeconds(1)), STALE));
    entityManager.getTransaction().begin();
    try {
      assertThrows(PersistenceException.class,
                   () -> dao.recordSuccess(id, "node-a", "a".repeat(64), null, "L".repeat(129), "h", null, Date.from(NOW), false, next));
    } finally {
      entityManager.getTransaction().rollback();
    }
  }

  /**
   * A new URL forgets the validators of the old one and is due at once; the
   * engine refuses a URL key the user already holds.
   */
  @Test
  void aNewUrlForgetsTheValidatorsAndADuplicateKeyIsRefused() {
    long id = inTransaction(() -> {
      CalendarSubscriptionEntity entity = subscription(1, 7, "a", NOW.plus(Duration.ofHours(3)), NOW);
      entity.setEtag("\"v1\"");
      entity.setLastModified("yesterday");
      entity.setContentHash("h");
      dao.saveAndFlush(subscription(2, 7, "b", NOW, NOW));
      return dao.saveAndFlush(entity).getId();
    });

    assertEquals(1, (int) inTransaction(() -> dao.updateUrl(id, "enc-new", "n".repeat(64), Date.from(NOW))));
    entityManager.clear();
    CalendarSubscriptionEntity updated = dao.findById(id).orElseThrow();
    assertEquals("enc-new", updated.getUrlEncrypted());
    assertNull(updated.getEtag());
    assertNull(updated.getLastModified());
    assertNull(updated.getContentHash());
    assertEquals(Date.from(NOW), updated.getNextRefreshDate());

    entityManager.getTransaction().begin();
    try {
      assertThrows(PersistenceException.class, () -> dao.updateUrl(id, "enc", "b".repeat(64), Date.from(NOW)));
    } finally {
      entityManager.getTransaction().rollback();
    }
    entityManager.clear();
    entityManager.getTransaction().begin();
    try {
      assertThrows(PersistenceException.class, () -> dao.saveAndFlush(subscription(3, 8, "b", NOW, NOW)));
    } finally {
      entityManager.getTransaction().rollback();
    }
  }

  /**
   * A save of the entity writes only the columns it changed: a validator another
   * writer committed after the entity was read survives an edit of another
   * column.
   */
  @Test
  void aSaveWritesOnlyTheColumnsItChanged() {
    long id = inTransaction(() -> dao.saveAndFlush(subscription(1, 7, "a", NOW, NOW)).getId());
    entityManager.clear();
    CalendarSubscriptionEntity read = dao.findById(id).orElseThrow();

    EntityManager other = factory.createEntityManager();
    try {
      other.getTransaction().begin();
      other.createQuery("UPDATE AgendaCalendarSubscription s SET s.etag = :etag WHERE s.id = :id")
           .setParameter("etag", "\"committed-elsewhere\"")
           .setParameter("id", id)
           .executeUpdate();
      other.getTransaction().commit();
    } finally {
      other.close();
    }
    inTransaction(() -> {
      read.setLastError("agenda.calendarSubscription.unreachable");
      dao.saveAndFlush(read);
    });
    entityManager.clear();

    CalendarSubscriptionEntity stored = dao.findById(id).orElseThrow();
    assertEquals("agenda.calendarSubscription.unreachable", stored.getLastError());
    assertEquals("\"committed-elsewhere\"", stored.getEtag(), "the edit must not put back the entity tag it read");
  }

  /**
   * The occurrence rows of a subscription are read page by page and deleted
   * together, leaving another subscription's rows.
   */
  @Test
  void occurrenceRowsAreReadPagedAndDeletedTogether() {
    inTransaction(() -> {
      eventDao.saveAndFlush(occurrence(11, 101, "k1"));
      eventDao.saveAndFlush(occurrence(11, 102, "k2"));
      eventDao.saveAndFlush(occurrence(11, 103, "k3"));
      eventDao.saveAndFlush(occurrence(12, 104, "k4"));
    });

    assertEquals(3, eventDao.findBySubscriptionId(11, PageRequest.of(0, 10)).size());
    assertEquals(2, eventDao.findBySubscriptionId(11, PageRequest.of(0, 2)).size());
    assertEquals(3, (int) inTransaction(() -> eventDao.deleteBySubscriptionId(11)));
    entityManager.clear();
    assertTrue(eventDao.findBySubscriptionId(11, PageRequest.of(0, 10)).isEmpty());
    assertEquals(1, eventDao.findBySubscriptionId(12, PageRequest.of(0, 10)).size());
  }

  /**
   * The owner listings of calendars leave a subscribed calendar out, while the
   * calendar itself is still there to read by its identifier.
   */
  @Test
  void theOwnerListingsLeaveSubscribedCalendarsOut() {
    long[] ids = new long[3];
    inTransaction(() -> {
      ids[0] = calendar(7, false);
      ids[1] = calendar(7, true);
      ids[2] = calendar(8, false);
    });
    entityManager.clear();

    List<Long> listed = entityManager.createNamedQuery("AgendaCalendar.getCalendarIdsByOwnerIds", Long.class)
                                     .setParameter("ownerIds", List.of(7L, 8L))
                                     .getResultList();
    Long count = entityManager.createNamedQuery("AgendaCalendar.countCalendarsByOwnerIds", Long.class)
                              .setParameter("ownerIds", List.of(7L, 8L))
                              .getSingleResult();

    assertEquals(List.of(ids[0], ids[2]), listed);
    assertEquals(2L, count);
    assertTrue(entityManager.find(CalendarEntity.class, ids[1]).isSubscription());
  }

  /**
   * Persists a calendar.
   *
   * @param ownerId owner identity identifier
   * @param subscription whether it is a subscribed calendar
   * @return its identifier
   */
  private long calendar(long ownerId, boolean subscription) {
    CalendarEntity calendar = new CalendarEntity();
    calendar.setOwnerId(ownerId);
    calendar.setColor("#000000");
    calendar.setCreatedDate(new Date());
    calendar.setSubscription(subscription);
    entityManager.persist(calendar);
    entityManager.flush();
    return calendar.getId();
  }

  /**
   * A new subscription row.
   *
   * @param calendarId calendar identifier
   * @param userIdentityId owner identifier
   * @param key the URL key letter, repeated to 64 characters
   * @param nextRefresh when it is due
   * @param created when it was created
   * @return the entity
   */
  private static CalendarSubscriptionEntity subscription(long calendarId, long userIdentityId, String key, Instant nextRefresh, Instant created) {
    CalendarSubscriptionEntity entity = new CalendarSubscriptionEntity();
    entity.setCalendarId(calendarId);
    entity.setUserIdentityId(userIdentityId);
    entity.setUrlEncrypted("enc-" + key);
    entity.setUrlKey(key.repeat(64));
    entity.setNextRefreshDate(Date.from(nextRefresh));
    entity.setCreatedDate(Date.from(created));
    return entity;
  }

  /**
   * A new occurrence row.
   *
   * @param subscriptionId subscription identifier
   * @param eventId event identifier
   * @param key occurrence key
   * @return the entity
   */
  private static CalendarSubscriptionEventEntity occurrence(long subscriptionId, long eventId, String key) {
    CalendarSubscriptionEventEntity entity = new CalendarSubscriptionEventEntity();
    entity.setSubscriptionId(subscriptionId);
    entity.setEventId(eventId);
    entity.setEventKey(key);
    entity.setContentHash("hash");
    return entity;
  }

  /**
   * Runs work in a committed transaction.
   *
   * @param work the work
   */
  private void inTransaction(Runnable work) {
    entityManager.getTransaction().begin();
    work.run();
    entityManager.getTransaction().commit();
  }

  /**
   * Runs work in a committed transaction and answers its result.
   *
   * @param <T> the result type
   * @param work the work
   * @return its result
   */
  private <T> T inTransaction(Supplier<T> work) {
    entityManager.getTransaction().begin();
    T result = work.get();
    entityManager.getTransaction().commit();
    return result;
  }

}
