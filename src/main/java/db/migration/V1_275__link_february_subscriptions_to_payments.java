package db.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V1_275__link_february_subscriptions_to_payments extends BaseJavaMigration {

  private static final Logger log =
      LoggerFactory.getLogger(V1_275__link_february_subscriptions_to_payments.class);

  private static final String POSTGRESQL = "PostgreSQL";

  private static final String PAIRS =
      """
      WITH subscriptions AS (
        SELECT t.id,
               t.transaction_date,
               t.metadata->>'personalCode' AS party_code,
               -e.amount AS amount,
               row_number() OVER (
                 PARTITION BY t.metadata->>'personalCode', -e.amount
                 ORDER BY t.transaction_date, t.id) AS position
          FROM ledger.transaction t
          JOIN ledger.entry e ON e.transaction_id = t.id
          JOIN ledger.account a ON a.id = e.account_id AND a.name = 'SUBSCRIPTIONS'
         WHERE t.transaction_type = 'FUND_SUBSCRIPTION'
           AND t.external_reference IS NULL
           AND t.metadata->>'personalCode' IS NOT NULL
      ),
      payments AS (
        SELECT p.id,
               p.party_code,
               p.amount,
               p.received_before,
               row_number() OVER (
                 PARTITION BY p.party_code, p.amount
                 ORDER BY p.received_before, p.created_at, p.id) AS position
          FROM saving_fund_payment p
         WHERE p.party_type = 'PERSON'
           AND p.status IN ('ISSUED', 'PROCESSED')
           AND p.amount > 0
           AND p.received_before < '2026-02-19'
           AND NOT EXISTS (
                 SELECT 1 FROM ledger.transaction linked
                  WHERE linked.transaction_type = 'FUND_SUBSCRIPTION'
                    AND linked.external_reference = p.id)
      )
      SELECT s.id AS transaction_id, p.id AS payment_id
        FROM subscriptions s
        JOIN payments p
          ON p.party_code = s.party_code
         AND p.amount = s.amount
         AND p.position = s.position
         AND p.received_before < s.transaction_date
      """;

  private static final String LINK =
      """
      WITH pairs AS (%s)
      UPDATE ledger.transaction t
         SET external_reference = pairs.payment_id
        FROM pairs
       WHERE t.id = pairs.transaction_id
      """
          .formatted(PAIRS);

  private static final String COUNT_UNLINKED_SUBSCRIPTIONS =
      """
      SELECT count(*) FROM ledger.transaction
       WHERE transaction_type = 'FUND_SUBSCRIPTION' AND external_reference IS NULL
      """;

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      int linked = statement.executeUpdate(LINK);
      try (ResultSet unlinked = statement.executeQuery(COUNT_UNLINKED_SUBSCRIPTIONS)) {
        unlinked.next();
        log.info(
            "Linked subscriptions to their payments: linked={}, stillUnlinked={}",
            linked,
            unlinked.getLong(1));
      }
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }
}
