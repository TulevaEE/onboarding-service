package ee.tuleva.onboarding.audit.health;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class AuditHealthServiceIntegrationTest {

  @Autowired private AuditHealthService auditHealthService;

  @Autowired private AuditHealthRepository auditHealthRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DataSource dataSource;

  private static final Instant TEST_NOW = Instant.parse("2025-06-01T10:00:00.00Z");
  private final Clock fixedClock = Clock.fixed(TEST_NOW, ZoneId.of("UTC"));

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
    ClockHolder.setClock(fixedClock);
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

  private Duration getMaxIntervalThresholdFromService() throws Exception {
    Field field = AuditHealthService.class.getDeclaredField("maxIntervalThreshold");
    field.setAccessible(true);
    return (Duration) field.get(auditHealthService);
  }

  private void truncateLoggedActionsTable() throws Exception {
    jdbcTemplate.execute("DELETE FROM audit.logged_actions;");
  }

  @Test
  @DisplayName("Integration: initializeOrRefreshThreshold sets positive threshold from DB data")
  @Transactional
  void integration_initialize_positiveThreshold() throws Exception {
    // given
    Instant baseTime = TEST_NOW.minus(Duration.ofDays(5));
    saveLogViaSql(baseTime, "I");
    saveLogViaSql(baseTime.plus(Duration.ofMinutes(30)), "U");
    saveLogViaSql(baseTime.plus(Duration.ofMinutes(30)).plus(Duration.ofHours(1)), "D");
    saveLogViaSql(
        baseTime
            .plus(Duration.ofMinutes(30))
            .plus(Duration.ofHours(1))
            .plus(Duration.ofMinutes(10)),
        "I");
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    assertThat(getMaxIntervalThresholdFromService()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  @DisplayName(
      "Integration: initializeOrRefreshThreshold sets ZERO for no positive intervals in DB")
  @Transactional
  void integration_initialize_zeroIntervalFromDB() throws Exception {
    // given
    Instant baseTime = TEST_NOW.minus(Duration.ofDays(3));
    saveLogViaSql(baseTime, "I");
    saveLogViaSql(baseTime, "U");
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    assertThat(getMaxIntervalThresholdFromService()).isEqualTo(Duration.ZERO);

    // given
    truncateLoggedActionsTable();
    saveLogViaSql(baseTime, "D");
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    assertThat(getMaxIntervalThresholdFromService()).isEqualTo(Duration.ZERO);
  }

  @Test
  @DisplayName("Integration: initializeOrRefreshThreshold sets ZERO when DB is empty")
  @Transactional
  void integration_initialize_emptyDB() throws Exception {
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    assertThat(getMaxIntervalThresholdFromService()).isEqualTo(Duration.ZERO);
  }

  @Test
  @DisplayName("Integration: isAuditLogDelayed works correctly after DB-driven positive threshold")
  @Transactional
  void integration_isDelayed_with_DB_PositiveThreshold() throws Exception {
    // given
    Instant baseTime = TEST_NOW.minus(Duration.ofDays(1));
    saveLogViaSql(baseTime, "I");
    saveLogViaSql(baseTime.plus(Duration.ofHours(1)), "U");
    auditHealthService.initializeOrRefreshThreshold();

    Instant lastEventTimeNotDelayed = TEST_NOW.minusSeconds(4320);
    saveLogViaSql(lastEventTimeNotDelayed, "LAST");
    // when
    boolean notDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(notDelayed).isFalse();

    truncateLoggedActionsTable();
    saveLogViaSql(baseTime, "I");
    saveLogViaSql(baseTime.plus(Duration.ofHours(1)), "U");
    auditHealthService.initializeOrRefreshThreshold();

    Instant lastEventTimeDelayed = TEST_NOW.minusSeconds(4321);
    saveLogViaSql(lastEventTimeDelayed, "LAST_DELAYED");
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  @DisplayName("Integration: isAuditLogDelayed works correctly after DB-driven ZERO threshold")
  @Transactional
  void integration_isDelayed_with_DB_ZeroThreshold() throws Exception {
    // given
    Instant lastEventTime = TEST_NOW.minus(Duration.ofDays(1));
    saveLogViaSql(lastEventTime, "I");
    auditHealthService.initializeOrRefreshThreshold();

    // when
    boolean isDelayedWithExistingLog = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayedWithExistingLog).isFalse();

    // given
    truncateLoggedActionsTable();
    auditHealthService.initializeOrRefreshThreshold();
    // when
    boolean isDelayedWithNoLogs = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayedWithNoLogs).isFalse();
  }
}
