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
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.CalendarSubscriptionEntity;
import org.exoplatform.agenda.entity.CalendarSubscriptionEventEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Runs agenda's real changelog on HSQLDB and checks what EXO-90278 adds to it:
 * the subscription flag on calendars — false for every existing one — and the
 * two subscription tables apply, roll back and re-apply; every mapped column
 * exists; the engine enforces the unique keys; the indexes exist.
 */
class CalendarSubscriptionChangelogTest {

  private static final String SUBSCRIPTION    = "EXO_AGENDA_SUBSCRIPTION";

  private static final String SUBSCRIPTION_EVENT = "EXO_AGENDA_SUBSCRIPTION_EVENT";

  private static final String FIRST_CHANGESET = "1.0.0-39";

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:agenda-subscription-changelog-" + System.nanoTime(), "sa", "");
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
   * The changelog creates both tables and their sequences, and a calendar
   * written without the new column is not a subscription.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theChangelogCreatesTheTablesAndNoCalendarIsASubscription() throws Exception {
    CalendarLinkChangelogTest.update(connection);

    assertTrue(tableExists(SUBSCRIPTION));
    assertTrue(tableExists(SUBSCRIPTION_EVENT));
    assertTrue(sequenceExists("SEQ_AGENDA_SUBSCRIPTION_ID"));
    assertTrue(sequenceExists("SEQ_AGENDA_SUBSCRIPTION_EVT_ID"));
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO EXO_AGENDA_CALENDAR (CALENDAR_ID, OWNER_ID, IS_SYSTEM, COLOR, CREATED_DATE)"
          + " VALUES (1, 7, TRUE, '#000000', CURRENT_TIMESTAMP)");
      try (ResultSet rows = statement.executeQuery("SELECT IS_SUBSCRIPTION FROM EXO_AGENDA_CALENDAR WHERE CALENDAR_ID = 1")) {
        assertTrue(rows.next());
        assertFalse(rows.getBoolean(1), "an existing calendar is not a subscription");
      }
    }
  }

  /**
   * Rolling back what EXO-90278 added removes it all and leaves Publish's table,
   * and the changelog applies again from there.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theAddedChangesetsRollBackAndReapply() throws Exception {
    CalendarLinkChangelogTest.update(connection);
    int added = CalendarLinkChangelogTest.changesetsSince(connection, FIRST_CHANGESET);
    assertEquals(5, added, "EXO-90278 adds five changesets");

    liquibase(connection).rollback(added, new Contexts(), new LabelExpression());

    assertFalse(tableExists(SUBSCRIPTION));
    assertFalse(tableExists(SUBSCRIPTION_EVENT));
    assertFalse(sequenceExists("SEQ_AGENDA_SUBSCRIPTION_ID"));
    assertFalse(sequenceExists("SEQ_AGENDA_SUBSCRIPTION_EVT_ID"));
    assertFalse(columnExists("EXO_AGENDA_CALENDAR", "IS_SUBSCRIPTION"));
    assertTrue(tableExists("EXO_AGENDA_CALENDAR_LINK"), "and nothing that came before");

    CalendarLinkChangelogTest.update(connection);

    assertTrue(tableExists(SUBSCRIPTION));
    assertTrue(tableExists(SUBSCRIPTION_EVENT));
    assertTrue(columnExists("EXO_AGENDA_CALENDAR", "IS_SUBSCRIPTION"));
  }

  /**
   * Every column the entities map is one the changelog creates.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void everyColumnTheEntitiesMapExists() throws Exception {
    CalendarLinkChangelogTest.update(connection);

    assertEquals(17, mappedColumnsExist(CalendarSubscriptionEntity.class));
    assertEquals(5, mappedColumnsExist(CalendarSubscriptionEventEntity.class));
    assertTrue(columnExists("EXO_AGENDA_CALENDAR", CalendarEntity.class.getDeclaredField("isSubscription")
                                                                       .getAnnotation(Column.class)
                                                                       .name()));
  }

  /**
   * The engine refuses a second subscription for a calendar, a second
   * subscription of a user to a URL, a second row for an event and a second row
   * for an occurrence identity.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theUniqueKeysAreEnforcedByTheEngine() throws Exception {
    CalendarLinkChangelogTest.update(connection);
    subscription(1, 42, "a");

    assertRefusedBy("UK_AGENDA_SUBSCRIPTION_CAL", () -> subscription(2, 42, "b"));
    assertRefusedBy("UK_AGENDA_SUBSCRIPTION_KEY", () -> subscription(3, 43, "a"));
    subscription(4, 44, "c");

    occurrence(1, 1, 100, "k1");
    assertRefusedBy("UK_AGENDA_SUB_EVENT_EVT", () -> occurrence(2, 1, 100, "k2"));
    assertRefusedBy("UK_AGENDA_SUB_EVENT_KEY", () -> occurrence(3, 1, 101, "k1"));
    occurrence(4, 1, 102, "k3");
  }

  /**
   * The indexes the due list, the listing and the occurrence reads rely on exist.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  void theIndexesExist() throws Exception {
    CalendarLinkChangelogTest.update(connection);

    assertTrue(indexes(SUBSCRIPTION).containsAll(Set.of("IDX_AGENDA_SUBSCRIPTION_DUE", "IDX_AGENDA_SUBSCRIPTION_USER")));
    assertTrue(indexes(SUBSCRIPTION_EVENT).contains("IDX_AGENDA_SUB_EVENT_SUB"));
  }

  /**
   * Checks that every column an entity maps exists.
   *
   * @param entity the entity class
   * @return how many columns it maps
   * @throws SQLException on a metadata failure
   */
  private int mappedColumnsExist(Class<?> entity) throws SQLException {
    String table = entity.getAnnotation(Table.class).name();
    int mapped = 0;
    for (java.lang.reflect.Field field : entity.getDeclaredFields()) {
      Column column = field.getAnnotation(Column.class);
      if (column != null) {
        mapped++;
        assertTrue(columnExists(table, column.name()), table + "." + column.name() + " must exist");
      }
    }
    return mapped;
  }

  /**
   * Asserts the engine refuses a statement through a named constraint.
   *
   * @param constraint the constraint name
   * @param statement the statement
   */
  private void assertRefusedBy(String constraint, SqlStatement statement) {
    SQLException refused = assertThrows(SQLException.class, statement::run);
    assertTrue(refused.getMessage().toUpperCase().contains(constraint), constraint + " must refuse it: " + refused.getMessage());
  }

  /**
   * Inserts a subscription row.
   *
   * @param id identifier
   * @param calendarId calendar identifier
   * @param key the URL key, repeated to 64 characters
   * @throws SQLException when refused
   */
  private void subscription(long id, long calendarId, String key) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + SUBSCRIPTION
          + " (SUBSCRIPTION_ID, CALENDAR_ID, USER_IDENTITY_ID, URL_ENCRYPTED, URL_KEY, NEXT_REFRESH_DATE, CREATED_DATE) VALUES ("
          + id + ", " + calendarId + ", 7, 'enc', '" + key.repeat(64) + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }
  }

  /**
   * Inserts an occurrence row.
   *
   * @param id identifier
   * @param subscriptionId subscription identifier
   * @param eventId event identifier
   * @param key the occurrence key
   * @throws SQLException when refused
   */
  private void occurrence(long id, long subscriptionId, long eventId, String key) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + SUBSCRIPTION_EVENT
          + " (SUBSCRIPTION_EVENT_ID, SUBSCRIPTION_ID, EVENT_ID, EVENT_KEY, CONTENT_HASH) VALUES (" + id + ", " + subscriptionId
          + ", " + eventId + ", '" + key + "', 'hash')");
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
   * The names of a table's indexes.
   *
   * @param table the table
   * @return the index names
   * @throws SQLException on a metadata failure
   */
  private Set<String> indexes(String table) throws SQLException {
    Set<String> names = new HashSet<>();
    try (ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
      while (rows.next()) {
        names.add(rows.getString("INDEX_NAME"));
      }
    }
    return names;
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

  /**
   * A statement that may be refused.
   */
  @FunctionalInterface
  private interface SqlStatement {

    /**
     * Runs the statement.
     *
     * @throws SQLException when refused
     */
    void run() throws SQLException;
  }

}
