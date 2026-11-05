package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_325BackfillEtfExecutionNavDateTest {

  private static final String URL =
      "jdbc:h2:mem:etfexecutionnavdatemigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_BACKFILL = "1.324";
  private static final String THE_BACKFILL = "1.325";

  private static final OffsetDateTime TRADED_AT_TALLINN_MIDNIGHT =
      OffsetDateTime.parse("2026-05-11T00:00:00+03:00");

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheBackfill() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_BACKFILL);
    jdbcClient = JdbcClient.create(dataSource);
    jdbcClient
        .sql(
            "INSERT INTO investment_transaction_batch (id, fund_code, created_by)"
                + " VALUES (1, 'TUK75', 'test-user')")
        .update();
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void anEtfFillGetsItsTallinnTradeDateAsItsNavDate() {
    givenExecution(1, "ETF", null);

    migrateTo(THE_BACKFILL);

    assertThat(navDateOfExecution(1)).isEqualTo(LocalDate.of(2026, 5, 11));
  }

  @Test
  void anEtfFillThatAlreadyHasANavDateKeepsIt() {
    givenExecution(2, "ETF", LocalDate.of(2026, 5, 12));

    migrateTo(THE_BACKFILL);

    assertThat(navDateOfExecution(2)).isEqualTo(LocalDate.of(2026, 5, 12));
  }

  @Test
  void aMutualFundTradeWithoutANavDateWaitsForItsSebConfirmation() {
    givenExecution(3, "FUND", null);

    migrateTo(THE_BACKFILL);

    assertThat(navDateOfExecution(3)).isNull();
  }

  private void givenExecution(long id, String instrumentType, @Nullable LocalDate navDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_transaction_order
              (id, batch_id, fund_code, instrument_isin, transaction_type, instrument_type,
               order_venue, order_uuid, order_status)
            VALUES (:id, 1, 'TUK75', 'IE00BKPTWY98', 'BUY', :instrumentType, 'SEB', :orderUuid,
                    'EXECUTED')
            """)
        .param("id", id)
        .param("instrumentType", instrumentType)
        .param("orderUuid", UUID.randomUUID())
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_transaction_execution
              (id, order_id, broker_transaction_id, execution_timestamp, nav_date, source)
            VALUES (:id, :id, :reference, :tradedAt, :navDate, 'SEB_OOTEL')
            """)
        .param("id", id)
        .param("reference", "DLA000000" + id)
        .param("tradedAt", TRADED_AT_TALLINN_MIDNIGHT)
        .param("navDate", navDate)
        .update();
  }

  private @Nullable LocalDate navDateOfExecution(long id) {
    return jdbcClient
        .sql("SELECT nav_date FROM investment_transaction_execution WHERE id = :id")
        .param("id", id)
        .query((rs, rowNum) -> rs.getObject("nav_date", LocalDate.class))
        .list()
        .getFirst();
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
}
