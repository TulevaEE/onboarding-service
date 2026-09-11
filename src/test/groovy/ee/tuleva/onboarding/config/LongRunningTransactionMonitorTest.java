package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ee.tuleva.onboarding.config.LongRunningTransactionMonitor.LongRunningTransaction;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

class LongRunningTransactionMonitorTest {

  private final LongRunningTransactionMonitor monitor =
      new LongRunningTransactionMonitor(h2DataSourceWithAlertTable(), Clock.systemUTC(), 300);

  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(LongRunningTransactionMonitor.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logAppender);
  }

  @Test
  void doesNotDetectPostgresOnH2() {
    assertThat(monitor.isPostgres()).isFalse();
  }

  @Test
  void detectsPostgresFromTheConnectionMetadata() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    given(dataSource.getConnection()).willReturn(connection);
    given(connection.getMetaData()).willReturn(metaData);
    given(metaData.getDatabaseProductName()).willReturn("PostgreSQL");
    var pgMonitor = new LongRunningTransactionMonitor(dataSource, Clock.systemUTC(), 300);

    assertThat(pgMonitor.isPostgres()).isTrue();
  }

  @Test
  void isPostgresReturnsFalseWhenTheConnectionCannotBeInspected() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    given(dataSource.getConnection()).willThrow(new SQLException("connection unavailable"));
    var brokenMonitor = new LongRunningTransactionMonitor(dataSource, Clock.systemUTC(), 300);

    assertThat(brokenMonitor.isPostgres()).isFalse();
  }

  @Test
  void monitorIsNoOpOnH2() {
    assertThatNoException().isThrownBy(monitor::monitor);
  }

  @Test
  void monitorSkipsQueryingWhenNotPostgres() {
    LongRunningTransactionMonitor spy = Mockito.spy(monitor);
    willReturn(false).given(spy).isPostgres();

    spy.monitor();

    verify(spy, never()).findNewLongRunningTransactions();
  }

  @Test
  void monitorQueriesForNewTransactionsWhenPostgres() {
    LongRunningTransactionMonitor spy = Mockito.spy(monitor);
    willReturn(true).given(spy).isPostgres();
    willReturn(List.of()).given(spy).findNewLongRunningTransactions();

    spy.monitor();

    verify(spy).findNewLongRunningTransactions();
  }

  @Test
  void monitorLogsAnErrorForEachNewLongRunningTransaction() {
    LongRunningTransactionMonitor spy = Mockito.spy(monitor);
    willReturn(true).given(spy).isPostgres();
    var transaction =
        new LongRunningTransaction(
            123, OffsetDateTime.now(), "onboarding-service", "active", 400, "SELECT 1");
    willReturn(List.of(transaction)).given(spy).findNewLongRunningTransactions();

    spy.monitor();

    assertThat(logAppender.list).hasSize(1);
    assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void monitorLogsNothingWhenThereAreNoNewLongRunningTransactions() {
    LongRunningTransactionMonitor spy = Mockito.spy(monitor);
    willReturn(true).given(spy).isPostgres();
    willReturn(List.of()).given(spy).findNewLongRunningTransactions();

    spy.monitor();

    assertThat(logAppender.list).isEmpty();
  }

  @Test
  void monitorDeletesAlertsPastRetentionWhenPostgres() {
    DataSource dataSource = h2DataSourceWithAlertTable();
    var jdbcClient = JdbcClient.create(dataSource);
    var now = OffsetDateTime.now();
    insertAlert(jdbcClient, 1, now.minusDays(8));
    insertAlert(jdbcClient, 2, now.minusDays(1));
    LongRunningTransactionMonitor spy =
        Mockito.spy(new LongRunningTransactionMonitor(dataSource, Clock.systemUTC(), 300));
    willReturn(true).given(spy).isPostgres();
    willReturn(List.of()).given(spy).findNewLongRunningTransactions();

    spy.monitor();

    List<Integer> remainingPids =
        jdbcClient
            .sql("SELECT pid FROM long_running_transaction_alert")
            .query(Integer.class)
            .list();
    assertThat(remainingPids).containsExactly(2);
  }

  // isFirstReport()'s dedup INSERT uses `ON CONFLICT ... DO NOTHING`, which H2 does not support
  // even in MODE=PostgreSQL; that path (and findLongRunningTransactions()'s pg_stat_activity
  // query) is covered against real PostgreSQL by LongRunningTransactionMonitorIntegrationTest.

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

  @Test
  void groupingErrorCodeGivesEachStatementItsOwnSentryIssue() {
    assertThat(
            LongRunningTransactionMonitor.groupingErrorCode(
                "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata"))
        .isEqualTo(
            "long-running-transaction: REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata");
  }

  @Test
  void groupingErrorCodeStaysWithinTheSentryTagValueLimit() {
    assertThat(LongRunningTransactionMonitor.groupingErrorCode("x".repeat(300))).hasSize(200);
  }

  private static void insertAlert(JdbcClient jdbcClient, int pid, OffsetDateTime createdTime) {
    jdbcClient
        .sql(
            "INSERT INTO long_running_transaction_alert (pid, xact_start, query, created_time) "
                + "VALUES (:pid, :xactStart, :query, :createdTime)")
        .param("pid", pid)
        .param("xactStart", createdTime)
        .param("query", "SELECT 1")
        .param("createdTime", createdTime)
        .update();
  }

  private static JdbcDataSource h2DataSource() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:longRunningTransactionMonitorTest"
            + System.nanoTime()
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    return dataSource;
  }

  private static JdbcDataSource h2DataSourceWithAlertTable() {
    JdbcDataSource dataSource = h2DataSource();
    JdbcClient.create(dataSource)
        .sql(
            """
            CREATE TABLE long_running_transaction_alert (
              pid integer NOT NULL,
              xact_start timestamp with time zone NOT NULL,
              query text NOT NULL,
              created_time timestamp with time zone NOT NULL,
              CONSTRAINT pk_long_running_transaction_alert PRIMARY KEY (pid, xact_start)
            )
            """)
        .update();
    return dataSource;
  }
}
