package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavLookup {

  private final JdbcClient jdbcClient;

  Optional<BigDecimal> findMarketPrice(String isin, LocalDate navDate) {
    return jdbcClient
        .sql(
            """
            SELECT DISTINCT market_price
            FROM nav_report
            WHERE account_type = 'SECURITY'
              AND account_id = :isin
              AND nav_date = :navDate
              AND market_price IS NOT NULL
            """)
        .param("isin", isin)
        .param("navDate", navDate)
        .query(BigDecimal.class)
        .optional();
  }
}
