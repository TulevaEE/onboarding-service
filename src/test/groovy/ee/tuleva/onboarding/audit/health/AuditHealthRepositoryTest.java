package ee.tuleva.onboarding.audit.health;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(AuditHealthRepository.class)
class AuditHealthRepositoryTest {

  @Autowired private AuditHealthRepository repository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DataSource dataSource;

  private static final Instant NOW_FOR_TESTS = Instant.parse("2025-05-27T10:00:00.00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW_FOR_TESTS, ZoneId.of("UTC"));

  private static final String CREATE_AUDIT_SCHEMA_IF_NOT_EXISTS =
      "CREATE SCHEMA IF NOT EXISTS audit";

  private static final String CREATE_LOGGED_ACTIONS_TABLE =
      """
            CREATE TABLE IF NOT EXISTS audit.logged_actions (
                event_id BIGSERIAL PRIMARY KEY,
                schema_name TEXT NOT NULL DEFAULT 'test_schema',
                table_name TEXT NOT NULL DEFAULT 'test_table',
                relid BIGINT NOT NULL DEFAULT 0,
                session_user_name TEXT,
                action_tstamp_tx TIMESTAMP NOT NULL,
                action_tstamp_stm TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                action_tstamp_clk TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                transaction_id BIGINT,
                application_name TEXT,
                client_addr VARCHAR(255),
                client_port INTEGER,
                client_query TEXT,
                action TEXT NOT NULL,
                row_data JSONB,
                changed_fields JSONB,
                statement_only BOOLEAN NOT NULL DEFAULT FALSE
            );
            """;

  @BeforeAll
  static void setupDatabase(@Autowired DataSource ds) throws Exception {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_AUDIT_SCHEMA_IF_NOT_EXISTS);
      stmt.execute(CREATE_LOGGED_ACTIONS_TABLE);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    ClockHolder.setClock(FIXED_CLOCK);
    try (Connection conn = this.dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_AUDIT_SCHEMA_IF_NOT_EXISTS);
      stmt.execute(CREATE_LOGGED_ACTIONS_TABLE);
    }

    jdbcTemplate.execute("DELETE FROM audit.logged_actions;");
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  private void saveLogViaSql(Instant time, String actionType) {
    String sql =
        """
        INSERT INTO audit.logged_actions
        (action_tstamp_tx, action, schema_name, table_name, relid, session_user_name, action_tstamp_stm, action_tstamp_clk, transaction_id, application_name, client_addr, client_port, client_query, statement_only)
        VALUES (?, ?, 'test_schema', 'test_table', 0, 'test_user', ?, ?, NULL, 'test_app', NULL, NULL, 'test_query', FALSE)
        """;
    jdbcTemplate.update(
        sql, Timestamp.from(time), actionType, Timestamp.from(time), Timestamp.from(time));
  }

  @Test
  @DisplayName("findLastAuditEventTimestamp returns empty if table is empty")
  void findLastAuditEventTimestamp_emptyTable() {
    // when
    Optional<Instant> lastTimestamp = repository.findLastAuditEventTimestamp();
    // then
    assertThat(lastTimestamp).isEmpty();
  }

  @Test
  @DisplayName("findLastAuditEventTimestamp returns the latest timestamp (via actual H2 query)")
  void findLastAuditEventTimestamp_returnsLatest() {
    // given
    Instant time1 = NOW_FOR_TESTS.minus(Duration.ofHours(2));
    Instant time2Latest = NOW_FOR_TESTS.minus(Duration.ofHours(1));
    saveLogViaSql(time1, "I");
    saveLogViaSql(time2Latest, "U");
    // when
    Optional<Instant> lastTimestamp = repository.findLastAuditEventTimestamp();
    // then
    assertThat(lastTimestamp).isPresent();
    assertThat(lastTimestamp.get().truncatedTo(ChronoUnit.SECONDS))
        .isEqualTo(time2Latest.truncatedTo(ChronoUnit.SECONDS));
  }

  @Test
  @DisplayName(
      "findLastAuditEventTimestamp returns empty and logs warning on query execution exception")
  void findLastAuditEventTimestamp_handlesQueryExecutionException() {
    // given
    jdbcTemplate.execute("DROP TABLE audit.logged_actions");

    // when
    Optional<Instant> result = repository.findLastAuditEventTimestamp();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findLongestIntervalSecondsSince returns 0.0 when table is empty")
  void findLongestIntervalSecondsSince_returnsZeroForEmptyTable() {
    // when
    AuditLogInterval result =
        repository.findLongestIntervalSecondsSince(NOW_FOR_TESTS.minus(Duration.ofDays(2)));
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("findLongestIntervalSecondsSince returns 0.0 when only one entry exists")
  void findLongestIntervalSecondsSince_returnsZeroForSingleEntry() {
    // given
    saveLogViaSql(NOW_FOR_TESTS.minus(Duration.ofHours(1)), "I");
    // when
    AuditLogInterval result =
        repository.findLongestIntervalSecondsSince(NOW_FOR_TESTS.minus(Duration.ofDays(2)));
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(0.0);
  }

  @Test
  @DisplayName(
      "findLongestIntervalSecondsSince returns 0.0 when entries have zero-duration intervals")
  void findLongestIntervalSecondsSince_returnsZeroForZeroDurationIntervals() {
    // given
    saveLogViaSql(NOW_FOR_TESTS.minus(Duration.ofHours(2)), "D");
    saveLogViaSql(NOW_FOR_TESTS.minus(Duration.ofHours(2)), "I");
    // when
    AuditLogInterval result =
        repository.findLongestIntervalSecondsSince(NOW_FOR_TESTS.minus(Duration.ofDays(2)));
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("findLongestIntervalSecondsSince returns correct max interval")
  void findLongestIntervalSecondsSince_correctInterval() {
    // given
    Instant twoDaysAgo = NOW_FOR_TESTS.minus(Duration.ofDays(2));
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(1)), "I");
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(2)), "U");
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(4)), "D");
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(5)), "I");
    // when
    AuditLogInterval result = repository.findLongestIntervalSecondsSince(twoDaysAgo);
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(Duration.ofHours(2).getSeconds());
  }

  @Test
  @DisplayName("findLongestIntervalSecondsSince ignores data older than 'sinceTime'")
  void findLongestIntervalSecondsSince_ignoresOldData() {
    // given
    Instant threeDaysAgo = NOW_FOR_TESTS.minus(Duration.ofDays(3));
    Instant twoDaysAgo = NOW_FOR_TESTS.minus(Duration.ofDays(2));
    saveLogViaSql(threeDaysAgo.plus(Duration.ofHours(1)), "I");
    saveLogViaSql(threeDaysAgo.plus(Duration.ofHours(3)), "U");
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(10)), "D");
    saveLogViaSql(twoDaysAgo.plus(Duration.ofHours(11)), "I");
    // when
    AuditLogInterval result = repository.findLongestIntervalSecondsSince(twoDaysAgo);
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(Duration.ofHours(1).getSeconds());
  }

  @Test
  @DisplayName("findLongestIntervalSecondsSince handles boundary of sinceTime correctly")
  void findLongestIntervalSecondsSince_boundaryCondition() {
    // given
    Instant since = NOW_FOR_TESTS.minus(Duration.ofDays(2));
    saveLogViaSql(since, "I");
    saveLogViaSql(since.plus(Duration.ofMinutes(30)), "U");
    // when
    AuditLogInterval result = repository.findLongestIntervalSecondsSince(since);
    // then
    assertThat(result).isNotNull();
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(Duration.ofMinutes(30).getSeconds());
  }
}
