package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=5")
class LongRunningTransactionMonitorIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void detectsOpenTransactionExceedingThreshold() throws SQLException {
    var monitor = new LongRunningTransactionMonitor(dataSource, 0);
    assumeTrue(monitor.isPostgres(), "pg_stat_activity only exists on PostgreSQL");

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("SELECT pg_sleep(0.1)");

        var transactions = monitor.findLongRunningTransactions();

        assertThat(transactions)
            .anySatisfy(
                transaction -> {
                  assertThat(transaction.query()).contains("pg_sleep");
                  assertThat(transaction.durationSeconds()).isGreaterThanOrEqualTo(0);
                });
      } finally {
        connection.rollback();
      }
    }
  }
}
