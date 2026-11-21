package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private NavFeeAccrualLedger navFeeAccrualLedger;

  @BeforeEach
  void setUp() {
    insertSecurityPositions();
    insertFeeRates();
    insertDepotFeeTiers();
  }

  @Test
  void calculateFeesForNav_savesManagementFeeAccrual() {
    feeCalculationService.calculateFeesForNav(TUK75, TEST_DATE, BASE_VALUE, null);

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
    feeCalculationService.calculateFeesForNav(TUK75, TEST_DATE, BASE_VALUE, null);

    var accrual = findAccrual(TUK75, FeeType.DEPOT, TEST_DATE);
    assertThat(accrual.fund()).isEqualTo(TUK75);
    assertThat(accrual.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(accrual.accrualDate()).isEqualTo(TEST_DATE);
    assertThat(accrual.dailyAmountGross()).isPositive();
  }

  @Test
  void calculateFeesForNav_isIdempotent() {
    feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, null);
    var firstAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterFirst = countLedgerEntries();

    feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, null);
    var secondAccrual = findAccrual(TKF100, FeeType.MANAGEMENT, TEST_DATE);
    int ledgerEntriesAfterSecond = countLedgerEntries();

    assertThat(secondAccrual.dailyAmountGross())
        .isEqualByComparingTo(firstAccrual.dailyAmountGross());
    assertThat(ledgerEntriesAfterSecond).isEqualTo(ledgerEntriesAfterFirst);
  }

  @Test
  void calculateFeesForNav_returnsTheNavFacingAccrualNotTheRawOne() {
    FeeResult result =
        feeCalculationService.calculateFeesForNav(TKF100, TEST_DATE, BASE_VALUE, null);

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
            .build());

    calculate(TUK75, dayOne, base);

    assertThat(managementFeeLedgerAmounts(TUK75, dayOne, dayOne))
        .containsExactly(new BigDecimal("-6849.32"));
  }

  @Test
  void calculateFeesForNav_keepsAMonthsManagementFeeOwedPastItsEndUntilTheFundsDebitClearsIt() {
    LocalDate november30 = LocalDate.of(2026, 11, 30);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate december2 = LocalDate.of(2026, 12, 2);
    calculate(TUK75, november30, BASE_VALUE);

    FeeResult beforeTheDebit = calculate(TUK75, december1, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "6849.32", december2);
    FeeResult afterTheDebit = calculate(TUK75, december2, BASE_VALUE);

    assertThat(beforeTheDebit.managementFeeAccrual()).isEqualByComparingTo("13698.64");
    assertThat(afterTheDebit.managementFeeAccrual()).isEqualByComparingTo("13698.63");
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, november30, december1)).isEmpty();
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december2, december2))
        .containsExactly(new BigDecimal("6849.32"));
    assertThat(managementFeeBalance(TUK75)).isEqualByComparingTo("-13698.64");
  }

  @Test
  void calculateFeesForNav_releasesWhatADebitBeyondTheThresholdPaidAndTheRestWhenItComes() {
    LocalDate november30 = LocalDate.of(2026, 11, 30);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate december2 = LocalDate.of(2026, 12, 2);
    calculate(TUK75, november30, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "6000.00", december1);

    FeeResult afterTheShortDebit = calculate(TUK75, december1, BASE_VALUE);
    BigDecimal ledgerAfterTheShortDebit = managementFeeBalance(TUK75);
    recordManagementFeeDebit(TUK75, "849.32", december2);
    FeeResult afterTheRest = calculate(TUK75, december2, BASE_VALUE);

    assertThat(afterTheShortDebit.managementFeeAccrual()).isEqualByComparingTo("7698.64");
    assertThat(ledgerAfterTheShortDebit).isEqualByComparingTo("-7698.64");
    assertThat(afterTheRest.managementFeeAccrual()).isEqualByComparingTo("13698.63");
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december1, december2))
        .containsExactly(new BigDecimal("6000.00"), new BigDecimal("849.32"));
  }

  @Test
  void calculateFeesForNav_reopensAMonthInTheLedgerAsWellAsTheNavWhenItsDebitIsPartlyReversed() {
    LocalDate november30 = LocalDate.of(2026, 11, 30);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate december2 = LocalDate.of(2026, 12, 2);
    calculate(TUK75, november30, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "6849.32", december1);
    calculate(TUK75, december1, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "-3000.00", december2);

    FeeResult result = calculate(TUK75, december2, BASE_VALUE);

    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("16698.63");
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december1, december2))
        .containsExactly(new BigDecimal("6849.32"), new BigDecimal("-3000.00"));
    assertThat(managementFeeBalance(TUK75)).isEqualByComparingTo("-16698.64");
  }

  @Test
  void calculateFeesForNav_datesAShortDebitsReleaseOnTheDebitDayEvenWhenANavSeesItLater() {
    LocalDate november30 = LocalDate.of(2026, 11, 30);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate december3 = LocalDate.of(2026, 12, 3);
    calculate(TUK75, november30, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "6000.00", december1);
    calculate(TUK75, december3, BASE_VALUE);

    FeeResult rerunOfTheDebitDay = calculate(TUK75, december1, BASE_VALUE);

    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december1, december1))
        .containsExactly(new BigDecimal("6000.00"));
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, november30, december3))
        .containsExactly(new BigDecimal("6000.00"));
    assertThat(rerunOfTheDebitDay.managementFeeAccrual()).isEqualByComparingTo("7698.64");
  }

  @Test
  void calculateFeesForNav_releasesTwoDebitsFirstSeenTogetherEachOnItsDayAndARerunAgrees() {
    LocalDate november30 = LocalDate.of(2026, 11, 30);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate december2 = LocalDate.of(2026, 12, 2);
    calculate(TUK75, november30, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "3000.00", december1);
    recordManagementFeeDebit(TUK75, "3849.32", december2);
    calculate(TUK75, december2, BASE_VALUE);

    FeeResult rerunOfTheFirstDebitDay = calculate(TUK75, december1, BASE_VALUE);

    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december1, december1))
        .containsExactly(new BigDecimal("3000.00"));
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, december2, december2))
        .containsExactly(new BigDecimal("3849.32"));
    assertThat(rerunOfTheFirstDebitDay.managementFeeAccrual()).isEqualByComparingTo("10698.64");
  }

  @Test
  void
      calculateFeesForNav_reversesAnObsoleteReleaseWhereItWasPostedWhenALateDebitMovesAMonthsPayment() {
    LocalDate november = LocalDate.of(2026, 11, 1);
    LocalDate december1 = LocalDate.of(2026, 12, 1);
    LocalDate january4 = LocalDate.of(2027, 1, 4);
    LocalDate january5 = LocalDate.of(2027, 1, 5);
    LocalDate january8 = LocalDate.of(2027, 1, 8);
    seedManagementFeeAccrual(TUK75, LocalDate.of(2026, 11, 15), "1000.00");
    seedManagementFeeAccrual(TUK75, LocalDate.of(2026, 12, 15), "1000.00");
    seedManagementFeeAccrual(TUK75, LocalDate.of(2027, 1, 7), "10.00");
    recordManagementFeeDebit(TUK75, "1000.00", january4);
    calculate(TUK75, january8, BASE_VALUE);
    recordManagementFeeDebit(TUK75, "1000.00", december1);
    calculate(TUK75, january8, BASE_VALUE);

    calculate(TUK75, january5, BASE_VALUE);

    var novemberSettled = navFeeAccrualLedger.settlementOf(TUK75, november, MANAGEMENT_FEE_ACCRUAL);
    assertThat(novemberSettled.settledThrough(december1)).isEqualByComparingTo("1000.00");
    assertThat(novemberSettled.settledThrough(january5)).isEqualByComparingTo("1000.00");
    assertThat(novemberSettled.amount()).isEqualByComparingTo("1000.00");
    assertThat(
            navFeeAccrualLedger
                .settlementOf(TUK75, LocalDate.of(2026, 12, 1), MANAGEMENT_FEE_ACCRUAL)
                .amount())
        .isEqualByComparingTo("1000.00");
  }

  private void seedManagementFeeAccrual(TulevaFund fund, LocalDate day, String amount) {
    feeAccrualRepository.save(
        FeeAccrual.builder()
            .fund(fund)
            .feeType(MANAGEMENT)
            .accrualDate(day)
            .feeMonth(day.withDayOfMonth(1))
            .baseValue(BASE_VALUE)
            .annualRate(new BigDecimal("0.0025"))
            .dailyAmountGross(new BigDecimal(amount))
            .daysInYear(365)
            .build());
    navFeeAccrualLedger.recordFeeAccrual(
        fund, day, MANAGEMENT_FEE_ACCRUAL, new BigDecimal(amount), java.util.Map.of());
  }

  @Test
  void calculateFeesForNav_stillReleasesAMonthBeforeTheFirstOneSettledByItsDebitAtItsEnd() {
    LocalDate january31 = LocalDate.of(2025, 1, 31);
    LocalDate february3 = LocalDate.of(2025, 2, 3);
    calculate(TUK75, january31, BASE_VALUE);

    FeeResult result = calculate(TUK75, february3, BASE_VALUE);

    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("20547.95");
    assertThat(managementFeeLedgerAmounts(TUK75, FEE_SETTLEMENT, january31, january31))
        .containsExactly(new BigDecimal("6849.32"));
  }

  private void recordManagementFeeDebit(TulevaFund fund, String amount, LocalDate bookingDate) {
    fundBankLedger.recordManagementFeePayment(
        fund, new BigDecimal(amount), UUID.randomUUID(), "management fee", bookingDate);
  }

  private FeeResult calculate(TulevaFund fund, LocalDate positionReportDate, BigDecimal base) {
    return feeCalculationService.calculateFeesForNav(fund, positionReportDate, base, null);
  }

  private List<BigDecimal> managementFeeLedgerAmounts(
      TulevaFund fund, LocalDate from, LocalDate to) {
    return managementFeeLedgerAmounts(fund, FEE_ACCRUAL, from, to);
  }

  private List<BigDecimal> managementFeeLedgerAmounts(
      TulevaFund fund, TransactionType transactionType, LocalDate from, LocalDate to) {
    return navLedgerRepository
        .findEntriesByTransactionTypeBetween(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(fund),
            transactionType,
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
