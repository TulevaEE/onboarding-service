package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeeNavInclusionPolicy {

  private final JdbcClient jdbcClient;

  public boolean includeInNav(TulevaFund fund, FeeType feeType, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT include_in_nav
            FROM investment_fee_policy
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
        .query(Boolean.class)
        .optional()
        .orElse(true);
  }
}
