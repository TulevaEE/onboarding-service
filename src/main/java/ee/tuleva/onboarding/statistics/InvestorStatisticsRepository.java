package ee.tuleva.onboarding.statistics;

import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvestorStatisticsRepository {

  private final JdbcClient jdbcClient;

  public long getActiveInvestorCount() {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(total_active_investors), 0)
            FROM analytics.mv_kpi_new
            WHERE reporting_date = (SELECT MAX(reporting_date) FROM analytics.mv_kpi_new)
            """)
        .query(Long.class)
        .single();
  }

  public OptionalLong getPreviousActiveInvestorCount() {
    long count =
        jdbcClient
            .sql(
                """
                SELECT COALESCE(SUM(total_active_investors), 0)
                FROM analytics.mv_kpi_new
                WHERE reporting_date = (
                  SELECT MAX(reporting_date) FROM analytics.mv_kpi_new
                  WHERE reporting_date < (SELECT MAX(reporting_date) FROM analytics.mv_kpi_new)
                )
                """)
            .query(Long.class)
            .single();
    return count > 0 ? OptionalLong.of(count) : OptionalLong.empty();
  }
}
