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

import org.exoplatform.agenda.entity.CalendarShareEntity;

/**
 * Runs agenda's real changelog on HSQLDB and checks what EXO-90357 adds to it:
 * the calendar share table applies, rolls back and re-applies, carries every
 * column the entity maps, and refuses a second share of a calendar with the
 * same colleague in the engine itself.
 */
class CalendarShareChangelogTest {

  private static final String TABLE           = "EXO_AGENDA_CALENDAR_SHARE";

  private static final String SEQUENCE        = "SEQ_AGENDA_CALENDAR_SHARE_ID";

  private static final String INDEX           = "IDX_AGENDA_CALENDAR_SHARE_SHAREE";

  /** The first changeset EXO-90357 adds. */
  private static final String FIRST_CHANGESET = "1.0.0-45";

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:agenda-share-changelog-" + System.nanoTime(), "sa", "");
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
   * The whole changelog applies and creates the share table, its index and its
   * sequence.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theChangelogCreatesTheShareTable() throws Exception {
    CalendarLinkChangelogTest.update(connection);

    assertTrue(tableExists(TABLE), "the share table must exist once the changelog has run");
    assertTrue(indexExists(INDEX), "and its sharee index, which every ACL read is served by");
    assertTrue(sequenceExists(SEQUENCE), "and the sequence the entity's generator reads on HSQLDB");
  }

  /**
   * Rolling back the four added changesets removes what they created, and the
   * changelog applies again from there — so none shipped with an unusable
   * rollback.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theAddedChangesetsRollBackAndReapply() throws Exception {
    CalendarLinkChangelogTest.update(connection);
    // Counted from EXO-90357's first changeset to the end of the changelog, so
    // it also rolls back what later deliveries appended (EXO-90373 appended
    // two): the four of EXO-90357 are a floor, not the count
    int added = CalendarLinkChangelogTest.changesetsSince(connection, FIRST_CHANGESET);
    assertTrue(added >= 4, "EXO-90357 adds the table, its unique key, its index and its sequence");
    liquibase(connection).rollback(added, new Contexts(), new LabelExpression());

    assertFalse(tableExists(TABLE), "rolling back must drop the share table");
    assertFalse(sequenceExists(SEQUENCE), "and its sequence");
    assertTrue(tableExists("EXO_AGENDA_CALENDAR_LINK"), "and nothing that came before it");

    CalendarLinkChangelogTest.update(connection);

    assertTrue(tableExists(TABLE), "re-applying must rebuild the table");
    assertTrue(indexExists(INDEX), "the index");
    assertTrue(sequenceExists(SEQUENCE), "and the sequence");
  }

  /**
   * Every column the entity maps is one the changelog creates.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void everyColumnTheEntityMapsExists() throws Exception {
    CalendarLinkChangelogTest.update(connection);

    Table table = CalendarShareEntity.class.getAnnotation(Table.class);
    assertEquals(TABLE, table.name());
    int mapped = 0;
    for (java.lang.reflect.Field field : CalendarShareEntity.class.getDeclaredFields()) {
      Column column = field.getAnnotation(Column.class);
      if (column != null) {
        mapped++;
        assertTrue(columnExists(TABLE, column.name()), "column " + column.name() + " must exist");
      }
    }
    assertEquals(9, mapped, "the entity maps nine columns");
  }

  /**
   * A calendar is shared once with a colleague: the engine refuses a second
   * row for the same pair, whatever the service does, and accepts the same
   * colleague on another calendar and another colleague on the same one.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void aCalendarIsSharedOnceWithAColleague() throws Exception {
    CalendarLinkChangelogTest.update(connection);
    insert(1, 42, 7);

    SQLException refused = assertThrows(SQLException.class, () -> insert(2, 42, 7));
    assertTrue(refused.getMessage().toUpperCase().contains("UK_AGENDA_CALENDAR_SHARE"),
               "the refusal must come from the pair's uniqueness constraint: " + refused.getMessage());
    insert(3, 43, 7);
    insert(4, 42, 8);
  }

  /**
   * A row inserted without saying so is not hidden: the column's default is
   * what makes a share visible until its sharee decides otherwise.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void aShareIsVisibleByDefault() throws Exception {
    CalendarLinkChangelogTest.update(connection);
    insert(1, 42, 7);

    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT HIDDEN FROM " + TABLE + " WHERE SHARE_ID = 1")) {
      assertTrue(rows.next());
      assertFalse(rows.getBoolean(1));
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
    return new Liquibase(CalendarLinkChangelogTest.CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }

  /**
   * Inserts a share row directly, without the optional columns.
   *
   * @param id row identifier
   * @param calendarId calendar identifier
   * @param shareeId sharee identity identifier
   * @throws SQLException when the engine refuses the row
   */
  private void insert(long id, long calendarId, long shareeId) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + TABLE
          + " (SHARE_ID, CALENDAR_ID, SHAREE_IDENTITY_ID, GRANTED_BY_IDENTITY_ID, CREATED_DATE, SOURCE) VALUES ("
          + id + ", " + calendarId + ", " + shareeId + ", 1, CURRENT_TIMESTAMP, 'EXO')");
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
   * Whether an index exists.
   *
   * @param index index name
   * @return true when it exists
   * @throws SQLException on a query failure
   */
  private boolean indexExists(String index) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO WHERE INDEX_NAME = '"
            + index + "'")) {
      rows.next();
      return rows.getInt(1) > 0;
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
