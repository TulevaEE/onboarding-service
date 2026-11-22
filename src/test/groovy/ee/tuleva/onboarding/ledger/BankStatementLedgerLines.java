package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public record BankStatementLedgerLines(JdbcClient jdbcClient) {

  public void book(
      TulevaFund fund,
      String transactionType,
      LocalDateTime transactionDateUtc,
      SystemAccount account,
      String amount) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST(:type AS ledger.transaction_type), :date, '{}')
            """)
        .param("id", transactionId)
        .param("type", transactionType)
        .param("date", transactionDateUtc)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.entry (id, account_id, transaction_id, amount, asset_type)
            VALUES (:id, :accountId, :transactionId, :amount, CAST('EUR' AS ledger.asset_type))
            """)
        .param("id", UUID.randomUUID())
        .param("accountId", systemAccount(account.getAccountName(fund)))
        .param("transactionId", transactionId)
        .param("amount", new BigDecimal(amount))
        .update();
  }

  private UUID systemAccount(String name) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.account (id, name, purpose, account_type, asset_type)
            VALUES (:id, :name,
              CAST('SYSTEM_ACCOUNT' AS ledger.account_purpose),
              CAST('EXPENSE' AS ledger.account_type),
              CAST('EUR' AS ledger.asset_type))
            ON CONFLICT DO NOTHING
            """)
        .param("id", UUID.randomUUID())
        .param("name", name)
        .update();
    return jdbcClient
        .sql(
            """
            SELECT id FROM ledger.account
            WHERE name = :name AND owner_party_id IS NULL AND purpose = 'SYSTEM_ACCOUNT'
            """)
        .param("name", name)
        .query(UUID.class)
        .single();
  }
}
