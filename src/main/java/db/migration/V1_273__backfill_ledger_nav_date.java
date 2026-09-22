package db.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V1_273__backfill_ledger_nav_date extends BaseJavaMigration {

  private static final Logger log = LoggerFactory.getLogger(V1_273__backfill_ledger_nav_date.class);

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String PRICED_TRANSACTIONS =
      """
      t.transaction_type IN ('FUND_SUBSCRIPTION', 'REDEMPTION_REQUEST')
        AND t.metadata->>'navPerUnit' IS NOT NULL
        AND t.metadata->>'navDate' IS NULL
      """;

  private static final String NAV_THE_ORDER_WAS_PRICED_AT =
      """
      SELECT published.%s
        FROM index_values published
       WHERE published.key = 'EE0000003283'
         AND published.date
             < (t.transaction_date AT TIME ZONE 'UTC' AT TIME ZONE 'Europe/Tallinn')::date
       ORDER BY published.date DESC
       LIMIT 1
      """;

  private static final String BACKFILL =
      """
      UPDATE ledger.transaction t
         SET metadata = jsonb_set(t.metadata, '{navDate}', to_jsonb((%s)))
       WHERE %s
         AND (%s) = (t.metadata->>'navPerUnit')::numeric
      """
          .formatted(
              NAV_THE_ORDER_WAS_PRICED_AT.formatted("date::text"),
              PRICED_TRANSACTIONS,
              NAV_THE_ORDER_WAS_PRICED_AT.formatted("value"));

  private static final String COUNT_LEFT_WITHOUT_A_NAV_DATE =
      """
      SELECT count(*) FROM ledger.transaction t WHERE %s
      """
          .formatted(PRICED_TRANSACTIONS);

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      int backfilled = statement.executeUpdate(BACKFILL);
      try (ResultSet left = statement.executeQuery(COUNT_LEFT_WITHOUT_A_NAV_DATE)) {
        left.next();
        log.info(
            "Backfilled the nav date of priced ledger transactions: backfilled={}, unmatched={}",
            backfilled,
            left.getLong(1));
      }
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
