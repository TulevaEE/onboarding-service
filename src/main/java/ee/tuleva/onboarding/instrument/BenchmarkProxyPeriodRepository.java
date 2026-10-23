package ee.tuleva.onboarding.instrument;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BenchmarkProxyPeriodRepository {

  private final JdbcClient jdbcClient;

  List<BenchmarkProxyPeriod> findAllOrderedByValidUntil() {
    return jdbcClient
        .sql(
            "SELECT holding_isin, proxy_isin, valid_until FROM benchmark_proxy_period"
                + " ORDER BY holding_isin, valid_until")
        .query(
            (rs, rowNum) ->
                new BenchmarkProxyPeriod(
                    rs.getString("holding_isin"),
                    rs.getString("proxy_isin"),
                    rs.getObject("valid_until", LocalDate.class)))
        .list();
  }
}
