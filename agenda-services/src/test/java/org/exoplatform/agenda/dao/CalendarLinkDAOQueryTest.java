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
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.CalendarLinkEntity;

/**
 * Runs the calendar link repository through Hibernate and HSQLDB, on the schema
 * agenda's real changelog builds — Hibernate only validates the entity against
 * it. What a mock suite cannot say: that the derived queries are statements the
 * engine runs, that the {@code @PortableSequence} generator finds its sequence,
 * and that the engine refuses a second link for a calendar through JPA too.
 */
class CalendarLinkDAOQueryTest {

  private String               url;

  private Connection           keeper;

  private EntityManagerFactory factory;

  private EntityManager        entityManager;

  private CalendarLinkDAO      dao;

  /**
   * Builds the schema with the changelog, then opens the persistence unit on it.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-link-dao-" + System.nanoTime();
    // Kept open for the whole test: the in-memory database lives as long as a
    // connection to it does.
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-calendar-link-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    dao = new JpaRepositoryFactory(entityManager).getRepository(CalendarLinkDAO.class);
  }

  /**
   * Closes everything and drops the database.
   *
   * @throws Exception when it cannot be shut down
   */
  @AfterEach
  void closeDatabase() throws Exception {
    if (entityManager != null && entityManager.isOpen()) {
      entityManager.close();
    }
    if (factory != null) {
      factory.close();
    }
    try (Statement statement = keeper.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    keeper.close();
  }

  /**
   * A saved link is found by its calendar and by its digest, and not by
   * another calendar or another digest.
   */
  @Test
  void aLinkIsFoundByItsCalendarAndByItsDigest() {
    inTransaction(() -> dao.saveAndFlush(link(42, 7, "a".repeat(64))));
    entityManager.clear();

    CalendarLinkEntity byCalendar = dao.findByCalendarId(42);
    assertNotNull(byCalendar, "the link must be found by its calendar");
    assertNotNull(byCalendar.getId(), "and carry an identifier the generator gave it");
    assertEquals(7, byCalendar.getCreatorId());
    assertEquals(42, dao.findByTokenHash("a".repeat(64)).getCalendarId(), "and be found by its digest");
    assertNull(dao.findByCalendarId(43), "another calendar has no link");
    assertNull(dao.findByTokenHash("b".repeat(64)), "and another digest opens nothing");
  }

  /**
   * Resetting rewrites the row: the new digest opens it, the old one no longer
   * does.
   */
  @Test
  void aResetRewritesTheRow() {
    inTransaction(() -> dao.saveAndFlush(link(42, 7, "a".repeat(64))));
    inTransaction(() -> {
      CalendarLinkEntity entity = dao.findByCalendarId(42);
      entity.setTokenHash("b".repeat(64));
      entity.setCreatorId(8);
      dao.saveAndFlush(entity);
    });
    entityManager.clear();

    assertNull(dao.findByTokenHash("a".repeat(64)), "the old digest must open nothing once reset");
    assertEquals(8, dao.findByTokenHash("b".repeat(64)).getCreatorId(), "and the new one the reset link");
    assertEquals(1, dao.count(), "still one row");
  }

  /**
   * The engine refuses a second link for the same calendar through JPA, which
   * is what the storage's retry relies on.
   */
  @Test
  void aSecondLinkForTheSameCalendarIsRefusedByTheEngine() {
    inTransaction(() -> dao.saveAndFlush(link(42, 7, "a".repeat(64))));
    entityManager.clear();

    entityManager.getTransaction().begin();
    try {
      assertThrows(PersistenceException.class, () -> dao.saveAndFlush(link(42, 8, "b".repeat(64))));
    } finally {
      entityManager.getTransaction().rollback();
    }
  }

  /**
   * Deleting the row leaves nothing to open.
   */
  @Test
  void aDeletedLinkOpensNothing() {
    inTransaction(() -> dao.saveAndFlush(link(42, 7, "a".repeat(64))));
    inTransaction(() -> dao.delete(dao.findByCalendarId(42)));
    entityManager.clear();

    assertNull(dao.findByTokenHash("a".repeat(64)));
    assertEquals(0, dao.count());
  }

  /**
   * The listing joins links to their calendars in one statement and keeps only
   * the calendars the given identities own, newest first, one page at a time.
   */
  @Test
  void theLinksOfTheCalendarsSomeIdentitiesOwnAreReadInOneJoin() {
    long[] calendarIds = new long[4];
    inTransaction(() -> {
      calendarIds[0] = calendarOf(1);
      calendarIds[1] = calendarOf(100);
      calendarIds[2] = calendarOf(2);
      calendarIds[3] = calendarOf(1);
    });
    inTransaction(() -> {
      dao.saveAndFlush(link(calendarIds[0], 1, "a".repeat(64), new Date(1000)));
      dao.saveAndFlush(link(calendarIds[1], 7, "b".repeat(64), new Date(3000)));
      dao.saveAndFlush(link(calendarIds[2], 2, "c".repeat(64), new Date(2000)));
    });
    entityManager.clear();

    List<CalendarLinkEntity> links = dao.findByCalendarOwnerIds(List.of(1L, 100L), PageRequest.of(0, 10));

    assertEquals(List.of(calendarIds[1], calendarIds[0]), links.stream().map(CalendarLinkEntity::getCalendarId).toList(),
                 "the owners' links, newest first; another user's calendar and a calendar with no link stay out");
    assertEquals(1, dao.findByCalendarOwnerIds(List.of(1L, 100L), PageRequest.of(0, 1)).size(), "one page at a time");
    assertTrue(dao.findByCalendarOwnerIds(List.of(3L), PageRequest.of(0, 10)).isEmpty());
  }

  /**
   * Persists a calendar of an owner.
   *
   * @param ownerId owner identity identifier
   * @return the calendar identifier
   */
  private long calendarOf(long ownerId) {
    CalendarEntity calendar = new CalendarEntity();
    calendar.setOwnerId(ownerId);
    calendar.setColor("#000000");
    calendar.setCreatedDate(new Date());
    entityManager.persist(calendar);
    entityManager.flush();
    return calendar.getId();
  }

  /**
   * A new link row created at a given date.
   *
   * @param calendarId calendar identifier
   * @param creatorId creator identifier
   * @param tokenHash digest
   * @param createdDate creation date
   * @return the entity
   */
  private CalendarLinkEntity link(long calendarId, long creatorId, String tokenHash, Date createdDate) {
    CalendarLinkEntity entity = link(calendarId, creatorId, tokenHash);
    entity.setCreatedDate(createdDate);
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
   * A new link row.
   *
   * @param calendarId calendar identifier
   * @param creatorId creator identifier
   * @param tokenHash digest
   * @return the entity
   */
  private CalendarLinkEntity link(long calendarId, long creatorId, String tokenHash) {
    CalendarLinkEntity entity = new CalendarLinkEntity();
    entity.setCalendarId(calendarId);
    entity.setCreatorId(creatorId);
    entity.setTokenHash(tokenHash);
    entity.setTokenEncrypted("encrypted-" + tokenHash.charAt(0));
    entity.setCreatedDate(new Date());
    return entity;
  }

}
