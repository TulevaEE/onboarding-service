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

class V1_325ExecutionPricingDateTest {

  private static final String URL =
      "jdbc:h2:mem:executionpricingdatemigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_PRICING_DATE = "1.324";
  private static final String THE_PRICING_DATE = "1.325";

  private static final OffsetDateTime TRADED_AT = OffsetDateTime.parse("2026-05-11T10:26:04Z");

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeThePricingDate() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_PRICING_DATE);
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
  void anEtfFillIsPricedOnItsTradeDate() {
    givenExecution(1, "ETF", null);

    migrateTo(THE_PRICING_DATE);

    assertThat(pricingDateOfExecution(1)).isEqualTo(LocalDate.of(2026, 5, 11));
  }

  @Test
  void aMutualFundTradeKeepsTheNavDateItAlreadyHadAsItsPricingDate() {
    givenExecution(2, "FUND", LocalDate.of(2026, 5, 12));

    migrateTo(THE_PRICING_DATE);

    assertThat(pricingDateOfExecution(2)).isEqualTo(LocalDate.of(2026, 5, 12));
  }

  @Test
  void aMutualFundTradeWithoutANavDateWaitsForItsSebConfirmation() {
    givenExecution(3, "FUND", null);

    migrateTo(THE_PRICING_DATE);

    assertThat(pricingDateOfExecution(3)).isNull();
  }

  @Test
  void theRegistryViewShowsThePricingDate() {
    givenExecution(4, "ETF", null);

    migrateTo(THE_PRICING_DATE);

    assertThat(
            jdbcClient
                .sql("SELECT pricing_date FROM v_transaction_registry WHERE execution_id = 4")
                .query(LocalDate.class)
                .single())
        .isEqualTo(LocalDate.of(2026, 5, 11));
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
        .param("tradedAt", TRADED_AT)
        .param("navDate", navDate)
        .update();
  }

  private @Nullable LocalDate pricingDateOfExecution(long id) {
    return jdbcClient
        .sql("SELECT pricing_date FROM investment_transaction_execution WHERE id = :id")
        .param("id", id)
        .query((rs, rowNum) -> rs.getObject("pricing_date", LocalDate.class))
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
