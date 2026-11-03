package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1_322__benchmark_proxy_period_history_trigger extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String CREATE_TRIGGER =
      """
      CREATE TRIGGER benchmark_proxy_period_history_trigger
          AFTER INSERT OR UPDATE OR DELETE ON benchmark_proxy_period
          FOR EACH ROW EXECUTE FUNCTION record_reference_data_change('holding_isin');
      """;

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(CREATE_TRIGGER);
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
