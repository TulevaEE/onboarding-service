package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
  @Autowired private FeeAccrualRepository feeAccrualRepository;
  @Autowired private NavLedgerRepository navLedgerRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    insertSecurityPositions();
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void calculateFeesForNav_savesManagementFeeAccrual() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(
        TUK75, TEST_DATE, new FeeBases(BASE_VALUE, BASE_VALUE), feeCutoff, null);

    var accrual = findAccrual(TUK75, FeeType.MANAGEMENT, TEST_DATE);
    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.MANAGEMENT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(accrual.baseValue()).isEqualByComparingTo(BASE_VALUE);
    assertThat(accrual.dailyAmountGross()).isPositive();
    assertThat(accrual.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculateFeesForNav_savesDepotFeeAccrual() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(
        TUK75, TEST_DATE, new FeeBases(BASE_VALUE, BASE_VALUE), feeCutoff, null);

    var accrual = findAccrual(TUK75, FeeType.DEPOT, TEST_DATE);
    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.dailyAmountGross()).isPositive();
  }

  @Test
  void calculateFeesForNav_isIdempotent() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    feeCalculationService.calculateFeesForNav(
        TKF100, TEST_DATE, new FeeBases(BASE_VALUE, BASE_VALUE), feeCutoff, null);
    var firstAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterFirst = countLedgerEntries();

    feeCalculationService.calculateFeesForNav(
        TKF100, TEST_DATE, new FeeBases(BASE_VALUE, BASE_VALUE), feeCutoff, null);
    var secondAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterSecond = countLedgerEntries();

    assertThat(secondAccrual.dailyAmountGross())
        .isEqualByComparingTo(firstAccrual.dailyAmountGross());
    assertThat(ledgerEntriesAfterSecond).isEqualTo(ledgerEntriesAfterFirst);
  }

  @Test
  void calculateFeesForNav_returnsTheNavFacingAccrualNotTheRawOne() {
    Instant feeCutoff = TEST_DATE.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeResult result =
        feeCalculationService.calculateFeesForNav(
            TKF100, TEST_DATE, new FeeBases(BASE_VALUE, BASE_VALUE), feeCutoff, null);

    // FeeResult is NAV-facing: the charged-to-fund policy is applied per accrual date here, so a
    // fee Tuleva bears comes back as zero even though it was accrued and recorded. TKF100's depot
    // fee is exactly that case ("tracked but not charged to the fund"); the accrual row still
    // exists, which the other tests in this class assert.
    assertThat(result.managementFeeAccrual()).isPositive();
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    assertThat(findAccrual(TKF100, FeeType.DEPOT, TEST_DATE).dailyAmountGross()).isPositive();
  }

  @Test
  void calculateFeesForNav_revisesAnAlreadyAccruedDayByAppendingTheDeltaAndKeepsLaterDaysRight() {
    LocalDate dayOne = TEST_DATE;
    LocalDate dayTwo = TEST_DATE.plusDays(1);
    BigDecimal firstBase = new BigDecimal("1000000000");
    BigDecimal dayTwoBase = new BigDecimal("1001000000");
    BigDecimal correctedBase = new BigDecimal("994000000");
    calculate(TUK75, dayOne, firstBase);
    calculate(TUK75, dayTwo, dayTwoBase);

    calculate(TUK75, dayOne, correctedBase);
    FeeResult throughDayTwo = calculate(TUK75, dayTwo, dayTwoBase);

    assertThat(findAccrual(TUK75, MANAGEMENT, dayOne).baseValue())
        .isEqualByComparingTo(correctedBase);
    assertThat(findAccrual(TUK75, MANAGEMENT, dayOne).dailyAmountGross())
        .isEqualByComparingTo("6808.219178");
    assertThat(throughDayTwo.managementFeeAccrual()).isEqualByComparingTo("13664.38");
    assertThat(managementFeeLedgerAmounts(TUK75, dayOne, dayTwo))
        .containsExactlyInAnyOrder(
            new BigDecimal("-6849.32"), new BigDecimal("41.10"), new BigDecimal("-6856.16"));
    assertThat(managementFeeBalance(TUK75)).isEqualByComparingTo("-13664.38");
  }

  @Test
  void calculateFeesForNav_revisingBackAndForthAppendsEveryStepAndEndsOnTheLatestValue() {
    LocalDate dayOne = TEST_DATE;
    BigDecimal firstBase = new BigDecimal("1000000000");
    BigDecimal correctedBase = new BigDecimal("994000000");
    calculate(TUK75, dayOne, firstBase);

    calculate(TUK75, dayOne, correctedBase);
    calculate(TUK75, dayOne, firstBase);
    calculate(TUK75, dayOne, correctedBase);
    calculate(TUK75, dayOne, correctedBase);

    assertThat(managementFeeLedgerAmounts(TUK75, dayOne, dayOne))
        .containsExactlyInAnyOrder(
            new BigDecimal("-6849.32"),
            new BigDecimal("41.10"),
            new BigDecimal("-41.10"),
            new BigDecimal("41.10"));
    assertThat(managementFeeBalance(TUK75)).isEqualByComparingTo("-6808.22");
  }

  @Test
  void calculateFeesForNav_recordsTheLedgerEntryForAnAccrualRowThatHasNone() {
    LocalDate dayOne = TEST_DATE;
    BigDecimal base = new BigDecimal("1000000000");
    feeAccrualRepository.save(
        FeeAccrual.builder()
            .fund(TUK75)
            .feeType(MANAGEMENT)
            .accrualDate(dayOne)
            .feeMonth(dayOne.withDayOfMonth(1))
            .baseValue(base)
            .annualRate(new BigDecimal("0.0025"))
            .dailyAmountGross(new BigDecimal("6849.315068"))
            .daysInYear(365)
            .referenceDate(dayOne)
            .build());

    calculate(TUK75, dayOne, base);

    assertThat(managementFeeLedgerAmounts(TUK75, dayOne, dayOne))
        .containsExactly(new BigDecimal("-6849.32"));
  }

  private FeeResult calculate(TulevaFund fund, LocalDate positionReportDate, BigDecimal base) {
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    return feeCalculationService.calculateFeesForNav(
        fund, positionReportDate, new FeeBases(base, base), feeCutoff, null);
  }

  private List<BigDecimal> managementFeeLedgerAmounts(
      TulevaFund fund, LocalDate from, LocalDate to) {
    return navLedgerRepository
        .findEntriesByTransactionTypeBetween(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(fund),
            FEE_ACCRUAL,
            from.atStartOfDay(ESTONIAN_ZONE).toInstant(),
            to.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant())
        .stream()
        .map(LedgerEntryAmount::amount)
        .map(amount -> amount.setScale(2, java.math.RoundingMode.HALF_UP))
        .toList();
  }

  private BigDecimal managementFeeBalance(TulevaFund fund) {
    return navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName(fund));
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

  private void insertSecurityPositions() {
    insertSecurityPosition(TUK75, LocalDate.of(2024, 12, 31), new BigDecimal("980000000"));
    insertSecurityPosition(
        TulevaFund.TUK00, LocalDate.of(2024, 12, 31), new BigDecimal("95000000"));
    insertSecurityPosition(
        TulevaFund.TUV100, LocalDate.of(2024, 12, 31), new BigDecimal("290000000"));
    insertSecurityPosition(TKF100, LocalDate.of(2024, 12, 31), new BigDecimal("48000000"));
  }

  private void insertSecurityPosition(TulevaFund fund, LocalDate date, BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, account_id, market_value)
            VALUES (:navDate, :fundCode, 'SECURITY', :accountId, :accountId, :marketValue)
            """)
        .param("navDate", date)
        .param("fundCode", fund.name())
        .param("accountId", "TEST_ISIN_" + fund.name())
        .param("marketValue", marketValue)
        .update();
  }

  private void insertFeeRates() {
    for (TulevaFund fund : TulevaFund.values()) {
      insertFeeRate(fund.name(), "MANAGEMENT", new BigDecimal("0.0025"), "FIXED");
      insertFeeRate(fund.name(), "DEPOT", ZERO, "TIER");
    }
  }

  private void insertFeeRate(
      String fundCode, String feeType, BigDecimal annualRate, String rateSource) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate
                (fund_code, fee_type, annual_rate, rate_source, valid_from, created_by)
            VALUES (:fundCode, :feeType, :annualRate, :rateSource, :validFrom, 'TEST')
            """)
        .param("fundCode", fundCode)
        .param("feeType", feeType)
        .param("annualRate", annualRate)
        .param("rateSource", rateSource)
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }

  private void insertDepotFeeTiers() {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_depot_fee_tier (min_aum, annual_rate, valid_from)
            VALUES (:minAum, :annualRate, :validFrom)
            """)
        .param("minAum", 0)
        .param("annualRate", new BigDecimal("0.01"))
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }
}
