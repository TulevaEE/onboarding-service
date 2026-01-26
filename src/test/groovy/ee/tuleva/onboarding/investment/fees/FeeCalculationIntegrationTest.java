package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.TulevaFund;
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

  private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);

  @BeforeEach
  void setUp() {
    insertTestAumData();
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
    feeCalculationService.calculateDailyFeesForFund(TUK75, TEST_DATE);
    var firstAccrual = findAccrual(TUK75, FeeType.MANAGEMENT, TEST_DATE);

    feeCalculationService.calculateDailyFeesForFund(TUK75, TEST_DATE);
    var secondAccrual = findAccrual(TUK75, FeeType.MANAGEMENT, TEST_DATE);

    assertThat(secondAccrual.dailyAmountNet()).isEqualByComparingTo(firstAccrual.dailyAmountNet());
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

  private void insertTestAumData() {
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 15), new BigDecimal("1000000000"));
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 14), new BigDecimal("995000000"));
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 13), new BigDecimal("990000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2025, 1, 15), new BigDecimal("100000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2025, 1, 15), new BigDecimal("300000000"));
    insertAumValue("AUM_EE3600109435", LocalDate.of(2024, 12, 31), new BigDecimal("980000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2024, 12, 31), new BigDecimal("95000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2024, 12, 31), new BigDecimal("290000000"));
  }

  private void insertAumValue(String key, LocalDate date, BigDecimal value) {
    jdbcClient
        .sql(
            """
            MERGE INTO index_values (key, date, value, provider, updated_at)
            KEY (key, date)
            VALUES (:key, :date, :value, 'TEST', now())
            """)
        .param("key", key)
        .param("date", date)
        .param("value", value)
        .update();
  }

  private void insertFeeRates() {
    jdbcClient
        .sql(
            """
            MERGE INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            KEY (fund_code, fee_type, valid_from)
            VALUES (:fundCode, :feeType, :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", "TUK75")
        .param("feeType", "MANAGEMENT")
        .param("annualRate", new BigDecimal("0.0025"))
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
