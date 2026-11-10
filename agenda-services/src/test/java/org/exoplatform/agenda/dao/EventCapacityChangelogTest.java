/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.dao;

import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.CHANGELOG;
import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.changesetsSince;
import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.update;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * Runs agenda's real changelog on HSQLDB and checks what the event capacity
 * limitation (eXIP 7.3.0.21) adds to it: the capacity columns and the
 * attendee index apply, roll back and re-apply; every event that existed
 * before them stays unlimited; and a rollback leaves no attendee holding a
 * waiting answer the previous version cannot read.
 * <p>
 * Written against the engine rather than mocked because what can be wrong here
 * — a default that is not the "no limit" the entity reads back, a rollback
 * that does not exist or forgets the waiting rows, an index in the wrong
 * changeset — is invisible to anything but a real database. Neither changeset
 * is restricted to an engine, so HSQLDB runs both, and a duplicate id in the
 * file fails every test here.
 */
class EventCapacityChangelogTest {

  private static final String EVENT_TABLE     = "EXO_AGENDA_EVENT";

  private static final String ATTENDEE_TABLE  = "EXO_AGENDA_ATTENDEE";

  private static final String INDEX           = "IDX_AGENDA_ATTENDEE_FROM";

  /** The first changeset the capacity limitation adds. */
  private static final String FIRST_CHANGESET = "1.0.0-exip-7.3.0.21-01";

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:agenda-capacity-changelog-" + System.nanoTime(), "sa", "");
  }

  /**
   * Drops the database.
   *
   * @throws SQLException when it cannot be shut down
   */
  @AfterEach
  void dropDatabase() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  /**
   * The whole changelog applies and the tables carry the columns the entities
   * map, and the attendee index.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theChangelogAddsTheCapacityColumnsAndTheIndex() throws Exception {
    update(connection);

    assertTrue(columnExists(EVENT_TABLE, "CAPACITY"));
    assertTrue(columnExists(EVENT_TABLE, "CAPACITY_REACHED_DATE"));
    assertTrue(columnExists(ATTENDEE_TABLE, "RESPONSE_DATE"));
    assertEquals(List.of("FROM_OCCURRENCE_ID"),
                 indexColumns(ATTENDEE_TABLE, INDEX),
                 "the index holds the column the re-push reads, alone");
  }

  /**
   * Rolling the two changesets back drops what they added and leaves the rest
   * of both tables alone, and the changelog applies again from there.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theAddedChangesetsRollBackAndReapply() throws Exception {
    update(connection);
    rollbackCapacity();

    assertFalse(columnExists(EVENT_TABLE, "CAPACITY"), "rolling back must drop the capacity");
    assertFalse(columnExists(EVENT_TABLE, "CAPACITY_REACHED_DATE"), "and its reached marker");
    assertFalse(columnExists(ATTENDEE_TABLE, "RESPONSE_DATE"), "and the response date");
    assertTrue(indexColumns(ATTENDEE_TABLE, INDEX).isEmpty(), "and the index");
    assertTrue(columnExists(EVENT_TABLE, "IS_OPEN"), "and nothing that came before them");
    assertTrue(columnExists(ATTENDEE_TABLE, "FROM_OCCURRENCE_ID"));

    update(connection);

    assertTrue(columnExists(EVENT_TABLE, "CAPACITY"), "re-applying must add the columns again");
    assertEquals(List.of("FROM_OCCURRENCE_ID"), indexColumns(ATTENDEE_TABLE, INDEX), "and the index");
  }

  /**
   * An event written before the columns existed has no limit, and the attendee
   * rows written before them have no response date: the waiting list orders
   * only the answers given once it exists.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void rowsWrittenBeforeTheColumnsHaveNoLimitAndNoResponseDate() throws Exception {
    update(connection);
    rollbackCapacity();
    insertEventWithAttendee(1, 11, EventAttendeeResponse.ACCEPTED.ordinal());

    update(connection);

    Integer capacity = intColumn("SELECT CAPACITY FROM " + EVENT_TABLE + " WHERE EVENT_ID = 1");
    assertNotNull(capacity, "the event that predates the column is backfilled, not left NULL");
    assertEquals(0, capacity.intValue(), "and backfilled with 0, which is no limit");
    assertNull(objectColumn("SELECT CAPACITY_REACHED_DATE FROM " + EVENT_TABLE + " WHERE EVENT_ID = 1"),
               "no date of it has reached a limit it never had");
    assertNull(objectColumn("SELECT RESPONSE_DATE FROM " + ATTENDEE_TABLE + " WHERE EVENT_ATTENDEE_ID = 11"));
  }

  /**
   * A rollback turns every waiting answer back into "not answered", the value
   * a version without the waiting list can read; the other answers keep
   * theirs. WAITING is stored by ordinal, so its value here is 4.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void aRollbackTurnsWaitingAnswersIntoNoAnswer() throws Exception {
    update(connection);
    assertEquals(4, EventAttendeeResponse.WAITING.ordinal(), "the value the rollback looks for");
    insertEventWithAttendee(1, 11, EventAttendeeResponse.WAITING.ordinal());
    insertAttendee(12, 1, 202, EventAttendeeResponse.TENTATIVE.ordinal());

    rollbackCapacity();

    assertEquals(EventAttendeeResponse.NEEDS_ACTION.ordinal(),
                 intColumn("SELECT RESPONSE FROM " + ATTENDEE_TABLE + " WHERE EVENT_ATTENDEE_ID = 11").intValue(),
                 "a waiting answer goes back to no answer");
    assertEquals(EventAttendeeResponse.TENTATIVE.ordinal(),
                 intColumn("SELECT RESPONSE FROM " + ATTENDEE_TABLE + " WHERE EVENT_ATTENDEE_ID = 12").intValue(),
                 "any other answer is kept");
  }

  private void rollbackCapacity() throws Exception {
    liquibase(connection).rollback(changesetsSince(connection, FIRST_CHANGESET), new Contexts(), new LabelExpression());
  }

  /**
   * Inserts an event row and one attendee row, with only the columns every
   * version of the tables has.
   *
   * @param eventId event row identifier
   * @param attendeeId attendee row identifier
   * @param response the attendee's answer, by ordinal
   * @throws SQLException when the engine refuses a row
   */
  private void insertEventWithAttendee(long eventId, long attendeeId, int response) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO EXO_AGENDA_CALENDAR (CALENDAR_ID, OWNER_ID, CREATED_DATE) VALUES (5, 1, CURRENT_TIMESTAMP)");
      statement.executeUpdate("INSERT INTO " + EVENT_TABLE
          + " (EVENT_ID, CALENDAR_ID, CREATOR_ID, START_DATE, END_DATE, ALL_DAY, AVAILABILITY, STATUS, CREATED_DATE, UPDATED_DATE,"
          + " ALLOW_ATTENDEE_TO_UPDATE, ALLOW_ATTENDEE_TO_INVITE) VALUES (" + eventId
          + ", 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, FALSE)");
    }
    insertAttendee(attendeeId, eventId, 201, response);
  }

  private void insertAttendee(long attendeeId, long eventId, long identityId, int response) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + ATTENDEE_TABLE + " (EVENT_ATTENDEE_ID, EVENT_ID, IDENTITY_ID, RESPONSE) VALUES ("
          + attendeeId + ", " + eventId + ", " + identityId + ", " + response + ")");
    }
    // Liquibase leaves the connection out of auto-commit after an update: the
    // rows must be committed, or its next run discards them with its own work
    if (!connection.getAutoCommit()) {
      connection.commit();
    }
  }

  /**
   * An integer column, as an {@code Integer} so that a SQL NULL comes back as
   * null.
   * <p>
   * Read with {@code getObject} and not {@code getInt}, which returns 0 for a
   * SQL NULL — the same 0 that means "no limit". With {@code getInt} the
   * backfill test stays green when the changeset's {@code defaultValueNumeric}
   * is deleted.
   *
   * @param sql a query returning one row and one column
   * @return the value, null when the column is NULL
   * @throws SQLException on a query failure
   */
  private Integer intColumn(String sql) throws SQLException {
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
      assertTrue(rows.next(), "the row must still be there");
      return rows.getObject(1, Integer.class);
    }
  }

  private Object objectColumn(String sql) throws SQLException {
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
      assertTrue(rows.next(), "the row must still be there");
      return rows.getObject(1);
    }
  }

  private boolean columnExists(String table, String column) throws SQLException {
    try (ResultSet rows = connection.getMetaData().getColumns(null, null, table, column)) {
      return rows.next();
    }
  }

  /**
   * The columns of an index, in their order, empty when the index does not
   * exist.
   *
   * @param table table name
   * @param index index name
   * @return the column names
   * @throws SQLException on a metadata failure
   */
  private List<String> indexColumns(String table, String index) throws SQLException {
    List<String> columns = new ArrayList<>();
    try (ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
      while (rows.next()) {
        if (index.equals(rows.getString("INDEX_NAME"))) {
          columns.add(rows.getString("COLUMN_NAME"));
        }
      }
    }
    return columns;
  }

  private static Liquibase liquibase(Connection connection) throws Exception {
    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }

}
