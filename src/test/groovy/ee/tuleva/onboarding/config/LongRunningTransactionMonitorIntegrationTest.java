package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=5")
class LongRunningTransactionMonitorIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired Clock clock;

  @Test
  void detectsOpenTransactionExceedingThreshold() throws SQLException {
    var monitor = new LongRunningTransactionMonitor(dataSource, clock, 0);
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

  @Test
  void reportsEachTransactionOnlyOnce() throws SQLException {
    var monitor = new LongRunningTransactionMonitor(dataSource, clock, 0);
    assumeTrue(monitor.isPostgres(), "pg_stat_activity only exists on PostgreSQL");

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("SELECT pg_sleep(0.1)");

        var firstCheck = monitor.findNewLongRunningTransactions();
        var secondCheck = monitor.findNewLongRunningTransactions();

        assertThat(firstCheck)
            .anySatisfy(transaction -> assertThat(transaction.query()).contains("pg_sleep"));
        assertThat(secondCheck)
            .noneSatisfy(transaction -> assertThat(transaction.query()).contains("pg_sleep"));
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void keepsRecentlyCreatedAlertsForTransactionsOlderThanRetention() {
    var monitor = new LongRunningTransactionMonitor(dataSource, clock, 0);
    assumeTrue(monitor.isPostgres(), "pg_stat_activity only exists on PostgreSQL");

    var jdbcClient = JdbcClient.create(dataSource);
    jdbcClient
        .sql(
            """
            INSERT INTO long_running_transaction_alert (pid, xact_start, query, created_time)
            VALUES (:pid, :xactStart, 'SELECT 1', :createdTime)
            """)
        .param("pid", 999998)
        .param("xactStart", OffsetDateTime.now(clock).minusDays(30))
        .param("createdTime", OffsetDateTime.now(clock))
        .update();

    monitor.monitor();

    var remaining =
        jdbcClient
            .sql("SELECT count(*) FROM long_running_transaction_alert WHERE pid = 999998")
            .query(Long.class)
            .single();
    assertThat(remaining).isEqualTo(1);
  }

  @Test
  void deletesAlertRowsPastRetention() {
    var monitor = new LongRunningTransactionMonitor(dataSource, clock, 0);
    assumeTrue(monitor.isPostgres(), "pg_stat_activity only exists on PostgreSQL");

    var jdbcClient = JdbcClient.create(dataSource);
    jdbcClient
        .sql(
            """
            INSERT INTO long_running_transaction_alert (pid, xact_start, query, created_time)
            VALUES (:pid, :xactStart, 'SELECT 1', :createdTime)
            """)
        .param("pid", 999999)
        .param("xactStart", OffsetDateTime.now(clock).minusDays(8))
        .param("createdTime", OffsetDateTime.now(clock).minusDays(8))
        .update();

    monitor.monitor();

    var remaining =
        jdbcClient
            .sql("SELECT count(*) FROM long_running_transaction_alert WHERE pid = 999999")
            .query(Long.class)
            .single();
    assertThat(remaining).isZero();
  }
}
