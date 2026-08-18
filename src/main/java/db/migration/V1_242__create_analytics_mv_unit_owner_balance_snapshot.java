package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// Java rather than SQL because H2 has no materialized views
public class V1_242__create_analytics_mv_unit_owner_balance_snapshot extends BaseJavaMigration {

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String CREATE_SCHEMA = "CREATE SCHEMA IF NOT EXISTS analytics;";

  private static final String CREATE_VIEW =
      """
      CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.mv_unit_owner_balance_snapshot AS
      WITH month_end_pick AS (SELECT (date_trunc('month', uo.snapshot_date - 1)
                                         + INTERVAL '1 month' - INTERVAL '1 day')::date AS month_end,
                                     max(uo.snapshot_date - 1)                           AS as_of_date
                              FROM unit_owner uo
                              GROUP BY 1)
      SELECT p.month_end,
             uo.snapshot_date AS source_snapshot_date,
             uo.personal_id,
             uo.first_name,
             uo.last_name,
             uo.phone,
             uo.email,
             uo.country,
             uo.language_preference,
             uo.pension_account,
             uo.death_date,
             uo.fund_manager,
             uo.p2_choice,
             uo.p2_choice_method,
             uo.p2_choice_date,
             uo.p2_rava_date,
             uo.p2_rava_status,
             uo.p2_mmte_date,
             uo.p2_mmte_status,
             uo.p2_rate,
             uo.p2_next_rate,
             uo.p2_next_rate_date,
             uo.p2_ykva_date,
             uo.p2_plav_date,
             uo.p2_fpaa_date,
             uo.p2_duty_start,
             uo.p2_duty_end,
             uo.p3_identification_date,
             uo.p3_identifier,
             uo.p3_block_flag,
             uo.p3_blocker,
             b.security_short_name,
             b.security_name,
             b.balance_type,
             b.balance_amount,
             b.start_date,
             b.last_updated,
             uo.date_created
      FROM month_end_pick p
               JOIN unit_owner uo ON uo.snapshot_date = p.as_of_date + 1
               JOIN unit_owner_balance b ON b.unit_owner_id = uo.id
      WHERE p.as_of_date = p.month_end;
      """;

  private static final String[] CREATE_INDEXES = {
    """
    CREATE INDEX IF NOT EXISTS idx_mv_unit_owner_balance_snapshot_month_end
      ON analytics.mv_unit_owner_balance_snapshot (month_end);
    """,
    """
    CREATE INDEX IF NOT EXISTS idx_mv_unit_owner_balance_snapshot_personal_id
      ON analytics.mv_unit_owner_balance_snapshot (personal_id);
    """,
    """
    CREATE INDEX IF NOT EXISTS idx_mv_unit_owner_balance_snapshot_security_short_name
      ON analytics.mv_unit_owner_balance_snapshot (security_short_name);
    """
  };

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(CREATE_SCHEMA);
      statement.execute(CREATE_VIEW);
      for (String createIndex : CREATE_INDEXES) {
        statement.execute(createIndex);
      }
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
