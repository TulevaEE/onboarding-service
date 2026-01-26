package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AumRepository {

  private final JdbcClient jdbcClient;

  public Optional<BigDecimal> getAum(TulevaFund fund, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT value FROM index_values
            WHERE key = :key AND date <= :date
            ORDER BY date DESC
            LIMIT 1
            """)
        .param("key", fund.getAumKey())
        .param("date", date)
        .query(BigDecimal.class)
        .optional();
  }

  public BigDecimal getTotalAum(LocalDate date) {
    return Arrays.stream(TulevaFund.values())
        .map(fund -> getAum(fund, date).orElse(ZERO))
        .reduce(ZERO, BigDecimal::add);
  }

  public LocalDate getLastAumDateInMonth(LocalDate monthStart) {
    LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

    return jdbcClient
        .sql(
            """
            SELECT MAX(date) FROM index_values
            WHERE key LIKE 'AUM_%'
              AND date >= :monthStart
              AND date <= :monthEnd
            """)
        .param("monthStart", monthStart)
        .param("monthEnd", monthEnd)
        .query(LocalDate.class)
        .optional()
        .orElse(null);
  }

  public LocalDate getAumReferenceDate(TulevaFund fund, LocalDate calendarDate) {
    return jdbcClient
        .sql(
            """
            SELECT date FROM index_values
            WHERE key = :key AND date <= :date
            ORDER BY date DESC
            LIMIT 1
            """)
        .param("key", fund.getAumKey())
        .param("date", calendarDate)
        .query(LocalDate.class)
        .optional()
        .orElse(null);
  }

  public BigDecimal getHistoricalMaxTotalAum(LocalDate upToDate) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(MAX(daily_total), 0) FROM (
                SELECT date, SUM(value) AS daily_total
                FROM index_values
                WHERE key IN (:keys) AND date <= :upToDate
                GROUP BY date
            ) daily_totals
            """)
        .param("keys", Arrays.stream(TulevaFund.values()).map(TulevaFund::getAumKey).toList())
        .param("upToDate", upToDate)
        .query(BigDecimal.class)
        .single();
  }
}
