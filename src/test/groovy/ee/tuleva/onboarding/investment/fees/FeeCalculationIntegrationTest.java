package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FeeCalculationIntegrationTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);
  private static final BigDecimal BASE_VALUE = new BigDecimal("1000000000");

  @Autowired private FeeCalculationService feeCalculationService;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    insertPositionCalculations();
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void calculateFeesForNav_savesManagementFeeAccrual() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(TUK75, TEST_DATE, BASE_VALUE, feeCutoff, null);

    var accrual = findAccrual(TUK75, FeeType.MANAGEMENT, TEST_DATE);
    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.MANAGEMENT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(accrual.baseValue()).isEqualByComparingTo(BASE_VALUE);
    assertThat(accrual.dailyAmountNet()).isPositive();
    assertThat(accrual.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculateFeesForNav_savesDepotFeeAccrual() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(TUK75, TEST_DATE, BASE_VALUE, feeCutoff, null);

    var accrual = findAccrual(TUK75, FeeType.DEPOT, TEST_DATE);
    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.dailyAmountNet()).isPositive();
    assertThat(accrual.dailyAmountGross()).isGreaterThan(accrual.dailyAmountNet());
    assertThat(accrual.vatRate()).isNotNull();
  }

  @Test
  void calculateFeesForNav_isIdempotent() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, feeCutoff, null);
    var firstAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterFirst = countLedgerEntries();

    feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, feeCutoff, null);
    var secondAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterSecond = countLedgerEntries();

    assertThat(secondAccrual.dailyAmountNet()).isEqualByComparingTo(firstAccrual.dailyAmountNet());
    assertThat(ledgerEntriesAfterSecond).isEqualTo(ledgerEntriesAfterFirst);
  }

  @Test
  void calculateFeesForNav_returnsFeeResult() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeResult result =
        feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, feeCutoff, null);

    assertThat(result.managementFeeAccrual()).isPositive();
    assertThat(result.depotFeeAccrual()).isPositive();
  }

  private FeeAccrual findAccrual(TulevaFund fund, FeeType feeType, LocalDate accrualDate) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_fee_accrual
            WHERE fund_code = :fundCode AND fee_type = :feeType AND accrual_date = :accrualDate
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("accrualDate", accrualDate)
        .query(FeeAccrual::fromResultSet)
        .single();
  }

  private int countLedgerEntries() {
    return jdbcClient.sql("SELECT COUNT(*) FROM ledger.entry").query(Integer.class).single();
  }

  private void insertPositionCalculations() {
    insertPositionCalculation(TUK75, LocalDate.of(2024, 12, 31), new BigDecimal("980000000"));
    insertPositionCalculation(
        TulevaFund.TUK00, LocalDate.of(2024, 12, 31), new BigDecimal("95000000"));
    insertPositionCalculation(
        TulevaFund.TUV100, LocalDate.of(2024, 12, 31), new BigDecimal("290000000"));
    insertPositionCalculation(TKF100, LocalDate.of(2024, 12, 31), new BigDecimal("48000000"));
  }

  private void insertPositionCalculation(TulevaFund fund, LocalDate date, BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_position_calculation
            (isin, fund_code, date, quantity, calculated_market_value, validation_status, created_at)
            VALUES (:isin, :fundCode, :date, 1, :marketValue, 'OK', now())
            """)
        .param("isin", "TEST_ISIN_" + fund.name())
        .param("fundCode", fund.name())
        .param("date", date)
        .param("marketValue", marketValue)
        .update();
  }

  private void insertFeeRates() {
    for (TulevaFund fund : TulevaFund.values()) {
      insertFeeRate(fund.name(), "MANAGEMENT", new BigDecimal("0.0025"));
    }
  }

  private void insertFeeRate(String fundCode, String feeType, BigDecimal annualRate) {
    jdbcClient
        .sql(
            """
            MERGE INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            KEY (fund_code, fee_type, valid_from)
            VALUES (:fundCode, :feeType, :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", fundCode)
        .param("feeType", feeType)
        .param("annualRate", annualRate)
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }

  private void insertDepotFeeTiers() {
    jdbcClient
        .sql(
            """
            MERGE INTO investment_depot_fee_tier (min_aum, annual_rate, valid_from)
            KEY (min_aum, valid_from)
            VALUES (:minAum, :annualRate, :validFrom)
            """)
        .param("minAum", 0)
        .param("annualRate", new BigDecimal("0.00035"))
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }
}
