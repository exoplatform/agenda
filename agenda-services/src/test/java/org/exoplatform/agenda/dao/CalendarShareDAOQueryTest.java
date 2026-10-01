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

import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.CalendarShareEntity;

/**
 * Runs the calendar share repository through Hibernate and HSQLDB, on the
 * schema agenda's real changelog builds. What a mock suite cannot say: that
 * every derived and written query is a statement the engine runs, that the
 * generator finds its sequence, that the join to the calendar table counts
 * what it should, and that the engine refuses a second share of a pair
 * through JPA too.
 */
class CalendarShareDAOQueryTest {

  private String               url;

  private Connection           keeper;

  private EntityManagerFactory factory;

  private EntityManager        entityManager;

  private CalendarShareDAO     dao;

  /**
   * Builds the schema with the changelog, then opens the persistence unit on it.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-share-dao-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-calendar-link-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    dao = new JpaRepositoryFactory(entityManager).getRepository(CalendarShareDAO.class);
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
   * A saved share is found by its pair, listed under its calendar and under
   * its sharee, and its calendar is among the sharee's identifiers.
   */
  @Test
  void aShareIsFoundByItsPairAndListedBothWays() {
    inTransaction(() -> dao.saveAndFlush(share(42, 7, new Date(1000))));
    entityManager.clear();

    CalendarShareEntity byPair = dao.findByCalendarIdAndShareeIdentityId(42, 7);
    assertNotNull(byPair, "the share must be found by its pair");
    assertNotNull(byPair.getId(), "and carry an identifier the generator gave it");
    assertEquals(CalendarShareSource.EXO, byPair.getSource());
    assertEquals(List.of(42L), dao.findCalendarIdsByShareeIdentityId(7));
    assertEquals(1, dao.findByCalendarIdOrderByCreatedDateAscIdAsc(42, PageRequest.of(0, 10)).size());
    assertEquals(1, dao.findByShareeIdentityIdOrderByCreatedDateDescIdDesc(7, PageRequest.of(0, 10)).size());
    assertNull(dao.findByCalendarIdAndShareeIdentityId(42, 8), "another colleague has no share");
    assertTrue(dao.findCalendarIdsByShareeIdentityId(8).isEmpty());
  }

  /**
   * The listings are ordered as their names say: a calendar's shares oldest
   * first, a sharee's newest first.
   */
  @Test
  void theListingsAreOrderedByDate() {
    inTransaction(() -> {
      dao.saveAndFlush(share(42, 7, new Date(3000)));
      dao.saveAndFlush(share(42, 8, new Date(1000)));
      dao.saveAndFlush(share(43, 7, new Date(2000)));
    });
    entityManager.clear();

    assertEquals(List.of(8L, 7L),
                 dao.findByCalendarIdOrderByCreatedDateAscIdAsc(42, PageRequest.of(0, 10))
                    .stream()
                    .map(CalendarShareEntity::getShareeIdentityId)
                    .toList());
    assertEquals(List.of(42L, 43L),
                 dao.findByShareeIdentityIdOrderByCreatedDateDescIdDesc(7, PageRequest.of(0, 10))
                    .stream()
                    .map(CalendarShareEntity::getCalendarId)
                    .toList());
    assertEquals(1, dao.findByShareeIdentityIdOrderByCreatedDateDescIdDesc(7, PageRequest.of(0, 1)).size(), "one page at a time");
  }

  /**
   * The engine refuses a second share of the same pair through JPA, which is
   * what the storage's race handling relies on.
   */
  @Test
  void aSecondShareOfTheSamePairIsRefusedByTheEngine() {
    inTransaction(() -> dao.saveAndFlush(share(42, 7, new Date())));
    entityManager.clear();

    entityManager.getTransaction().begin();
    try {
      assertThrows(PersistenceException.class, () -> dao.saveAndFlush(share(42, 7, new Date())));
    } finally {
      entityManager.getTransaction().rollback();
    }
  }

