package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DepotFeeTierRepository {

  private final JdbcClient jdbcClient;

  public BigDecimal findRateForAum(BigDecimal totalAum, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT annual_rate
            FROM investment_depot_fee_tier
            WHERE min_aum <= :totalAum
              AND valid_from <= :date
              AND (valid_to IS NULL OR valid_to >= :date)
            ORDER BY min_aum DESC
            LIMIT 1
            """)
        .param("totalAum", totalAum)
        .param("date", date)
        .query(BigDecimal.class)
        .optional()
        .orElse(new BigDecimal("0.00035"));
  }
}
