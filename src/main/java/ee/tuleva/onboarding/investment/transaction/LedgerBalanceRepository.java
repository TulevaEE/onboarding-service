package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LedgerBalanceRepository {

  private final JdbcClient jdbcClient;
  private final FundValueRepository fundValueRepository;

  BigDecimal getIncomingPaymentsClearing() {
    return getAccountBalance("INCOMING_PAYMENTS_CLEARING", "SYSTEM_ACCOUNT");
  }

  BigDecimal getUnreconciledBankReceipts() {
    return getAccountBalance("UNRECONCILED_BANK_RECEIPTS", "SYSTEM_ACCOUNT");
  }

  BigDecimal getFundUnitsReservedValue() {
    BigDecimal units = getFundUnitsBalance("FUND_UNITS_RESERVED", "USER_ACCOUNT");
    if (units.signum() == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal nav =
        fundValueRepository
            .findLastValueForFund(TKF100.getIsin())
            .map(FundValue::value)
            .orElse(BigDecimal.ZERO);
    return units.multiply(nav);
  }

  private BigDecimal getAccountBalance(String accountName, String purpose) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
              AND a.purpose = :purpose
            """)
        .param("accountName", accountName)
        .param("purpose", purpose)
        .query(BigDecimal.class)
        .single();
  }

  private BigDecimal getFundUnitsBalance(String accountName, String purpose) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
              AND a.purpose = :purpose
              AND a.asset_type = 'FUND_UNIT'
            """)
        .param("accountName", accountName)
        .param("purpose", purpose)
        .query(BigDecimal.class)
        .single();
  }
}
