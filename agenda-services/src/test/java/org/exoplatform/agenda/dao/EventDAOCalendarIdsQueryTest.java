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
import java.util.Set;
import java.util.TreeSet;

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
 * Runs the event listing query through Hibernate and HSQLDB, on the schema
 * agenda's real changelog builds, for the shape EXO-90357 adds to it: the
 * calendars shared with the reader, <em>or</em>-ed with the owner and attendee
 * criteria in one statement, with the attendee join turned outer so that an
 * event the reader does not attend is still found through its calendar.
 * <p>
 * <b>What this does not verify.</b> HSQLDB proves the grammar and the logic;
 * the plan MySQL, PostgreSQL or Oracle choose is not run here.
 */
class EventDAOCalendarIdsQueryTest {

  private static final long    READER = 7;

  private static final long    OWNER  = 1;

  private static final Date    START  = new Date(1_000_000);

  private static final Date    END    = new Date(9_000_000);

  private String               url;

  private Connection           keeper;

  private EntityManagerFactory factory;

  private EntityManager        entityManager;

  private EventDAO             dao;

  private long                 ownCalendar;

  private long                 sharedCalendar;

  private long                 otherCalendar;

  private long                 ownEventAttended;

  private long                 sharedEventNotAttended;

  private long                 sharedEventAttended;

  private long                 otherEventNotAttended;

  private long                 otherEventAttended;

  /**
   * Builds the schema with the changelog, opens the persistence unit on it,
   * and writes three calendars — the reader's own, one shared with them, one
   * of a stranger — with an event each the reader attends or not.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-event-dao-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-events-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    dao = new EntityManagerEventDAO<EventEntity>(entityManager) {
    };
    entityManager.getTransaction().begin();
    ownCalendar = calendarOf(READER);
    sharedCalendar = calendarOf(OWNER);
    otherCalendar = calendarOf(OWNER);
    ownEventAttended = eventIn(ownCalendar, READER, READER);
    sharedEventNotAttended = eventIn(sharedCalendar, OWNER);
    sharedEventAttended = eventIn(sharedCalendar, OWNER, READER);
    otherEventNotAttended = eventIn(otherCalendar, OWNER);
    otherEventAttended = eventIn(otherCalendar, OWNER, READER);
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
   * Without shared calendars, the statement is what it was: the reader's
   * calendar, the events they attend in it.
   */
  @Test
  void withoutSharedCalendarsTheOwnerAndAttendeeCriteriaStand() {
    Set<Long> ids = ids(dao.getEventIds(START, END, List.of(READER), List.of(READER), List.of(EventAttendeeResponse.ACCEPTED), null, null, 0));

    assertEquals(Set.of(ownEventAttended), ids);
  }

  /**
   * A shared calendar's events are found whether or not the reader attends
   * them, beside the reader's own; the stranger's calendar stays out, even the
   * event the reader attends there, since the owner criterion still applies
   * to it.
   */
  @Test
  void aSharedCalendarsEventsAreFoundWhetherAttendedOrNot() {
    Set<Long> ids = ids(dao.getEventIds(START,
                                        END,
                                        List.of(READER),
                                        List.of(READER),
                                        List.of(EventAttendeeResponse.ACCEPTED),
                                        null,
                                        List.of(sharedCalendar),
                                        0));

    assertEquals(Set.of(ownEventAttended, sharedEventNotAttended, sharedEventAttended), ids);
  }

  /**
   * The exclusion still applies to a shared calendar: a hidden one is left
   * out even when listed.
   */
  @Test
  void anExcludedCalendarStaysOutEvenWhenListed() {
    Set<Long> ids = ids(dao.getEventIds(START,
                                        END,
                                        List.of(READER),
                                        List.of(READER),
                                        null,
                                        List.of(sharedCalendar),
                                        List.of(sharedCalendar),
                                        0));

    assertEquals(Set.of(ownEventAttended), ids);
  }

  /**
   * With no owner and no attendee criteria, the listed calendars alone select.
   */
  @Test
  void listedCalendarsAloneSelectWhenNothingElseDoes() {
    Set<Long> ids = ids(dao.getEventIds(START, END, null, null, null, null, List.of(sharedCalendar), 0));

    assertEquals(Set.of(sharedEventNotAttended, sharedEventAttended), ids);
  }

  /**
   * With owners only — no attendee criterion — the listed calendars add to
   * the owners' events.
   */
  @Test
  void listedCalendarsAddToTheOwnersEvents() {
    Set<Long> ids = ids(dao.getEventIds(START, END, List.of(READER), null, null, null, List.of(sharedCalendar), 0));

    assertEquals(Set.of(ownEventAttended, sharedEventNotAttended, sharedEventAttended), ids);
    assertEquals(otherEventNotAttended + otherEventAttended > 0, true, "the stranger's events exist and were not listed");
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
   * Persists a confirmed event in a calendar, inside the queried period, with
   * the given attendees all accepted.
   *
   * @param calendarId calendar identifier
   * @param creatorId creator identity identifier
   * @param attendeeIds identity identifiers of the attendees
   * @return the event identifier
   */
  private long eventIn(long calendarId, long creatorId, long... attendeeIds) {
    EventEntity event = new EventEntity();
    event.setCalendar(entityManager.find(CalendarEntity.class, calendarId));
    event.setCreatorId(creatorId);
    event.setModifierId(creatorId);
    event.setCreatedDate(new Date());
    event.setStartDate(new Date(2_000_000));
    event.setEndDate(new Date(3_000_000));
    event.setAvailability(EventAvailability.BUSY);
    event.setStatus(EventStatus.CONFIRMED);
    entityManager.persist(event);
    entityManager.flush();
    for (long attendeeId : attendeeIds) {
      EventAttendeeEntity attendee = new EventAttendeeEntity();
      attendee.setEvent(event);
      attendee.setIdentityId(attendeeId);
      attendee.setResponse(EventAttendeeResponse.ACCEPTED);
      entityManager.persist(attendee);
    }
    entityManager.flush();
    return event.getId();
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

  /**
   * The identifiers as a set, order aside.
   *
   * @param ids the identifiers
   * @return the set
   */
  private static Set<Long> ids(List<Long> ids) {
    return new TreeSet<>(ids);
  }

}
