package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavReportPositionProvider {

  private final JdbcClient jdbcClient;

  Map<String, BigDecimal> getSecurityMarketValues(TulevaFund fund, LocalDate navDate) {
    return jdbcClient
        .sql(
            """
            SELECT account_id, market_value
            FROM nav_report
            WHERE fund_code = :fundCode
              AND nav_date = :navDate
              AND account_type = 'SECURITY'
              AND account_id IS NOT NULL
            """)
        .param("fundCode", fund.getCode())
        .param("navDate", navDate)
        .query(
            (rs, rowNum) -> Map.entry(rs.getString("account_id"), rs.getBigDecimal("market_value")))
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  Optional<BigDecimal> getCalculatedAum(TulevaFund fund, LocalDate navDate) {
    return jdbcClient
        .sql(
            """
            SELECT market_value
            FROM nav_report
            WHERE fund_code = :fundCode
              AND nav_date = :navDate
              AND account_type = 'UNITS'
            """)
        .param("fundCode", fund.getCode())
        .param("navDate", navDate)
        .query(BigDecimal.class)
        .optional();
  }
}
