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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.entity.EventRecurrenceEntity;

/**
 * Runs the decide step's two event reads through Hibernate and HSQLDB, on the
 * schema agenda's real changelog builds: the locking read of a row, and the
 * read of a series' dates in one statement.
 * <p>
 * <b>What this does not verify.</b> HSQLDB shows the statement Hibernate
 * sends, and, in the default LOCKS mode this unit runs in, that a second
 * locking read waits for the first transaction — on a lock of the whole table
 * there, not of the row. The container's database runs in MVCC, where it does
 * not wait. How MySQL and PostgreSQL serialise two decide steps is left to
 * the two-session check on those two databases that the delivery records in
 * its pull request.
 */
class EventDAOLockQueryTest {

  private static final long          OWNER     = 1;

  private final List<String>         statements = Collections.synchronizedList(new ArrayList<>());

  private final StatementInspector   inspector  = sql -> {
                                                  statements.add(sql);
                                                  return sql;
                                                };

  private String                     url;

  private Connection                 keeper;

  private EntityManagerFactory       factory;

  private EntityManager              entityManager;

  private EventDAO                   dao;

  private long                       calendarId;

  /**
   * Builds the schema with the changelog and opens the persistence unit on
   * it, with an inspector that records every statement sent.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-event-lock-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-events-test",
                                                     Map.of("jakarta.persistence.jdbc.url",
                                                            url,
                                                            "hibernate.session_factory.statement_inspector",
                                                            inspector));
    entityManager = factory.createEntityManager();
    dao = new EntityManagerEventDAO<EventEntity>(entityManager) {
    };
    entityManager.getTransaction().begin();
    CalendarEntity calendar = new CalendarEntity();
    calendar.setOwnerId(OWNER);
    calendar.setColor("#000000");
    calendar.setCreatedDate(new Date());
    entityManager.persist(calendar);
    entityManager.flush();
    calendarId = calendar.getId();
    entityManager.getTransaction().commit();
    entityManager.clear();
  }

  /**
   * Closes everything and drops the database.
   *
   * @throws Exception when it cannot be shut down
   */
  @AfterEach
  void closeDatabase() throws Exception {
    if (entityManager != null && entityManager.isOpen()) {
      if (entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().rollback();
      }
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
   * The locking read is one statement on the event table that carries
   * {@code FOR UPDATE}, and nothing else: it loads no entity, so the eager
   * recurrence of the row is not read before the transaction's next lock.
   */
  @Test
  void theLockingReadIsOneSelectForUpdateOfTheRowAndNothingElse() {
    long eventId = inTransaction(() -> {
      long id = event(null, null, 2_000_000);
      recurrenceOf(id);
      return id;
    });
    entityManager.clear();
    entityManager.getTransaction().begin();
    statements.clear();

    assertTrue(dao.lockEvent(eventId));

    assertEquals(1, statements.size(), "the locking read sent other statements: " + statements);
    String first = statements.get(0).toLowerCase();
    assertTrue(first.contains("exo_agenda_event") && first.contains("for update"),
               "the statement is not a locking read of the event row: " + first);
  }

  /**
   * A row that does not exist is reported as missing, not as an error: the
   * caller resolves again.
   */
  @Test
  void theLockingReadOfAMissingRowFindsNone() {
    entityManager.getTransaction().begin();

    assertFalse(dao.lockEvent(Long.MAX_VALUE));
  }

  /**
   * The lock needs a transaction to be held in: outside one, the read is
   * refused instead of reading the row unlocked.
   */
  @Test
  void theLockingReadNeedsATransaction() {
    long eventId = inTransaction(() -> event(null, null, 2_000_000));
    entityManager.clear();

    assertThrows(PersistenceException.class, () -> dao.lockEvent(eventId));
  }

  /**
   * A second locking read of the row, from another transaction, waits until
   * the first transaction ends: the lock is held by the transaction, not by
   * the statement. A plain read, which this would be without the lock mode,
   * does not wait.
   *
   * @throws Exception when the second reader fails
   */
  @Test
  void aSecondLockingReadWaitsForTheFirstTransaction() throws Exception {
    long eventId = inTransaction(() -> event(null, null, 2_000_000));
    entityManager.clear();
    entityManager.getTransaction().begin();
    dao.lockEvent(eventId);

    EntityManager other = factory.createEntityManager();
    EventDAO otherDao = new EntityManagerEventDAO<EventEntity>(other) {
    };
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> secondReader = executor.submit(() -> {
        other.getTransaction().begin();
        try {
          return otherDao.lockEvent(eventId);
        } finally {
          other.getTransaction().commit();
        }
      });
      assertThrows(TimeoutException.class,
                   () -> secondReader.get(500, TimeUnit.MILLISECONDS),
                   "the second locking read did not wait for the first transaction");

      entityManager.getTransaction().commit();

      assertTrue(secondReader.get(10, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
      other.close();
    }
  }

  /**
   * The dates of a series are read in one statement, the series' own dates
   * only, ordered by start date, with their recurrence loaded by that same
   * statement.
   */
  @Test
  void theDatesOfASeriesAreReadInOneStatementByStartDate() {
    long seriesId = inTransaction(() -> event(null, null, 1_000_000));
    long otherSeriesId = inTransaction(() -> event(null, null, 1_000_000));
    long third = inTransaction(() -> event(seriesId, new Date(7_000_000), 7_000_000));
    long first = inTransaction(() -> event(seriesId, new Date(3_000_000), 3_000_000));
    long second = inTransaction(() -> {
      long id = event(seriesId, new Date(5_000_000), 5_000_000);
      recurrenceOf(id);
      return id;
    });
    inTransaction(() -> event(otherSeriesId, new Date(4_000_000), 4_000_000));
    inTransaction(() -> event(seriesId, null, 6_000_000));
    entityManager.clear();
    statements.clear();

    List<EventEntity> dates = dao.getExceptionalOccurences(seriesId);
    int statementsOfTheRead = statements.size();
    dates.forEach(EventEntity::getRecurrence);

    assertEquals(List.of(first, second, third), dates.stream().map(EventEntity::getId).toList());
    assertNotNull(dates.get(1).getRecurrence());
    assertEquals(1, statementsOfTheRead, "the dates were not read in one statement: " + statements);
    assertEquals(1, statements.size(), "reading the recurrence of a date sent a statement: " + statements);
  }

  /**
   * Runs a write in its own committed transaction.
   *
   * @param write the write, returning an identifier
   * @return the identifier
   */
  private long inTransaction(java.util.function.LongSupplier write) {
    entityManager.getTransaction().begin();
    long id = write.getAsLong();
    entityManager.getTransaction().commit();
    return id;
  }

  /**
   * Persists a confirmed event, a date of a series when a parent is given.
   *
   * @param parentId the series identifier, or null
   * @param occurrenceId the date's occurrence identifier, or null
   * @param start the start instant in milliseconds
   * @return the event identifier
   */
  private long event(Long parentId, Date occurrenceId, long start) {
    EventEntity event = new EventEntity();
    event.setCalendar(entityManager.find(CalendarEntity.class, calendarId));
    if (parentId != null) {
      event.setParent(entityManager.find(EventEntity.class, parentId));
    }
    event.setOccurrenceId(occurrenceId);
    event.setCreatorId(OWNER);
    event.setModifierId(OWNER);
    event.setCreatedDate(new Date());
    event.setStartDate(new Date(start));
    event.setEndDate(new Date(start + 1_000_000));
    event.setAvailability(EventAvailability.BUSY);
    event.setStatus(EventStatus.CONFIRMED);
    entityManager.persist(event);
    entityManager.flush();
    return event.getId();
  }

  /**
   * Persists a recurrence on an event.
   *
   * @param eventId the event identifier
   */
  private void recurrenceOf(long eventId) {
    EventRecurrenceEntity recurrence = new EventRecurrenceEntity();
    recurrence.setEvent(entityManager.find(EventEntity.class, eventId));
    recurrence.setStartDate(new Date(1_000_000));
    recurrence.setEndDate(new Date(9_000_000));
    recurrence.setType(EventRecurrenceType.DAILY);
    recurrence.setRrule("FREQ=DAILY");
    entityManager.persist(recurrence);
    entityManager.flush();
  }

  /**
   * The event repository over a given entity manager. Generic, and built
   * through an anonymous subclass, because the base repository reads its
   * entity class from the generic superclass of the runtime class: a plain
   * subclass of {@code EventDAO} has none and fails in the constructor.
   *
   * @param <E> the entity class the base repository reads, {@code EventEntity}
   */
  private static class EntityManagerEventDAO<E> extends EventDAO {

    private final EntityManager entityManager;

    /**
     * Builds the repository.
     *
     * @param entityManager the entity manager every query runs on
     */
    EntityManagerEventDAO(EntityManager entityManager) {
      super(null, null, null, null, null, null, null);
      this.entityManager = entityManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EntityManager getEntityManager() {
      return entityManager;
    }
  }
}
