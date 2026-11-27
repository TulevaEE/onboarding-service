package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.BANK_FEE;
import static ee.tuleva.onboarding.ledger.SystemAccount.INTEREST_INCOME;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BankChargesRepository {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final JdbcClient jdbcClient;

  public BankCharges chargedBetween(
      TulevaFund fund, LocalDate fromInclusive, LocalDate toInclusive) {
    return jdbcClient
        .sql(
            """
            SELECT
              COALESCE(SUM(CASE WHEN a.name = :bankFeeAccount THEN e.amount END), 0) AS bank_fees,
              COALESCE(SUM(CASE WHEN a.name = :interestAccount
                                 AND t.transaction_type = 'INTEREST_RECEIVED'
                                 AND e.amount > 0
                                THEN e.amount END), 0) AS overdraft_interest
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            JOIN ledger.transaction t ON e.transaction_id = t.id
            WHERE a.name IN (:bankFeeAccount, :interestAccount)
              AND a.purpose = 'SYSTEM_ACCOUNT'
              AND t.transaction_date >= :fromInclusive
              AND t.transaction_date < :toExclusive
            """)
        .param("bankFeeAccount", BANK_FEE.getAccountName(fund))
        .param("interestAccount", INTEREST_INCOME.getAccountName(fund))
        .param("fromInclusive", startOf(fromInclusive))
        .param("toExclusive", startOf(toInclusive.plusDays(1)))
        .query(
            (rs, rowNum) ->
                new BankCharges(
                    rs.getBigDecimal("bank_fees"), rs.getBigDecimal("overdraft_interest")))
        .single();
  }

  public Optional<LocalDate> statementsInLedgerFrom(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT t.transaction_date
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            JOIN ledger.transaction t ON e.transaction_id = t.id
            WHERE a.name IN (:bankFeeAccount, :interestAccount)
              AND a.purpose = 'SYSTEM_ACCOUNT'
            ORDER BY t.transaction_date
            LIMIT 1
            """)
        .param("bankFeeAccount", BANK_FEE.getAccountName(fund))
        .param("interestAccount", INTEREST_INCOME.getAccountName(fund))
        .query((rs, rowNum) -> rs.getTimestamp("transaction_date"))
        .optional()
        .map(firstLine -> firstLine.toInstant().atZone(ESTONIAN_ZONE).toLocalDate())
        .map(firstLineDay -> firstLineDay.withDayOfMonth(1));
  }

  private static Timestamp startOf(LocalDate day) {
    return Timestamp.from(day.atStartOfDay(ESTONIAN_ZONE).toInstant());
  }
}
