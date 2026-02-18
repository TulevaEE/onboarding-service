package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_UNITS;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NavLedgerRepository {

  private static final String SECURITIES_UNITS_PREFIX = SECURITIES_UNITS.name() + ":";

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

  public BigDecimal getFundUnitsBalance(String accountName) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0) AS total_balance
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
              AND a.purpose = 'USER_ACCOUNT'
              AND a.asset_type = 'FUND_UNIT'
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

  public Map<String, BigDecimal> getSecuritiesUnitBalances() {
    Map<String, BigDecimal> balances = new HashMap<>();
    jdbcClient
        .sql(
            """
            SELECT a.name, COALESCE(SUM(e.amount), 0) AS total_balance
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name LIKE :prefix
              AND a.purpose = 'SYSTEM_ACCOUNT'
            GROUP BY a.name
            """)
        .param("prefix", SECURITIES_UNITS_PREFIX + "%")
        .query(
            (rs, rowNum) -> {
              String accountName = rs.getString("name");
              BigDecimal balance = rs.getBigDecimal("total_balance");
              String isin = parseIsin(accountName);
              balances.put(isin, balance);
              return null;
            })
        .list();
    return balances;
  }

  private String parseIsin(String accountName) {
    String[] parts = accountName.split(":");
    return parts[parts.length - 1];
  }
}
