package ee.tuleva.onboarding.config;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
public class LongRunningTransactionMonitor {

  private final DataSource dataSource;
  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final Duration threshold;

  private @Nullable Boolean postgres;

  public LongRunningTransactionMonitor(
      DataSource dataSource,
      Clock clock,
      @Value("${monitoring.long-running-transaction.threshold-seconds:300}")
          long thresholdSeconds) {
    this.dataSource = dataSource;
    this.jdbcClient = JdbcClient.create(dataSource);
    this.clock = clock;
    this.threshold = Duration.ofSeconds(thresholdSeconds);
  }

  record LongRunningTransaction(
      int pid,
      OffsetDateTime xactStart,
      String applicationName,
      String state,
      long durationSeconds,
      String query) {}

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "LongRunningTransactionMonitor",
      lockAtMostFor = "2m",
      lockAtLeastFor = "30s")
  public void monitor() {
    if (!isPostgres()) {
      return;
    }
    try {
      findNewLongRunningTransactions().forEach(this::report);
      deleteAlertsPastRetention();
    } catch (DataAccessException e) {
      log.warn("Long-running transaction check failed", e);
    }
  }

  List<LongRunningTransaction> findNewLongRunningTransactions() {
    return findLongRunningTransactions().stream().filter(this::isFirstReport).toList();
  }

  private boolean isFirstReport(LongRunningTransaction transaction) {
    int inserted =
        jdbcClient
            .sql(
                """
                INSERT INTO long_running_transaction_alert (pid, xact_start, query, created_time)
                VALUES (:pid, :xactStart, :query, :createdTime)
                ON CONFLICT (pid, xact_start) DO NOTHING
                """)
            .param("pid", transaction.pid())
            .param("xactStart", transaction.xactStart())
            .param("query", transaction.query())
            .param("createdTime", OffsetDateTime.now(clock))
            .update();
    return inserted == 1;
  }

  private void deleteAlertsPastRetention() {
    jdbcClient
        .sql("DELETE FROM long_running_transaction_alert WHERE created_time < :cutoff")
        .param("cutoff", OffsetDateTime.now(clock).minusDays(7))
        .update();
  }

  private void report(LongRunningTransaction transaction) {
    log.error(
        "Long-running database transaction: pid={}, applicationName={}, state={}, durationSeconds={}, query={}",
        transaction.pid(),
        transaction.applicationName(),
        transaction.state(),
        transaction.durationSeconds(),
        transaction.query());
  }

  List<LongRunningTransaction> findLongRunningTransactions() {
    return jdbcClient
        .sql(
            """
            SELECT pid, xact_start, application_name, state,
                   EXTRACT(EPOCH FROM (now() - xact_start))::bigint AS duration_seconds,
                   LEFT(query, 200) AS query
            FROM pg_stat_activity
            WHERE usename = current_user
              AND backend_type = 'client backend'
              AND pid <> pg_backend_pid()
              AND xact_start IS NOT NULL
              AND now() - xact_start > make_interval(secs => :thresholdSeconds)
            ORDER BY xact_start
            """)
        .param("thresholdSeconds", threshold.toSeconds())
        .query(LongRunningTransaction.class)
        .list()
        .stream()
        .map(LongRunningTransactionMonitor::sanitized)
        .toList();
  }

  private static LongRunningTransaction sanitized(LongRunningTransaction transaction) {
    return new LongRunningTransaction(
        transaction.pid(),
        transaction.xactStart(),
        transaction.applicationName(),
        transaction.state(),
        transaction.durationSeconds(),
        sanitizeQuery(transaction.query()));
  }

  static String sanitizeQuery(String query) {
    if (containsUnmaskableSyntax(query)) {
      return redactedToVerb(query);
    }
    return query
        .replaceAll("'[^']*'", "'?'")
        .replaceAll("'[^']+$", "'?'")
        .replaceAll("\\d{4,}", "?");
  }

  private static boolean containsUnmaskableSyntax(String query) {
    return query.contains("\\")
        || query.contains("--")
        || query.contains("/*")
        || query.replaceAll("\\$\\d+", "").contains("$");
  }

  private static String redactedToVerb(String query) {
    String verb = query.trim().split("\\s", 2)[0];
    if (!verb.matches("[A-Za-z]+")) {
      return "[redacted]";
    }
    return verb + " [redacted]";
  }

  boolean isPostgres() {
    if (postgres == null) {
      try (var connection = dataSource.getConnection()) {
        postgres =
            connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
      } catch (SQLException e) {
        log.warn("Could not detect database product for transaction monitoring", e);
        return false;
      }
    }
    return postgres;
  }
}
