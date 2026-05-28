package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavReportPositionLookup {

  private final JdbcClient jdbcClient;

  Map<String, BigDecimal> findSecurityQuantities(TulevaFund fund, LocalDate navDate) {
    var rows =
        jdbcClient
            .sql(
                """
                SELECT account_id, quantity
                FROM nav_report
                WHERE fund_code = :fundCode
                  AND nav_date = :navDate
                  AND account_type = 'SECURITY'
                  AND account_id IS NOT NULL
                  AND quantity IS NOT NULL
                  AND calculation_id = (
                    SELECT calculation_id FROM nav_report
                    WHERE fund_code = :fundCode AND nav_date = :navDate
                      AND published_at IS NOT NULL
                    ORDER BY id DESC LIMIT 1)
                """)
            .param("fundCode", fund.getCode())
            .param("navDate", navDate)
            .query(
                (rs, rowNum) -> Map.entry(rs.getString("account_id"), rs.getBigDecimal("quantity")))
            .list();

    Map<String, BigDecimal> result = new HashMap<>();
    for (var entry : rows) {
      result.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
    }
    return result;
  }
}
