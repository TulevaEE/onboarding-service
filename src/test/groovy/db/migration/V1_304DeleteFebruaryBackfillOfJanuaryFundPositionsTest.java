package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_304DeleteFebruaryBackfillOfJanuaryFundPositionsTest {

  private static final String URL =
      "jdbc:h2:mem:februarybackfillmigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_CLEANUP_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.303?";
  private static final String THE_CLEANUP = "1.304";

  private static final String FUND = "TUK75";
  private static final String ISIN = "ZZ0000000001";

  private static final LocalDate FIRST_BACKFILLED_NAV_DATE = LocalDate.of(2026, 1, 20);
  private static final LocalDate LAST_BACKFILLED_NAV_DATE = LocalDate.of(2026, 1, 26);
  private static final LocalDate DAY_BEFORE_THE_BACKFILLED_DATES = LocalDate.of(2026, 1, 19);
  private static final LocalDate DAY_AFTER_THE_BACKFILLED_DATES = LocalDate.of(2026, 1, 27);

  private static final LocalDateTime ORIGINALLY_WRITTEN = LocalDateTime.of(2026, 2, 2, 13, 5);
  private static final LocalDateTime BACKFILL_MINUTE_STARTS = LocalDateTime.of(2026, 2, 20, 9, 57);
  private static final LocalDateTime BACKFILL_WRITTEN = BACKFILL_MINUTE_STARTS.plusSeconds(2);
  private static final LocalDateTime BACKFILL_MINUTE_ENDS = BACKFILL_MINUTE_STARTS.plusMinutes(1);
  private static final LocalDateTime JUST_BEFORE_THE_BACKFILL_MINUTE =
      BACKFILL_MINUTE_STARTS.minusNanos(1_000);
  private static final LocalDateTime MORNING_SWEDBANK_IMPORT_THAT_DAY =
      LocalDateTime.of(2026, 2, 20, 6, 2);
  private static final LocalDateTime MORNING_SEB_IMPORT_THAT_DAY =
      LocalDateTime.of(2026, 2, 20, 7, 27);

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheCleanup() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_CLEANUP_EVEN_IF_NO_MIGRATION_HOLDS_IT);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void everyAccountTypeTheBackfillWroteIsDeletedAndThePositionsWrittenAtTheTimeAreKept() {
    var writtenAtTheTime =
        List.of(
            givenPosition(
                FIRST_BACKFILLED_NAV_DATE, "SECURITY", ISIN, "AT THE TIME", ORIGINALLY_WRITTEN),
            givenPosition(FIRST_BACKFILLED_NAV_DATE, "CASH", null, "Deposit", ORIGINALLY_WRITTEN));
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "SECURITY", ISIN, "Backfilled", BACKFILL_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "CASH", null, "Cash account", BACKFILL_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "UNITS", "ZZ0000000009", "Units", BACKFILL_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "NAV", null, "TotalNetAsset", BACKFILL_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "LIABILITY", null, "Payables", BACKFILL_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, "RECEIVABLES", null, "Receivables", BACKFILL_WRITTEN);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(writtenAtTheTime);
  }

  @Test
  void theFirstAndLastBackfilledNavDatesAreBothCleaned() {
    givenBackfilledSecurity(FIRST_BACKFILLED_NAV_DATE, BACKFILL_WRITTEN);
    givenBackfilledSecurity(LAST_BACKFILLED_NAV_DATE, BACKFILL_WRITTEN);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).isEmpty();
  }

  @Test
  void aPositionWrittenAtTheFirstInstantOfTheBackfillMinuteIsDeleted() {
    givenBackfilledSecurity(FIRST_BACKFILLED_NAV_DATE, BACKFILL_MINUTE_STARTS);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).isEmpty();
  }

  @Test
  void positionsWrittenJustOutsideTheBackfillMinuteAreKept() {
    var outsideTheMinute =
        List.of(
            givenPosition(
                FIRST_BACKFILLED_NAV_DATE,
                "SECURITY",
                ISIN,
                "Just before",
                JUST_BEFORE_THE_BACKFILL_MINUTE),
            givenPosition(
                FIRST_BACKFILLED_NAV_DATE, "SECURITY", ISIN, "Just after", BACKFILL_MINUTE_ENDS));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(outsideTheMinute);
  }

  @Test
  void theDailyImportsWrittenEarlierThatMorningAreKept() {
    var dailyImports =
        List.of(
            givenPosition(
                FIRST_BACKFILLED_NAV_DATE,
                "SECURITY",
                ISIN,
                "Swedbank",
                MORNING_SWEDBANK_IMPORT_THAT_DAY),
            givenPosition(
                FIRST_BACKFILLED_NAV_DATE, "SECURITY", ISIN, "SEB", MORNING_SEB_IMPORT_THAT_DAY));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(dailyImports);
  }

  @Test
  void positionsWrittenInTheBackfillMinuteForNavDatesOutsideTheBackfilledWeekAreKept() {
    var outsideTheWeek =
        List.of(
            givenBackfilledSecurity(DAY_BEFORE_THE_BACKFILLED_DATES, BACKFILL_WRITTEN),
            givenBackfilledSecurity(DAY_AFTER_THE_BACKFILLED_DATES, BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(outsideTheWeek);
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

  private long givenBackfilledSecurity(LocalDate navDate, LocalDateTime createdAt) {
    return givenPosition(navDate, "SECURITY", ISIN, "Backfilled", createdAt);
  }

  private long givenPosition(
      LocalDate navDate,
      String accountType,
      @Nullable String accountId,
      String accountName,
      LocalDateTime createdAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
              (nav_date, fund_code, account_type, account_id, account_name,
               quantity, currency, market_price, market_value, created_at)
            VALUES (:navDate, :fundCode, :accountType, :accountId, :accountName,
                    :quantity, 'EUR', :marketPrice, :marketValue, :createdAt)
            """)
        .param("navDate", navDate)
        .param("fundCode", FUND)
        .param("accountType", accountType)
        .param("accountId", accountId)
        .param("accountName", accountName)
        .param("quantity", new BigDecimal("1000.00000000"))
        .param("marketPrice", new BigDecimal("10.00000000"))
        .param("marketValue", new BigDecimal("10000.00"))
        .param("createdAt", createdAt)
        .update();
    return jdbcClient
        .sql(
            """
            SELECT id FROM investment_fund_position
            WHERE nav_date = :navDate AND fund_code = :fundCode
              AND account_type = :accountType AND account_name = :accountName
            """)
        .param("navDate", navDate)
        .param("fundCode", FUND)
        .param("accountType", accountType)
        .param("accountName", accountName)
        .query(Long.class)
        .single();
  }

  private List<Long> remainingPositionIds() {
    return jdbcClient
        .sql("SELECT id FROM investment_fund_position ORDER BY id")
        .query(Long.class)
        .list();
  }
}
