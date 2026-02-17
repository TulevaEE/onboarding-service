package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NavLedgerRepository {

  private final JdbcClient jdbcClient;

  public BigDecimal sumBalanceByAccountName(String accountName) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0) AS total_balance
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
              AND a.purpose = 'USER_ACCOUNT'
            """)
        .param("accountName", accountName)
        .query(BigDecimal.class)
        .single();
  }

  public BigDecimal getSystemAccountBalance(String accountName) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0) AS total_balance
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
              AND a.purpose = 'SYSTEM_ACCOUNT'
            """)
        .param("accountName", accountName)
        .query(BigDecimal.class)
        .single();
  }
}
