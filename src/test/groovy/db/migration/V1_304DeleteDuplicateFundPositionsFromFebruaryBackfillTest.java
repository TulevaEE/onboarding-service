package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_304DeleteDuplicateFundPositionsFromFebruaryBackfillTest {

  private static final String URL =
      "jdbc:h2:mem:duplicatefundpositionsmigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_CLEANUP_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.303?";
  private static final String THE_CLEANUP = "1.304";

  private static final String ISIN = "ZZ0000000001";
  private static final String OTHER_ISIN = "ZZ0000000002";
  private static final String FUND = "TUK75";
  private static final String OTHER_FUND = "TUV100";
  private static final String SECURITY = "SECURITY";
  private static final String CASH = "CASH";

  private static final LocalDate FIRST_BACKFILLED_NAV_DATE = LocalDate.of(2026, 1, 20);
  private static final LocalDate LAST_BACKFILLED_NAV_DATE = LocalDate.of(2026, 1, 26);
  private static final LocalDate DAY_BEFORE_THE_BACKFILLED_DATES = LocalDate.of(2026, 1, 19);
  private static final LocalDate DAY_AFTER_THE_BACKFILLED_DATES = LocalDate.of(2026, 1, 27);

  private static final LocalDateTime ORIGINALLY_WRITTEN = LocalDateTime.of(2026, 2, 2, 13, 5);
  private static final LocalDateTime BACKFILL_WRITTEN = LocalDateTime.of(2026, 2, 20, 9, 57);
  private static final LocalDateTime SIX_DAYS_AFTER_THE_ORIGINAL = ORIGINALLY_WRITTEN.plusDays(6);
  private static final LocalDateTime SEVEN_DAYS_AFTER_THE_ORIGINAL = ORIGINALLY_WRITTEN.plusDays(7);

  private static final String ORIGINAL_NAME = "ORIGINAL CUSTODIAN NAME";
  private static final String BACKFILLED_NAME = "Backfilled custodian name";

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
  void aSecurityBackfilledWeeksAfterItsOriginalUnderAnotherNameIsDeletedAndTheOriginalKept() {
    long original = givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, BACKFILLED_NAME, BACKFILL_WRITTEN);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactly(original);
  }

  @Test
  void everyBackfilledNavDateFromTheTwentiethToTheTwentySixthIsCleaned() {
    long firstOriginal =
        givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, BACKFILLED_NAME, BACKFILL_WRITTEN);
    long lastOriginal = givenPosition(LAST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN);
    givenPosition(LAST_BACKFILLED_NAV_DATE, BACKFILLED_NAME, BACKFILL_WRITTEN);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactly(firstOriginal, lastOriginal);
  }

  @Test
  void aSecurityWrittenExactlySevenDaysAfterItsOriginalIsDeleted() {
    long original = givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN);
    givenPosition(FIRST_BACKFILLED_NAV_DATE, BACKFILLED_NAME, SEVEN_DAYS_AFTER_THE_ORIGINAL);

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactly(original);
  }

  @Test
  void aSecurityWrittenWithinSevenDaysOfItsSiblingIsKept() {
    var both =
        List.of(
            givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(FIRST_BACKFILLED_NAV_DATE, BACKFILLED_NAME, SIX_DAYS_AFTER_THE_ORIGINAL));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(both);
  }

  @Test
  void duplicatesOnNavDatesOutsideTheBackfilledWeekAreKept() {
    var all =
        List.of(
            givenPosition(DAY_BEFORE_THE_BACKFILLED_DATES, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(DAY_BEFORE_THE_BACKFILLED_DATES, BACKFILLED_NAME, BACKFILL_WRITTEN),
            givenPosition(DAY_AFTER_THE_BACKFILLED_DATES, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(DAY_AFTER_THE_BACKFILLED_DATES, BACKFILLED_NAME, BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(all);
  }

  @Test
  void aBackfilledSecurityWithoutAnEarlierRowForTheSameIsinIsKept() {
    var both =
        List.of(
            givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(
                FUND,
                FIRST_BACKFILLED_NAV_DATE,
                SECURITY,
                OTHER_ISIN,
                BACKFILLED_NAME,
                BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(both);
  }

  @Test
  void theSameIsinBackfilledIntoAnotherFundIsKept() {
    var both =
        List.of(
            givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(
                OTHER_FUND,
                FIRST_BACKFILLED_NAV_DATE,
                SECURITY,
                ISIN,
                BACKFILLED_NAME,
                BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(both);
  }

  @Test
  void theSameAccountIdBackfilledUnderAnotherAccountTypeIsKept() {
    var both =
        List.of(
            givenPosition(FIRST_BACKFILLED_NAV_DATE, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(
                FUND, FIRST_BACKFILLED_NAV_DATE, CASH, ISIN, BACKFILLED_NAME, BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(both);
  }

  @Test
  void backfilledRowsWithoutAnAccountIdAreKeptEvenBesideEarlierRowsWithoutOne() {
    var both =
        List.of(
            givenPosition(
                FUND, FIRST_BACKFILLED_NAV_DATE, CASH, null, ORIGINAL_NAME, ORIGINALLY_WRITTEN),
            givenPosition(
                FUND, FIRST_BACKFILLED_NAV_DATE, CASH, null, BACKFILLED_NAME, BACKFILL_WRITTEN));

    migrateTo(THE_CLEANUP);

    assertThat(remainingPositionIds()).containsExactlyElementsOf(both);
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

  private long givenPosition(LocalDate navDate, String accountName, LocalDateTime createdAt) {
    return givenPosition(FUND, navDate, SECURITY, ISIN, accountName, createdAt);
  }

  private long givenPosition(
      String fundCode,
      LocalDate navDate,
      String accountType,
      String accountId,
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
        .param("fundCode", fundCode)
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
        .param("fundCode", fundCode)
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
