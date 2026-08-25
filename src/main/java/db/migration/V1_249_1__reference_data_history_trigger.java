package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// Java rather than SQL because H2 cannot parse plpgsql
public class V1_249_1__reference_data_history_trigger extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String CREATE_FUNCTION =
      """
      CREATE OR REPLACE FUNCTION record_reference_data_change() RETURNS trigger AS $$
      DECLARE
          key_column text := TG_ARGV[0];
      BEGIN
          IF (TG_OP = 'DELETE') THEN
              INSERT INTO reference_data_history
                  (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
              VALUES (TG_TABLE_NAME, to_jsonb(OLD) ->> key_column, 'DELETE',
                      to_jsonb(OLD), NULL, session_user, now());
              RETURN OLD;
          ELSIF (TG_OP = 'UPDATE') THEN
              INSERT INTO reference_data_history
                  (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
              VALUES (TG_TABLE_NAME, to_jsonb(NEW) ->> key_column, 'UPDATE',
                      to_jsonb(OLD), to_jsonb(NEW), session_user, now());
              RETURN NEW;
          ELSE
              INSERT INTO reference_data_history
                  (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
              VALUES (TG_TABLE_NAME, to_jsonb(NEW) ->> key_column, 'INSERT',
                      NULL, to_jsonb(NEW), session_user, now());
              RETURN NEW;
          END IF;
      END;
      $$ LANGUAGE plpgsql;
      """;

  private static final String[] RECREATE_TRIGGERS = {
    "DROP TRIGGER IF EXISTS instrument_reference_history_trigger ON instrument_reference;",
    """
    CREATE TRIGGER instrument_reference_history_trigger
        AFTER INSERT OR UPDATE OR DELETE ON instrument_reference
        FOR EACH ROW EXECUTE FUNCTION record_reference_data_change('isin');
    """,
    "DROP TRIGGER IF EXISTS benchmark_category_proxy_history_trigger ON benchmark_category_proxy;",
    """
    CREATE TRIGGER benchmark_category_proxy_history_trigger
        AFTER INSERT OR UPDATE OR DELETE ON benchmark_category_proxy
        FOR EACH ROW EXECUTE FUNCTION record_reference_data_change('benchmark_category');
    """
  };

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(CREATE_FUNCTION);
      for (String recreateTrigger : RECREATE_TRIGGERS) {
        statement.execute(recreateTrigger);
      }
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