  /**
   * The counts join the shares to their calendars and keep the given owner's
   * calendars only, one row per shared calendar.
   */
  @Test
  void theShareeCountsAreGroupedByTheOwnersCalendars() {
    long[] calendarIds = new long[3];
    inTransaction(() -> {
      calendarIds[0] = calendarOf(1);
      calendarIds[1] = calendarOf(1);
      calendarIds[2] = calendarOf(2);
    });
    inTransaction(() -> {
      dao.saveAndFlush(share(calendarIds[0], 7, new Date()));
      dao.saveAndFlush(share(calendarIds[0], 8, new Date()));
      dao.saveAndFlush(share(calendarIds[2], 7, new Date()));
    });
    entityManager.clear();

    List<Object[]> rows = dao.countByCalendarOfOwner(1);

    assertEquals(1, rows.size(), "the owner's calendars only, and only the shared one");
    assertEquals(calendarIds[0], ((Number) rows.get(0)[0]).longValue());
    assertEquals(2, ((Number) rows.get(0)[1]).longValue());
    assertTrue(dao.countByCalendarOfOwner(3).isEmpty());
  }

  /**
   * The bulk deletions run and take exactly what they name: a calendar's
   * shares, a sharee's shares, an owner's calendars' shares.
   */
  @Test
  void theBulkDeletionsTakeWhatTheyName() {
    long[] calendarIds = new long[2];
    inTransaction(() -> {
      calendarIds[0] = calendarOf(1);
      calendarIds[1] = calendarOf(2);
    });
    inTransaction(() -> {
      dao.saveAndFlush(share(calendarIds[0], 7, new Date()));
      dao.saveAndFlush(share(calendarIds[0], 8, new Date()));
      dao.saveAndFlush(share(calendarIds[1], 7, new Date()));
      dao.saveAndFlush(share(99, 9, new Date()));
    });
    entityManager.clear();

    inTransaction(() -> assertEquals(1, dao.deleteByCalendarId(99)));
    inTransaction(() -> assertEquals(2, dao.deleteByShareeIdentityId(7)));
    inTransaction(() -> assertEquals(1, dao.deleteByCalendarOwner(1)));
    entityManager.clear();

    assertEquals(0, dao.count());
  }

  /**
   * The shares an owner's calendars carry through a channel are read in one
   * join, and cleared rows stay out.
   */
  @Test
  void theSharesOfAnOwnerDeliveredThroughAChannelAreReadInOneJoin() {
    long[] calendarIds = new long[2];
    inTransaction(() -> {
      calendarIds[0] = calendarOf(1);
      calendarIds[1] = calendarOf(2);
    });
    inTransaction(() -> {
      CalendarShareEntity delivered = share(calendarIds[0], 7, new Date());
      delivered.setDeliveredTo("caldav:1");
      dao.saveAndFlush(delivered);
      dao.saveAndFlush(share(calendarIds[0], 8, new Date()));
      CalendarShareEntity other = share(calendarIds[1], 7, new Date());
      other.setDeliveredTo("caldav:1");
      dao.saveAndFlush(other);
    });
    entityManager.clear();

    List<CalendarShareEntity> delivered = dao.findByCalendarOwnerAndDeliveredTo(1, "caldav:1");

    assertEquals(1, delivered.size());
    assertEquals(7, delivered.get(0).getShareeIdentityId());
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
   * A new share row.
   *
   * @param calendarId calendar identifier
   * @param shareeId sharee identity identifier
   * @param createdDate creation date
   * @return the entity
   */
  private CalendarShareEntity share(long calendarId, long shareeId, Date createdDate) {
    CalendarShareEntity entity = new CalendarShareEntity();
    entity.setCalendarId(calendarId);
    entity.setShareeIdentityId(shareeId);
    entity.setGrantedById(1);
    entity.setSource(CalendarShareSource.EXO);
    entity.setCreatedDate(createdDate);
    return entity;
  }

}
