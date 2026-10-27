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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventConferenceEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.EventConferenceMatch;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;

/**
 * Runs the lookup of meeting links by call through Hibernate and HSQLDB, on
 * the schema agenda's real changelog builds: the statement the engine parses,
 * its join to the series of an occurrence, and the escape of the LIKE
 * wildcards a call identifier holds (<code>_</code> in every Jitsi meeting
 * call). The whole-segment rule is the service's, pinned in its own test.
 * <p>
 * <b>What this does not verify.</b> HSQLDB proves the grammar and the logic,
 * and runs here only. The escape character is <code>!</code> and not a
 * backslash, which a MySQL string literal reads as an escape of its own; the
 * same statement and rows were run by hand on MySQL 8.0 and PostgreSQL 16,
 * with the same answers (EXO-92121), which this suite does not repeat.
 */
class EventConferenceDAOQueryTest {

  private static final String          JITSI   = "jitsi";

  private static final String          CALL_ID = "g_space-abc";

  private String                       url;

  private Connection                   keeper;

  private EntityManagerFactory         factory;

  private EntityManager                entityManager;

  private AgendaEventConferenceStorage storage;

  private long                         series;

  private long                         storedOccurrence;

  private long                         longerCallEvent;

  /**
   * Builds the schema with the changelog, opens the persistence unit on it,
   * and writes: a series and one of its stored occurrences holding the
   * call's link (with an invitation, as agenda saves it), an event of a call
   * whose identifier starts with the same text, an event whose link differs
   * from the call identifier only where it holds <code>_</code>, and a
   * manual conference holding the same link.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void openDatabase() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-conference-dao-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("agenda-events-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    EventConferenceDAO dao = new EntityManagerConferenceDAO<EventConferenceEntity>(entityManager) {
    };
    storage = new AgendaEventConferenceStorage(null, dao, null);

    entityManager.getTransaction().begin();
    long calendar = calendar();
    series = event(calendar, null);
    storedOccurrence = event(calendar, series);
    longerCallEvent = event(calendar, null);
    long wildcardLookalike = event(calendar, null);
    long manual = event(calendar, null);
    conference(series, JITSI, "https://host/jitsi/meet/" + CALL_ID + "?inviteId=1");
    conference(storedOccurrence, JITSI, "https://host/jitsi/meet/" + CALL_ID + "?inviteId=1");
    conference(longerCallEvent, JITSI, "https://host/jitsi/meet/" + CALL_ID + "d");
    conference(wildcardLookalike, JITSI, "https://host/jitsi/meet/gXspace-abc");
    conference(manual, "manual", "https://host/jitsi/meet/" + CALL_ID);
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
   * The links of the call's type containing its text are found, each with
   * its series: the series and its stored occurrence, which holds the same
   * link, answer the series once, and the longer call's event answers
   * itself, the service ruling its segment out.
   */
  @Test
  void theLinksHoldingTheCallAreFoundOncePerSeries() {
    List<EventConferenceMatch> matches = storage.findConferencesContaining(JITSI, "/" + CALL_ID, 50);

    Set<String> found = matches.stream().map(match -> match.seriesId() + " " + match.url()).collect(Collectors.toSet());
    assertEquals(Set.of(series + " https://host/jitsi/meet/" + CALL_ID + "?inviteId=1",
                        longerCallEvent + " https://host/jitsi/meet/" + CALL_ID + "d"),
                 found);
    assertEquals(2, matches.size(), "the link the stored occurrence copies from its series comes back once");
  }

  /**
   * The <code>_</code> of a call identifier is matched literally: a link
   * holding any other character there is not found, as the unescaped LIKE
   * would find it.
   */
  @Test
  void anUnderscoreOfTheCallIdIsMatchedLiterally() {
    List<EventConferenceMatch> matches = storage.findConferencesContaining(JITSI, "/" + CALL_ID, 50);

    assertTrue(matches.stream().noneMatch(match -> match.url().contains("gXspace")),
               "a link differing where the call identifier holds _ is not the call's");
  }

  /**
   * A <code>%</code> and the escape character itself are matched literally
   * too: a text holding them finds nothing here, as no link holds them.
   */
  @Test
  void aPercentAndTheEscapeCharacterAreMatchedLiterally() {
    assertTrue(storage.findConferencesContaining(JITSI, "/g%abc", 50).isEmpty(), "% matches only %");
    assertTrue(storage.findConferencesContaining(JITSI, "/g!_space-abc", 50).isEmpty(), "! matches only !");
  }

  /**
   * A conference of another type holding the same link is not found.
   */
  @Test
  void aConferenceOfAnotherTypeIsNotFound() {
    assertTrue(storage.findConferencesContaining(JITSI, "/" + CALL_ID, 50).stream().noneMatch(match -> match.url().endsWith("/" + CALL_ID)),
               "the manual conference holds the link with nothing after it, and is of another type");
    assertEquals(1, storage.findConferencesContaining("manual", "/" + CALL_ID, 50).size());
  }

  /**
   * The limit bounds what is read.
   */
  @Test
  void theLimitBoundsTheRows() {
    assertEquals(1, storage.findConferencesContaining(JITSI, "/" + CALL_ID, 1).size());
  }

  /**
   * Persists a calendar.
   *
   * @return its identifier
   */
  private long calendar() {
    CalendarEntity calendar = new CalendarEntity();
    calendar.setOwnerId(1L);
    calendar.setColor("#000000");
    calendar.setCreatedDate(new Date());
    entityManager.persist(calendar);
    entityManager.flush();
    return calendar.getId();
  }

  /**
   * Persists a confirmed event, an occurrence of a series when one is given.
   *
   * @param calendarId calendar identifier
   * @param parentId the series, or <code>null</code>
   * @return the event identifier
   */
  private long event(long calendarId, Long parentId) {
    EventEntity event = new EventEntity();
    event.setCalendar(entityManager.find(CalendarEntity.class, calendarId));
    event.setCreatorId(1L);
    event.setModifierId(1L);
    event.setCreatedDate(new Date());
    event.setStartDate(new Date(2_000_000));
    event.setEndDate(new Date(3_000_000));
    event.setAvailability(EventAvailability.BUSY);
    event.setStatus(EventStatus.CONFIRMED);
    if (parentId != null) {
      event.setParent(entityManager.find(EventEntity.class, parentId));
      event.setOccurrenceId(new Date(2_000_000));
    }
    entityManager.persist(event);
    entityManager.flush();
    return event.getId();
  }

  /**
   * Persists a conference on an event.
   *
   * @param eventId event identifier
   * @param type conference type
   * @param link meeting link
   */
  private void conference(long eventId, String type, String link) {
    EventConferenceEntity conference = new EventConferenceEntity();
    conference.setEvent(entityManager.find(EventEntity.class, eventId));
    conference.setType(type);
    conference.setUrl(link);
    entityManager.persist(conference);
    entityManager.flush();
  }

  /**
   * The conference repository over a given entity manager. Generic, and built
   * through an anonymous subclass, because the base repository reads its
   * entity class from the generic superclass of the runtime class.
   *
   * @param <E> the entity class the base repository reads,
   *          {@code EventConferenceEntity}
   */
  private static class EntityManagerConferenceDAO<E> extends EventConferenceDAO {

    private final EntityManager entityManager;

    /**
     * Builds the repository.
     *
     * @param entityManager the entity manager every query runs on
     */
    EntityManagerConferenceDAO(EntityManager entityManager) {
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
