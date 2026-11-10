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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.agenda.entity.EventEntity;

/**
 * Runs the two reads a seat decision makes through Hibernate and HSQLDB, on
 * the schema agenda's real changelog builds: the rows of an event narrowed by
 * answer, and its waiting rows in promotion order.
 */
class EventAttendeeDAOSeatQueryTest {

  private static final long    OWNER = 1;

  private String               url;

  private Connection           keeper;

  private EntityManagerFactory factory;

  private EntityManager        entityManager;

  private EventAttendeeDAO     dao;

  private long                 calendarId;

  /**
   * Builds the schema with the changelog and opens the persistence unit on
   * it.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-attendee-seat-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-events-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    dao = new EntityManagerAttendeeDAO<EventAttendeeEntity>(entityManager) {
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
   * The seat read returns the rows of the event with the answers asked, every
   * column a decision needs, and loads no entity into the persistence context.
   */
  @Test
  void theSeatReadReturnsTheRowsOfTheEventWithTheAnswersAskedAsValues() {
    entityManager.getTransaction().begin();
    long eventId = event();
    long otherEventId = event();
    long accepted = attendee(eventId, 11, EventAttendeeResponse.ACCEPTED, new Date(3_000_000), new Date(5_000_000), new Date(100));
    long waiting = attendee(eventId, 12, EventAttendeeResponse.WAITING, null, null, new Date(200));
    attendee(eventId, 13, EventAttendeeResponse.DECLINED, null, null, new Date(300));
    attendee(otherEventId, 14, EventAttendeeResponse.ACCEPTED, null, null, new Date(400));
    entityManager.getTransaction().commit();
    entityManager.clear();

    List<Object[]> rows = dao.getSeatRows(eventId, EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.WAITING);

    Map<Long, Object[]> rowsById = rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> row));
    assertEquals(2, rows.size(), "the read is not narrowed to the event and to the answers asked");
    Object[] acceptedRow = rowsById.get(accepted);
    assertEquals(11L, acceptedRow[1]);
    assertEquals(3_000_000L, ((Date) acceptedRow[2]).getTime());
    assertEquals(5_000_000L, ((Date) acceptedRow[3]).getTime());
    assertEquals(EventAttendeeResponse.ACCEPTED, acceptedRow[4]);
    assertEquals(100L, ((Date) acceptedRow[5]).getTime());
    assertEquals(EventAttendeeResponse.WAITING, rowsById.get(waiting)[4]);
    assertEquals(0,
                 entityManager.unwrap(Session.class).getStatistics().getEntityCount(),
                 "the read loaded entities into the persistence context");
  }

  /**
   * The waiting rows come earliest answer date first and, on the same date,
   * lowest id first: the order a promotion walks. Rows of another event or
   * with another answer are not read.
   */
  @Test
  void theWaitingReadOrdersByAnswerDateThenId() {
    entityManager.getTransaction().begin();
    long eventId = event();
    long otherEventId = event();
    long latest = attendee(eventId, 21, EventAttendeeResponse.WAITING, null, null, new Date(2_000));
    long firstOfTwo = attendee(eventId, 22, EventAttendeeResponse.WAITING, null, null, new Date(1_000));
    long secondOfTwo = attendee(eventId, 23, EventAttendeeResponse.WAITING, null, null, new Date(1_000));
    attendee(eventId, 24, EventAttendeeResponse.ACCEPTED, null, null, new Date(500));
    attendee(otherEventId, 25, EventAttendeeResponse.WAITING, null, null, new Date(10));
    entityManager.getTransaction().commit();
    entityManager.clear();

    List<Long> ids = dao.getWaitingAttendees(eventId).stream().map(row -> (Long) row[0]).toList();

    assertEquals(List.of(firstOfTwo, secondOfTwo, latest), ids);
  }

  /**
   * Persists a confirmed event in the test calendar.
   *
   * @return the event identifier
   */
  private long event() {
    EventEntity event = new EventEntity();
    event.setCalendar(entityManager.find(CalendarEntity.class, calendarId));
    event.setCreatorId(OWNER);
    event.setModifierId(OWNER);
    event.setCreatedDate(new Date());
    event.setStartDate(new Date(1_000_000));
    event.setEndDate(new Date(2_000_000));
    event.setAvailability(EventAvailability.BUSY);
    event.setStatus(EventStatus.CONFIRMED);
    entityManager.persist(event);
    entityManager.flush();
    return event.getId();
  }

  /**
   * Persists an attendee row.
   *
   * @param eventId the event identifier
   * @param identityId the attendee identity identifier
   * @param response the answer
   * @param from first occurrence the row covers, or null
   * @param until occurrence the row stops before, or null
   * @param responseDate the answer date
   * @return the row identifier
   */
  private long attendee(long eventId,
                        long identityId,
                        EventAttendeeResponse response,
                        Date from,
                        Date until,
                        Date responseDate) {
    EventAttendeeEntity attendee = new EventAttendeeEntity();
    attendee.setEvent(entityManager.find(EventEntity.class, eventId));
    attendee.setIdentityId(identityId);
    attendee.setResponse(response);
    attendee.setFromOccurrenceId(from);
    attendee.setUntilOccurrenceId(until);
    attendee.setResponseDate(responseDate);
    entityManager.persist(attendee);
    entityManager.flush();
    return attendee.getId();
  }

  /**
   * The attendee repository over a given entity manager, built through an
   * anonymous subclass for the reason {@code EventDAOLockQueryTest} gives: the
   * base repository reads its entity class from the generic superclass of the
   * runtime class.
   *
   * @param <E> the entity class the base repository reads,
   *          {@code EventAttendeeEntity}
   */
  private static class EntityManagerAttendeeDAO<E> extends EventAttendeeDAO {

    private final EntityManager entityManager;

    /**
     * Builds the repository.
     *
     * @param entityManager the entity manager every query runs on
     */
    EntityManagerAttendeeDAO(EntityManager entityManager) {
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
