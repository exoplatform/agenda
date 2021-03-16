package org.exoplatform.agenda.liquibase;

import java.sql.CallableStatement;
import java.sql.ResultSet;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.*;
import liquibase.resource.ResourceAccessor;

public class DropHSQLDBUniqueConstraint implements CustomTaskChange {

  private String tableName;

  private String uniqueColumns;

  @Override
  public String getConfirmationMessage() {
    return "Unique constraint for table '" + tableName + "' and unique columns '" + uniqueColumns + "' dropped";
  }

  @Override
  public void setUp() throws SetupException {
    // Not used
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    // Not used
  }

  @Override
  public ValidationErrors validate(Database database) {
    // Not used
    return null;
  }

  @Override
  public void execute(Database database) throws CustomChangeException {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    try {
      connection.attached(database);
      CallableStatement statement =
                                  connection.prepareCall("SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'UNIQUE' AND TABLE_NAME = '"
                                      + tableName + "'");
      ResultSet indexResults = statement.executeQuery();
      if (!indexResults.next()) {
        return;
      }
      String constraintName = indexResults.getString(1);
      CallableStatement dropConstraintQuery = connection.prepareCall("ALTER TABLE " + tableName + " DROP CONSTRAINT "
          + constraintName);
      dropConstraintQuery.execute();
      connection.commit();
    } catch (Exception e) {
      throw new CustomChangeException("Error dropping constraint of table " +
          tableName + "for unique columns " + uniqueColumns, e);
    }
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getUniqueColumns() {
    return uniqueColumns;
  }

  public void setUniqueColumns(String uniqueColumns) {
    this.uniqueColumns = uniqueColumns;
  }

}
