package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1_382__analytics_query_performance extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String CREATE_RATE_CHANGE_INDEX =
      """
      CREATE INDEX IF NOT EXISTS idx_unit_owner_p2_rate_change
        ON unit_owner (snapshot_date, personal_id)
        INCLUDE (p2_rate, p2_next_rate, p2_choice, p2_rava_status, p2_duty_end, death_date)
        WHERE p2_next_rate IS NOT NULL AND p2_rate IS DISTINCT FROM p2_next_rate
      """;

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(CREATE_RATE_CHANGE_INDEX);
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
