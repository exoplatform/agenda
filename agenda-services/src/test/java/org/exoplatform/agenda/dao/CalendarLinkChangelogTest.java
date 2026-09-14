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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.agenda.entity.CalendarLinkEntity;

/**
 * Runs agenda's real changelog on HSQLDB and checks what EXO-90252 adds to it:
 * the calendar link table applies, rolls back and re-applies, carries every
 * column the entity maps, and enforces one link per calendar and one row per
 * token digest in the engine itself.
 */
class CalendarLinkChangelogTest {

  /** agenda's master changelog, the one the Kernel changelogs plugin runs. */
  static final String CHANGELOG = "db/changelog/agenda-rdbms.db.changelog.xml";

  private static final String TABLE     = "EXO_AGENDA_CALENDAR_LINK";

  private static final String SEQUENCE  = "SEQ_AGENDA_CALENDAR_LINK_ID";

  /** The changesets EXO-90252 adds, both run on HSQLDB. */
  private static final int    ADDED_CHANGESETS = 2;

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:agenda-link-changelog-" + System.nanoTime(), "sa", "");
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
   * The whole changelog applies and creates the link table and its sequence.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theChangelogCreatesTheLinkTable() throws Exception {
    update(connection);

    assertTrue(tableExists(TABLE), "the link table must exist once the changelog has run");
    assertTrue(sequenceExists(SEQUENCE), "and so must the sequence the entity's generator reads on HSQLDB");
  }

  /**
   * Rolling back the two added changesets removes what they created, and the
   * changelog applies again from there — so neither shipped with an unusable
   * rollback.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theAddedChangesetsRollBackAndReapply() throws Exception {
    update(connection);
    liquibase(connection).rollback(ADDED_CHANGESETS, new Contexts(), new LabelExpression());

    assertFalse(tableExists(TABLE), "rolling back must drop the link table");
    assertFalse(sequenceExists(SEQUENCE), "and its sequence");
    assertTrue(tableExists("EXO_AGENDA_CALENDAR"), "and nothing that came before it");

    update(connection);

    assertTrue(tableExists(TABLE), "re-applying must rebuild the table");
    assertTrue(sequenceExists(SEQUENCE), "and the sequence");
  }

  /**
   * Every column the entity maps is one the changelog creates.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void everyColumnTheEntityMapsExists() throws Exception {
    update(connection);

    Table table = CalendarLinkEntity.class.getAnnotation(Table.class);
    assertEquals(TABLE, table.name());
    int mapped = 0;
    for (java.lang.reflect.Field field : CalendarLinkEntity.class.getDeclaredFields()) {
      Column column = field.getAnnotation(Column.class);
      if (column != null) {
        mapped++;
        assertTrue(columnExists(TABLE, column.name()), "column " + column.name() + " must exist");
      }
    }
    assertEquals(6, mapped, "the entity maps six columns");
  }

  /**
   * A calendar has at most one link: the engine refuses a second row for the
   * same calendar, whatever the service does.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void aCalendarHasAtMostOneLink() throws Exception {
    update(connection);
    insert(1, 42, 7, "a".repeat(64));

    SQLException refused = assertThrows(SQLException.class, () -> insert(2, 42, 8, "b".repeat(64)));
    assertTrue(refused.getMessage().toUpperCase().contains("UK_AGENDA_CALENDAR_LINK_CAL"),
               "the refusal must come from the calendar uniqueness constraint: " + refused.getMessage());
    insert(3, 43, 8, "c".repeat(64));
  }

  /**
   * One digest opens one link: the engine refuses a second row with the same
   * token digest.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void aTokenDigestOpensOneLink() throws Exception {
    update(connection);
    insert(1, 42, 7, "a".repeat(64));

    SQLException refused = assertThrows(SQLException.class, () -> insert(2, 43, 7, "a".repeat(64)));
    assertTrue(refused.getMessage().toUpperCase().contains("UK_AGENDA_CALENDAR_LINK_HASH"),
               "the refusal must come from the digest uniqueness constraint: " + refused.getMessage());
  }

  /**
   * Applies agenda's changelog to a connection.
   *
   * @param connection the database
   * @throws Exception when Liquibase fails
   */
  static void update(Connection connection) throws Exception {
    liquibase(connection).update(new Contexts(), new LabelExpression());
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

  /**
   * Inserts a link row directly.
   *
   * @param id row identifier
   * @param calendarId calendar identifier
   * @param creatorId creator identifier
   * @param tokenHash digest
   * @throws SQLException when the engine refuses the row
   */
  private void insert(long id, long calendarId, long creatorId, String tokenHash) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + TABLE + " (LINK_ID, CALENDAR_ID, CREATOR_ID, TOKEN_HASH, TOKEN_ENCRYPTED, CREATED_DATE) VALUES ("
          + id + ", " + calendarId + ", " + creatorId + ", '" + tokenHash + "', 'encrypted', CURRENT_TIMESTAMP)");
    }
  }

  /**
   * Whether a table exists.
   *
   * @param table table name
   * @return true when it exists
   * @throws SQLException on a metadata failure
   */
  private boolean tableExists(String table) throws SQLException {
    try (ResultSet rows = connection.getMetaData().getTables(null, null, table, null)) {
      return rows.next();
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
   * Whether a sequence exists.
   *
   * @param sequence sequence name
   * @return true when it exists
   * @throws SQLException on a query failure
   */
  private boolean sequenceExists(String sequence) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME = '"
            + sequence + "'")) {
      rows.next();
      return rows.getInt(1) > 0;
    }
  }

}
