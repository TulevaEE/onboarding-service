package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1_262__instrument_reference_write_guards extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String CREATE_INSTRUMENT_GUARD =
      """
      CREATE OR REPLACE FUNCTION guard_instrument_reference_update() RETURNS trigger AS $$
      DECLARE
          proxy_categories text;
      BEGIN
          NEW.updated_at := now();

          IF OLD.active AND NOT NEW.active THEN
              SELECT string_agg(benchmark_category, ', ' ORDER BY benchmark_category)
                INTO proxy_categories
                FROM benchmark_category_proxy
               WHERE etf_proxy_isin = OLD.isin OR index_proxy_isin = OLD.isin;

              IF proxy_categories IS NOT NULL THEN
                  RAISE EXCEPTION
                      'Cannot deactivate %: it is still the benchmark proxy for %. Point those categories at another instrument first, in the same transaction.',
                      OLD.isin, proxy_categories;
              END IF;
          END IF;

          RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;
      """;

  private static final String CREATE_PROXY_GUARD =
      """
      CREATE OR REPLACE FUNCTION guard_benchmark_category_proxy_write() RETURNS trigger AS $$
      DECLARE
          inactive_isin text;
      BEGIN
          SELECT isin
            INTO inactive_isin
            FROM instrument_reference
           WHERE isin IN (NEW.etf_proxy_isin, NEW.index_proxy_isin)
             AND NOT active
           ORDER BY isin
           LIMIT 1;

          IF inactive_isin IS NOT NULL THEN
              RAISE EXCEPTION
                  'Cannot point benchmark category % at %: that instrument is active = false, so its prices are no longer fetched and the benchmark would freeze.',
                  NEW.benchmark_category, inactive_isin;
          END IF;

          RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;
      """;

  private static final String[] RECREATE_TRIGGERS = {
    "DROP TRIGGER IF EXISTS instrument_reference_write_guard_trigger ON instrument_reference;",
    """
    CREATE TRIGGER instrument_reference_write_guard_trigger
        BEFORE UPDATE ON instrument_reference
        FOR EACH ROW EXECUTE FUNCTION guard_instrument_reference_update();
    """,
    "DROP TRIGGER IF EXISTS benchmark_category_proxy_write_guard_trigger"
        + " ON benchmark_category_proxy;",
    """
    CREATE TRIGGER benchmark_category_proxy_write_guard_trigger
        BEFORE INSERT OR UPDATE ON benchmark_category_proxy
        FOR EACH ROW EXECUTE FUNCTION guard_benchmark_category_proxy_write();
    """
  };

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(CREATE_INSTRUMENT_GUARD);
      statement.execute(CREATE_PROXY_GUARD);
      for (String recreateTrigger : RECREATE_TRIGGERS) {
        statement.execute(recreateTrigger);
      }
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
