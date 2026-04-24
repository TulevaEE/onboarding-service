package ee.tuleva.onboarding.investment.config;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvestmentParameterRepository {

  private final JdbcClient jdbcClient;

  public BigDecimal findLatestValue(InvestmentParameter parameter, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT numeric_value
            FROM investment_parameter
            WHERE parameter_name = :name
              AND fund_code IS NULL
              AND effective_date <= :asOf
            ORDER BY effective_date DESC
            LIMIT 1
            """)
        .param("name", parameter.name())
        .param("asOf", asOf)
        .query(BigDecimal.class)
        .optional()
        .orElseThrow(() -> missing(parameter, null, asOf));
  }

  public BigDecimal findLatestValue(
      InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT numeric_value
            FROM investment_parameter
            WHERE parameter_name = :name
              AND fund_code = :fundCode
              AND effective_date <= :asOf
            ORDER BY effective_date DESC
            LIMIT 1
            """)
        .param("name", parameter.name())
        .param("fundCode", fund.name())
        .param("asOf", asOf)
        .query(BigDecimal.class)
        .optional()
        .orElseThrow(() -> missing(parameter, fund, asOf));
  }

  private static IllegalStateException missing(
      InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return new IllegalStateException(
        "No investment parameter found: parameter="
            + parameter
            + ", fund="
            + (fund == null ? "GLOBAL" : fund.name())
            + ", asOf="
            + asOf);
  }
}
