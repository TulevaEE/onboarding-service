package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeeCalculationIntegrationTest {

  @Autowired private FeeCalculationService feeCalculationService;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private EntityManager entityManager;

  private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);

  @BeforeEach
  void setUp() {
    insertTestPositionData();
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void calculateDailyFeesForFund_savesManagementFeeAccrual() {
    feeCalculationService.calculateDailyFeesForFund(TUK75, TEST_DATE);

    var accrual = findAccrual(TUK75, FeeType.MANAGEMENT, TEST_DATE);

    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.MANAGEMENT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(accrual.baseValue()).isEqualByComparingTo(new BigDecimal("1000000000"));
    assertThat(accrual.dailyAmountNet()).isPositive();
    assertThat(accrual.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculateDailyFeesForFund_savesDepotFeeAccrual() {
    feeCalculationService.calculateDailyFeesForFund(TUK75, TEST_DATE);

    var accrual = findAccrual(TUK75, FeeType.DEPOT, TEST_DATE);

    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.dailyAmountNet()).isPositive();
    assertThat(accrual.dailyAmountGross()).isGreaterThan(accrual.dailyAmountNet());
    assertThat(accrual.vatRate()).isNotNull();
  }

  @Test
  void calculateDailyFeesForFund_isIdempotent() {
    feeCalculationService.calculateDailyFeesForFund(TKF100, TEST_DATE);
    entityManager.flush();
    var firstAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterFirst = countLedgerEntries();

    feeCalculationService.calculateDailyFeesForFund(TKF100, TEST_DATE);
    entityManager.flush();
    var secondAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterSecond = countLedgerEntries();

    assertThat(secondAccrual.dailyAmountNet()).isEqualByComparingTo(firstAccrual.dailyAmountNet());
    assertThat(ledgerEntriesAfterSecond).isEqualTo(ledgerEntriesAfterFirst);
  }

  @Test
  void calculateDailyFees_onlyRecordsToLedgerForNavEnabledFunds() {
    feeCalculationService.calculateDailyFees(TEST_DATE);
    entityManager.flush();

    int ledgerTransactionCount =
        jdbcClient.sql("SELECT COUNT(*) FROM ledger.transaction").query(Integer.class).single();

    int expectedTransactions = 2; // management + depot for TKF100 only
    assertThat(ledgerTransactionCount).isEqualTo(expectedTransactions);

    for (TulevaFund fund : TulevaFund.values()) {
      assertThat(findAccrual(fund, FeeType.MANAGEMENT, TEST_DATE)).isNotNull();
    }
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

  private void insertTestPositionData() {
    insertPositionCalculation(TUK75, LocalDate.of(2025, 1, 15), new BigDecimal("1000000000"));
    insertPositionCalculation(TUK75, LocalDate.of(2025, 1, 14), new BigDecimal("995000000"));
    insertPositionCalculation(TUK75, LocalDate.of(2025, 1, 13), new BigDecimal("990000000"));
    insertPositionCalculation(
        TulevaFund.TUK00, LocalDate.of(2025, 1, 15), new BigDecimal("100000000"));
    insertPositionCalculation(
        TulevaFund.TUV100, LocalDate.of(2025, 1, 15), new BigDecimal("300000000"));
    insertPositionCalculation(TKF100, LocalDate.of(2025, 1, 15), new BigDecimal("50000000"));
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
