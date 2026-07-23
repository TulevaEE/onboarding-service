package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Clock;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class LongRunningTransactionMonitorTest {

  private final LongRunningTransactionMonitor monitor =
      new LongRunningTransactionMonitor(h2DataSource(), Clock.systemUTC(), 300);

  @Test
  void doesNotDetectPostgresOnH2() {
    assertThat(monitor.isPostgres()).isFalse();
  }

  @Test
  void monitorIsNoOpOnH2() {
    assertThatNoException().isThrownBy(monitor::monitor);
  }

  @Test
  void sanitizeQueryMasksQuotedLiterals() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "SELECT id FROM users WHERE email = 'someone@example.com'"))
        .isEqualTo("SELECT id FROM users WHERE email = '?'");
  }

  @Test
  void sanitizeQueryMasksLongDigitRuns() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "UPDATE aml_check SET personal_code = 38888888888"))
        .isEqualTo("UPDATE aml_check SET personal_code = ?");
  }

  @Test
  void sanitizeQueryMasksTruncatedTrailingLiteral() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "SELECT id FROM users WHERE first_name = 'Mar"))
        .isEqualTo("SELECT id FROM users WHERE first_name = '?'");
  }

  @Test
  void sanitizeQueryKeepsStatementsWithoutLiteralsIntact() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata"))
        .isEqualTo("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata");
  }

  @Test
  void sanitizeQueryKeepsBindParameterPlaceholders() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "SELECT id FROM payment WHERE recipient_personal_code = $1 AND payment_type <> $2"))
        .isEqualTo(
            "SELECT id FROM payment WHERE recipient_personal_code = $1 AND payment_type <> $2");
  }

  @Test
  void sanitizeQueryRedactsDollarQuotedStatementsToTheVerb() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "SELECT id FROM users WHERE email = $$someone@example.com$$"))
        .isEqualTo("SELECT [redacted]");
  }

  @Test
  void sanitizeQueryRedactsEscapeStringStatementsToTheVerb() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "SELECT id FROM users WHERE last_name = E'O\\'Brien'"))
        .isEqualTo("SELECT [redacted]");
  }

  @Test
  void sanitizeQueryRedactsStatementsStartingWithACommentEntirely() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "/*email=someone@example.com*/ SELECT pg_sleep(400)"))
        .isEqualTo("[redacted]");
  }

  @Test
  void sanitizeQueryRedactsCommentedStatementsToTheVerb() {
    assertThat(
            LongRunningTransactionMonitor.sanitizeQuery(
                "UPDATE users SET email = 'x' -- note about someone@example.com"))
        .isEqualTo("UPDATE [redacted]");
  }

  private static JdbcDataSource h2DataSource() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:longRunningTransactionMonitorTest;DB_CLOSE_DELAY=-1");
    return dataSource;
  }
}
