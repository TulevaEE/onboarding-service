package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@NullMarked
@Repository
@RequiredArgsConstructor
class HistoricalTransactionFeeRepository {

  private final JdbcClient jdbcClient;

  BigDecimal sumForFundBetween(TulevaFund fund, LocalDate fromInclusive, LocalDate toInclusive) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM investment_historical_transaction_fee
            WHERE fund_code = :fundCode
              AND value_date >= :fromInclusive
              AND value_date <= :toInclusive
            """)
        .param("fundCode", fund.getCode())
        .param("fromInclusive", fromInclusive)
        .param("toInclusive", toInclusive)
        .query(BigDecimal.class)
        .single();
  }
}
