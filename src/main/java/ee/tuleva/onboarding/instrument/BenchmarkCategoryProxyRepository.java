package ee.tuleva.onboarding.instrument;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BenchmarkCategoryProxyRepository {

  private final JdbcClient jdbcClient;

  List<BenchmarkCategoryProxy> findAll() {
    return jdbcClient
        .sql(
            "SELECT id, benchmark_category, etf_proxy_isin, index_proxy_isin, index_series_key FROM benchmark_category_proxy")
        .query(
            (rs, rowNum) ->
                new BenchmarkCategoryProxy(
                    rs.getLong("id"),
                    rs.getString("benchmark_category"),
                    rs.getString("etf_proxy_isin"),
                    rs.getString("index_proxy_isin"),
                    rs.getString("index_series_key")))
        .list();
  }
}
