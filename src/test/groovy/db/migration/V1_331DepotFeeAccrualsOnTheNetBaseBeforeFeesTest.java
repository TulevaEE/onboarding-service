package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_331DepotFeeAccrualsOnTheNetBaseBeforeFeesTest {

  private static final String URL =
      "jdbc:h2:mem:depotnetbasemigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_REWRITE_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.330?";
  private static final String THE_REWRITE = "1.331";

  private static final String FUND = "TUK75";
  private static final String OTHER_FUND = "TUK00";
  private static final LocalDate DAY = LocalDate.of(2026, 10, 15);
  private static final LocalDate LEAP_DAY = LocalDate.of(2028, 2, 29);
  private static final BigDecimal NET_BASE_BEFORE_FEES = new BigDecimal("1000000.00");
  private static final BigDecimal GROSS_ASSETS = new BigDecimal("1080000.00");
  private static final BigDecimal MANAGEMENT_RATE = new BigDecimal("0.00250000");
  private static final BigDecimal DEPOT_RATE = new BigDecimal("0.01000000");

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheRewrite() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_REWRITE_EVEN_IF_NO_MIGRATION_HOLDS_IT);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void aDepotRowOnTheGrossAssetsTakesTheManagementBaseAndItsAmountFromItsOwnRate() {
    givenAccrual(FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "DEPOT", DAY))
        .isEqualTo(new Row(NET_BASE_BEFORE_FEES, DEPOT_RATE, new BigDecimal("27.397260"), 365));
  }

  @Test
  void aLeapYearDepotRowIsRecomputedOverItsOwn366Days() {
    givenAccrual(FUND, "MANAGEMENT", LEAP_DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", LEAP_DAY, GROSS_ASSETS, DEPOT_RATE, 366);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "DEPOT", LEAP_DAY))
        .isEqualTo(new Row(NET_BASE_BEFORE_FEES, DEPOT_RATE, new BigDecimal("27.322404"), 366));
  }

  @Test
  void theManagementRowItselfIsLeftExactlyAsItWas() {
    givenAccrual(FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);
    var before = accrual(FUND, "MANAGEMENT", DAY);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "MANAGEMENT", DAY)).isEqualTo(before);
  }

  @Test
  void aDepotRowAlreadyOnTheManagementBaseKeepsItsRecordedAmount() {
    givenAccrual(FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, NET_BASE_BEFORE_FEES, DEPOT_RATE, 365);
    var before = accrual(FUND, "DEPOT", DAY);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "DEPOT", DAY)).isEqualTo(before);
  }

  @Test
  void aDepotRowWithNoManagementRowOfItsOwnFundThatDayIsLeftAlone() {
    givenAccrual(OTHER_FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "MANAGEMENT", DAY.minusDays(1), NET_BASE_BEFORE_FEES, DEPOT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);
    var before = accrual(FUND, "DEPOT", DAY);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "DEPOT", DAY)).isEqualTo(before);
  }

  @Test
  void aDepotRowOnADayTheFundIsChargedTheDepotFeeIsLeftAlone() {
    givenDepotFeeChargedToTheFundFrom(FUND, DAY);
    givenAccrual(FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);
    var before = accrual(FUND, "DEPOT", DAY);

    migrateTo(THE_REWRITE);

    assertThat(accrual(FUND, "DEPOT", DAY)).isEqualTo(before);
  }

  @Test
  void onlyTheDepotRowsTheRewriteNeedsAreTouched() {
    givenAccrual(FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);
    givenAccrual(OTHER_FUND, "MANAGEMENT", DAY, NET_BASE_BEFORE_FEES, MANAGEMENT_RATE, 365);
    givenAccrual(OTHER_FUND, "DEPOT", DAY, GROSS_ASSETS, DEPOT_RATE, 365);

    migrateTo(THE_REWRITE);

    assertThat(depotBases()).containsExactlyInAnyOrder(NET_BASE_BEFORE_FEES, NET_BASE_BEFORE_FEES);
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

  private void givenAccrual(
      String fund,
      String feeType,
      LocalDate accrualDate,
      BigDecimal baseValue,
      BigDecimal annualRate,
      int daysInYear) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_accrual
              (fund_code, fee_type, accrual_date, fee_month, base_value, annual_rate,
               daily_amount_gross, days_in_year)
            VALUES (:fund, :feeType, :accrualDate, :feeMonth, :baseValue, :annualRate,
                    :dailyAmountGross, :daysInYear)
            """)
        .param("fund", fund)
        .param("feeType", feeType)
        .param("accrualDate", accrualDate)
        .param("feeMonth", accrualDate.withDayOfMonth(1))
        .param("baseValue", baseValue)
        .param("annualRate", annualRate)
        .param("dailyAmountGross", new BigDecimal("9.999999"))
        .param("daysInYear", daysInYear)
        .update();
  }

  private void givenDepotFeeChargedToTheFundFrom(String fund, LocalDate validFrom) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_policy (fund_code, fee_type, charged_to_fund, valid_from)
            VALUES (:fund, 'DEPOT', true, :validFrom)
            """)
        .param("fund", fund)
        .param("validFrom", validFrom)
        .update();
  }

  private Row accrual(String fund, String feeType, LocalDate accrualDate) {
    return jdbcClient
        .sql(
            """
            SELECT base_value, annual_rate, daily_amount_gross, days_in_year
            FROM investment_fee_accrual
            WHERE fund_code = :fund AND fee_type = :feeType AND accrual_date = :accrualDate
            """)
        .param("fund", fund)
        .param("feeType", feeType)
        .param("accrualDate", accrualDate)
        .query(
            (rs, rowNum) ->
                new Row(
                    rs.getBigDecimal("base_value"),
                    rs.getBigDecimal("annual_rate"),
                    rs.getBigDecimal("daily_amount_gross"),
                    rs.getInt("days_in_year")))
        .single();
  }

  private List<BigDecimal> depotBases() {
    return jdbcClient
        .sql("SELECT base_value FROM investment_fee_accrual WHERE fee_type = 'DEPOT'")
        .query(BigDecimal.class)
        .list();
  }

  private record Row(
      BigDecimal baseValue, BigDecimal annualRate, BigDecimal dailyAmountGross, int daysInYear) {}
}
