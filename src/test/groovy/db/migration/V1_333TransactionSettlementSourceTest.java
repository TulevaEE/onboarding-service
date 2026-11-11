package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_333TransactionSettlementSourceTest {

  private static final String URL =
      "jdbc:h2:mem:settlementsourcemigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_SOURCE_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.332?";
  private static final String THE_SOURCE = "1.333";

  private static final String HISTORICAL_IMPORT_BATCH = "historical-import";
  private static final String REGISTRY_BATCH = "test";
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 2, 16);

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheSource() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_SOURCE_EVEN_IF_NO_MIGRATION_HOLDS_IT);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void anImportedOrderTheImportAloneSettled_isLabelledAHistoricalImport() {
    long order = settledOrderIn(HISTORICAL_IMPORT_BATCH);

    migrateTo(THE_SOURCE);

    assertThat(sourceOf(order)).isEqualTo("HISTORICAL_IMPORT");
  }

  @Test
  void anImportedOrderTheAbsenceSettled_keepsTheAbsenceSource() {
    long order = settledOrderIn(HISTORICAL_IMPORT_BATCH);
    auditEvent(order, "SETTLEMENT_DETECTED");

    migrateTo(THE_SOURCE);

    assertThat(sourceOf(order)).isEqualTo("PENDING_REPORT_ABSENCE");
  }

  @Test
  void anImportedOrderAReplayMovedToItsFirstAbsence_keepsTheAbsenceSource() {
    long order = settledOrderIn(HISTORICAL_IMPORT_BATCH);
    auditEvent(order, "SETTLEMENT_REDATED");

    migrateTo(THE_SOURCE);

    assertThat(sourceOf(order)).isEqualTo("PENDING_REPORT_ABSENCE");
  }

  @Test
  void aRegistryOrder_keepsTheAbsenceSource() {
    long order = settledOrderIn(REGISTRY_BATCH);

    migrateTo(THE_SOURCE);

    assertThat(sourceOf(order)).isEqualTo("PENDING_REPORT_ABSENCE");
  }

  private void migrateTo(String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(FLYWAY_LOCATIONS)
        .baselineOnMigrate(true)
        .target(version)
        .load()
        .migrate();
  }

  private long settledOrderIn(String batchCreatedBy) {
    long batch =
        jdbcClient
            .sql(
                "INSERT INTO investment_transaction_batch (fund_code, created_by)"
                    + " VALUES ('TKF100', :createdBy) RETURNING id")
            .param("createdBy", batchCreatedBy)
            .query(Long.class)
            .single();
    long order =
        jdbcClient
            .sql(
                "INSERT INTO investment_transaction_order (batch_id, fund_code, instrument_isin,"
                    + " transaction_type, instrument_type, order_venue, order_uuid, order_status)"
                    + " VALUES (:batch, 'TKF100', 'IE00BFG1TM61', 'BUY', 'FUND', 'SEB', :uuid,"
                    + " 'SETTLED') RETURNING id")
            .param("batch", batch)
            .param("uuid", UUID.randomUUID())
            .query(Long.class)
            .single();
    jdbcClient
        .sql(
            "INSERT INTO transaction_settlement (order_id, settled_at, report_date)"
                + " VALUES (:order, CURRENT_TIMESTAMP, :reportDate)")
        .param("order", order)
        .param("reportDate", REPORT_DATE)
        .update();
    return order;
  }

  private void auditEvent(long order, String eventType) {
    jdbcClient
        .sql(
            "INSERT INTO investment_transaction_audit_event (batch_id, order_id, event_type)"
                + " SELECT batch_id, id, :eventType FROM investment_transaction_order"
                + " WHERE id = :order")
        .param("eventType", eventType)
        .param("order", order)
        .update();
  }

  private String sourceOf(long order) {
    return jdbcClient
        .sql("SELECT source FROM transaction_settlement WHERE order_id = :order")
        .param("order", order)
        .query(String.class)
        .single();
  }
}
