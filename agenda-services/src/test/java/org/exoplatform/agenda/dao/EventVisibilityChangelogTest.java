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

import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.CHANGELOG;
import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.changesetsSince;
import static org.exoplatform.agenda.dao.CalendarLinkChangelogTest.update;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

import org.exoplatform.agenda.constant.EventVisibility;

/**
 * Runs agenda's real changelog on HSQLDB and checks what EXO-90322 adds to it:
 * the event visibility column applies, rolls back and re-applies, and backfills
 * every row that existed before it with the ordinal of
 * {@link EventVisibility#DEFAULT}, so no stored event changes what it publishes.
 * <p>
 * Written against the engine rather than mocked because the two things that can
 * be wrong here — a rollback that does not exist, and a default that is not the
 * ordinal the enum reads back — are invisible to anything but a real database.
 */
class EventVisibilityChangelogTest {

  private static final String TABLE           = "EXO_AGENDA_EVENT";

  private static final String COLUMN          = "VISIBILITY";

  /** The changeset EXO-90322 adds. */
  private static final String FIRST_CHANGESET = "1.0.0-44";

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:agenda-visibility-changelog-" + System.nanoTime(), "sa", "");
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
   * The whole changelog applies and the event table carries the column the
   * entity maps.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theChangelogAddsTheVisibilityColumn() throws Exception {
    update(connection);

    assertTrue(columnExists(TABLE, COLUMN), "the visibility column must exist once the changelog has run");
  }

  /**
   * Rolling the changeset back drops the column and leaves the rest of the
   * event table alone, and the changelog applies again from there — so it did
   * not ship with an unusable rollback.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theAddedChangesetRollsBackAndReapplies() throws Exception {
    update(connection);
    liquibase(connection).rollback(changesetsSince(connection, FIRST_CHANGESET), new Contexts(), new LabelExpression());

    assertFalse(columnExists(TABLE, COLUMN), "rolling back must drop the column");
    assertTrue(columnExists(TABLE, "AVAILABILITY"), "and nothing that came before it");

    update(connection);

    assertTrue(columnExists(TABLE, COLUMN), "re-applying must add it again");
  }

  /**
   * An event written before the column existed publishes what it always did:
   * the backfilled value is the ordinal {@link EventVisibility#DEFAULT} reads
   * back as, which {@code AgendaCalendarLinkServiceImpl.isPrivate} does not
   * mask. A default of anything else would turn every existing calendar into
   * busy blocks on upgrade.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void anEventWrittenBeforeTheColumnKeepsPublishingInFull() throws Exception {
    update(connection);
    liquibase(connection).rollback(changesetsSince(connection, FIRST_CHANGESET), new Contexts(), new LabelExpression());
    insertEventWithoutVisibility(1);

    update(connection);

    Integer stored = visibilityOf(1);
    assertNotNull(stored, "the row that predates the column is backfilled, not left NULL");
    assertEquals(EventVisibility.DEFAULT.ordinal(),
                 stored.intValue(),
                 "and backfilled with DEFAULT, which does not mask");
  }

  /**
   * Inserts an event row the way it looked before the column existed.
   *
   * @param id row identifier
   * @throws SQLException when the engine refuses the row
   */
  private void insertEventWithoutVisibility(long id) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO EXO_AGENDA_CALENDAR (CALENDAR_ID, OWNER_ID, CREATED_DATE) VALUES (5, 1, CURRENT_TIMESTAMP)");
      statement.executeUpdate("INSERT INTO " + TABLE
          + " (EVENT_ID, CALENDAR_ID, CREATOR_ID, START_DATE, END_DATE, ALL_DAY, AVAILABILITY, STATUS, CREATED_DATE, UPDATED_DATE,"
          + " ALLOW_ATTENDEE_TO_UPDATE, ALLOW_ATTENDEE_TO_INVITE) VALUES (" + id
          + ", 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, FALSE)");
    }
  }

  /**
   * The stored visibility of an event, as an {@code Integer} so that a SQL NULL
   * comes back as null.
   * <p>
   * Read with {@code getObject} and not {@code getInt}, which returns 0 for a
   * SQL NULL — the same 0 that {@link EventVisibility#DEFAULT} has. With
   * {@code getInt} this whole test stays green when the changeset's
   * {@code defaultValueNumeric} is deleted: the column is then left NULL on
   * existing rows and the assertion cannot tell that from a backfill. Measured
   * on the HSQLDB this suite runs against, with the default declared and
   * without it.
   *
   * @param id row identifier
   * @return the ordinal stored in the column, null when the column is NULL
   * @throws SQLException on a query failure
   */
  private Integer visibilityOf(long id) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT " + COLUMN + " FROM " + TABLE + " WHERE EVENT_ID = " + id)) {
      assertTrue(rows.next(), "the event row must still be there");
      return rows.getObject(1, Integer.class);
    }
  }

  /**
   * Whether a column exists.
   *
   * @param table table name
   * @param column column name
   * @return true when it exists
   * @throws SQLException on a metadata failure
   */
  private boolean columnExists(String table, String column) throws SQLException {
    try (ResultSet rows = connection.getMetaData().getColumns(null, null, table, column)) {
      return rows.next();
    }
  }

  /**
   * A Liquibase instance over agenda's changelog.
   *
   * @param connection the database
   * @return the instance
   * @throws Exception when the database cannot be wrapped
   */
  private static Liquibase liquibase(Connection connection) throws Exception {
    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }

}
