package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeeRateRepository {

  private final JdbcClient jdbcClient;

  public Optional<FeeRate> findValidRate(TulevaFund fund, FeeType feeType, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT id, fund_code, fee_type, annual_rate, valid_from, valid_to
            FROM investment_fee_rate
            WHERE fund_code = :fundCode
              AND fee_type = :feeType
              AND valid_from <= :date
              AND (valid_to IS NULL OR valid_to >= :date)
            ORDER BY valid_from DESC
            LIMIT 1
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("date", date)
        .query(FeeRate::fromResultSet)
        .optional();
  }
}
