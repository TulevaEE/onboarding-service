package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
class AnalyticsQueryPerformanceMigrationTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private DataSource dataSource;
  @Autowired private TransactionTemplate transactionTemplate;

  @Test
  void paymentRateChangeCountsOnTheLatestSnapshotCanReadOnlyTheRateChangeIndex()
      throws SQLException {
    assumeTrue(isPostgres(), "Partial covering indexes exist only on PostgreSQL");

    var plan =
        planWithoutSequentialScans(
            """
            SELECT count(DISTINCT personal_id)
            FROM unit_owner
            WHERE snapshot_date = (SELECT max(snapshot_date) FROM unit_owner)
              AND p2_next_rate IS NOT NULL
              AND p2_rate IS DISTINCT FROM p2_next_rate
              AND p2_rate < p2_next_rate
              AND p2_choice IN ('TUK00', 'TUK75')
            """);

    assertThat(plan).anyMatch(line -> line.contains("idx_unit_owner_p2_rate_change"));
  }

  private List<String> planWithoutSequentialScans(String query) {
    return transactionTemplate.execute(
        status -> {
          jdbcClient.sql("SET LOCAL enable_seqscan = off").update();
          return jdbcClient.sql("EXPLAIN " + query).query(String.class).list();
        });
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
    }
  }
}
