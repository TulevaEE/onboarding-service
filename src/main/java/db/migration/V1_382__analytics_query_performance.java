package db.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1_382__analytics_query_performance extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String RATE_CHANGE_INDEX = "idx_unit_owner_p2_rate_change";

  private static final String VACUUM_INSERT_ONLY_TABLES_BY_ROW_COUNT_NOT_TABLE_SHARE =
      """
      ALTER TABLE event_log SET (
        autovacuum_vacuum_insert_scale_factor = 0,
        autovacuum_vacuum_insert_threshold = 100000
      );
      ALTER TABLE unit_owner SET (
        autovacuum_vacuum_insert_scale_factor = 0,
        autovacuum_vacuum_insert_threshold = 200000
      )
      """;

  private static final String CREATE_RATE_CHANGE_INDEX =
      """
      CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_unit_owner_p2_rate_change
        ON unit_owner (snapshot_date, personal_id)
        INCLUDE (p2_rate, p2_next_rate, p2_choice, p2_rava_status, p2_duty_end, death_date)
        WHERE p2_next_rate IS NOT NULL AND p2_rate IS DISTINCT FROM p2_next_rate
      """;

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(VACUUM_INSERT_ONLY_TABLES_BY_ROW_COUNT_NOT_TABLE_SHARE);
      dropRateChangeIndexLeftInvalidByAnInterruptedBuild(statement);
      statement.execute(CREATE_RATE_CHANGE_INDEX);
    }
  }

  private void dropRateChangeIndexLeftInvalidByAnInterruptedBuild(Statement statement)
      throws Exception {
    if (isInvalid(statement, RATE_CHANGE_INDEX)) {
      statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + RATE_CHANGE_INDEX);
    }
  }

  private boolean isInvalid(Statement statement, String indexName) throws Exception {
    try (ResultSet resultSet =
        statement.executeQuery(
            "SELECT NOT indisvalid FROM pg_index WHERE indexrelid = to_regclass('public."
                + indexName
                + "')")) {
      return resultSet.next() && resultSet.getBoolean(1);
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
