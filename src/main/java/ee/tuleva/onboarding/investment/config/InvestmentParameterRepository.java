package ee.tuleva.onboarding.investment.config;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvestmentParameterRepository {

  private final JdbcClient jdbcClient;

  public BigDecimal findLatestValue(InvestmentParameter parameter, LocalDate asOf) {
    return findLatestValueIfPresent(parameter, asOf)
        .orElseThrow(() -> missing(parameter, "fund=GLOBAL", asOf));
  }

  public Optional<BigDecimal> findLatestValueIfPresent(
      InvestmentParameter parameter, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT numeric_value
            FROM investment_parameter
            WHERE parameter_name = :name
              AND fund_code IS NULL
              AND isin IS NULL
              AND effective_date <= :asOf
            ORDER BY effective_date DESC, id DESC
            LIMIT 1
            """)
        .param("name", parameter.name())
        .param("asOf", asOf)
        .query(BigDecimal.class)
        .optional();
  }

  public BigDecimal findLatestValue(
      InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return findLatestValueIfPresent(parameter, fund, asOf)
        .orElseThrow(() -> missing(parameter, "fund=" + fund.name(), asOf));
  }

  public Optional<BigDecimal> findLatestValueIfPresent(
      InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT numeric_value
            FROM investment_parameter
            WHERE parameter_name = :name
              AND fund_code = :fundCode
              AND isin IS NULL
              AND effective_date <= :asOf
            ORDER BY effective_date DESC, id DESC
            LIMIT 1
            """)
        .param("name", parameter.name())
        .param("fundCode", fund.name())
        .param("asOf", asOf)
        .query(BigDecimal.class)
        .optional();
  }

  public BigDecimal findLatestValueForIsinOrGlobal(
      InvestmentParameter parameter, String isin, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT numeric_value
            FROM investment_parameter
            WHERE parameter_name = :name
              AND fund_code IS NULL
              AND (isin = :isin OR isin IS NULL)
              AND effective_date <= :asOf
            ORDER BY CASE WHEN isin IS NULL THEN 1 ELSE 0 END, effective_date DESC, id DESC
            LIMIT 1
            """)
        .param("name", parameter.name())
        .param("isin", isin)
        .param("asOf", asOf)
        .query(BigDecimal.class)
        .optional()
        .orElseThrow(() -> missing(parameter, "fund=GLOBAL, isin=" + isin, asOf));
  }

  private static IllegalStateException missing(
      InvestmentParameter parameter, String scope, LocalDate asOf) {
    return new IllegalStateException(
        "No investment parameter found: parameter=" + parameter + ", " + scope + ", asOf=" + asOf);
  }
}
