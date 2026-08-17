package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepotFeeCalculatorTest {

  @Mock private DepotFeeTierRepository tierRepository;
  @Mock private FundNavQueryService fundNavQueryService;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private FeeRateRepository feeRateRepository;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private PublicHolidays publicHolidays;

  @InjectMocks private DepotFeeCalculator calculator;

  // Deliberately different numbers, so a test that reads the wrong one cannot pass by accident.
  private static final BigDecimal NAV_FEE_BASE = new BigDecimal("480000000");
  private static final BigDecimal ASSET_VALUE = new BigDecimal("500000000");
  private static final FeeBases BASES = new FeeBases(NAV_FEE_BASE, ASSET_VALUE);

  @Test
  void calculate_returnsDailyFeeWithoutVat() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.fund()).isEqualTo(TKF100);
    assertThat(result.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.annualRate()).isEqualTo(fundRate);
    assertThat(result.referenceDate()).isEqualTo(date);
    assertThat(result.daysInYear()).isEqualTo(365);

    BigDecimal expectedDailyGross =
        ASSET_VALUE.multiply(fundRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
  }

  /**
   * "Depootasu arvutatakse ... Fondi aktivate turuväärtuste summale" — the asset side, not the net
   * NAV base the management fee uses. Fails against the previous behaviour, which took whichever
   * single base value it was handed.
   */
  @Test
  void calculate_chargesTheAssetValueAndNotTheNetNavBase() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.baseValue()).isEqualByComparingTo(ASSET_VALUE);
    assertThat(result.baseValue()).isNotEqualByComparingTo(NAV_FEE_BASE);
  }

  /**
   * "kasutades tegeliku (aasta ja kuu) päevade arvu meetodit" — actual/actual, so a leap year
   * divides by 366. The management fee keeps its contractual 365.
   */
  @Test
  void calculate_usesActualDaysInYearSoALeapYearDividesBy366() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.daysInYear()).isEqualTo(366);

    BigDecimal expectedDailyGross =
        ASSET_VALUE.multiply(fundRate).divide(BigDecimal.valueOf(366), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
    assertThat(result.dailyAmountGross())
        .isNotEqualByComparingTo(
            ASSET_VALUE
                .multiply(fundRate)
                .divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP));
  }

  @Test
  void calculate_returnsZeroFeeWhenFundRateIsZero() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, ZERO, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_usesTierWhenTheRowSaysTier() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.005");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    stubEveryFundsAssets(MAY_END, new BigDecimal("350000000"));
    when(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .thenReturn(tierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  /**
   * The band for July is set from May's month end, not June's: the depositary submits by 10 June
   * the values from the last business day of May, for the rate applicable in July. Stubbing only
   * the May anchor means this fails outright if the calculator reaches for any other date.
   */
  @Test
  void calculate_anchorsTheTierTwoMonthEndsBackAsTheAgreementRequires() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.0004");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    when(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .thenReturn(Optional.of(LocalDate.of(2025, 5, 30)));
    when(fundNavQueryService.findAssetTotal(anyString(), eq(LocalDate.of(2025, 5, 30))))
        .thenReturn(Optional.of(new BigDecimal("250000000")));
    when(publicHolidays.nextWorkingDay(LocalDate.of(2025, 5, 30)))
        .thenReturn(LocalDate.of(2025, 6, 2));
    when(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .thenReturn(ZERO);
    when(tierRepository.findRateForAum(new BigDecimal("1000000000"), feeMonth))
        .thenReturn(tierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  /**
   * A fund with no calculation on or before the anchor contributes nothing, rather than throwing.
   */
  @Test
  void calculate_skipsFundsWithNoCalculationAtTheAnchor() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    when(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .thenReturn(Optional.empty());
    when(tierRepository.findRateForAum(ZERO, feeMonth)).thenReturn(ZERO);

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_accruesNothingWhenNoRateRowIsValid() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date)).thenReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
    verifyNoInteractions(tierRepository);
    verifyNoInteractions(fundNavQueryService);
  }

  @Test
  void calculate_usesTierRateAsIsWithoutAnyFloor() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tinyTierRate = new BigDecimal("0.00001");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    stubEveryFundsAssets(MAY_END, new BigDecimal("350000000"));
    when(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .thenReturn(tinyTierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tinyTierRate);
  }

  /**
   * The 18.09.2026 switch-over in miniature: a 0 row closed the day before, a TIER row after it.
   */
  @Test
  void calculate_resolvesTheRowPerDaySoAFeeCanStartMidMonth() {
    LocalDate feeMonth = LocalDate.of(2025, 9, 1);
    LocalDate beforeStart = LocalDate.of(2025, 9, 9);
    LocalDate onStart = LocalDate.of(2025, 9, 10);
    LocalDate julyEnd = LocalDate.of(2025, 7, 31);
    BigDecimal tierRate = new BigDecimal("0.0004");

    when(feeMonthResolver.resolveFeeMonth(beforeStart)).thenReturn(feeMonth);
    when(feeMonthResolver.resolveFeeMonth(onStart)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, beforeStart))
        .thenReturn(
            Optional.of(
                new FeeRate(
                    1L, TUK75, FeeType.DEPOT, ZERO, LocalDate.of(2025, 1, 1), beforeStart)));
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, onStart))
        .thenReturn(Optional.of(tierRow(TUK75, onStart, null)));
    stubEveryFundsAssets(julyEnd, new BigDecimal("350000000"));
    when(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .thenReturn(tierRate);

    FeeAccrual before = calculator.calculate(TUK75, beforeStart, BASES);
    FeeAccrual on = calculator.calculate(TUK75, onStart, BASES);

    assertThat(before.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(before.dailyAmountGross()).isEqualByComparingTo(ZERO);
    assertThat(before.feeMonth()).isEqualTo(feeMonth);

    assertThat(on.annualRate()).isEqualByComparingTo(tierRate);
    assertThat(on.dailyAmountGross())
        .isEqualByComparingTo(
            ASSET_VALUE
                .multiply(tierRate)
                .divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP));
    assertThat(on.feeMonth()).isEqualTo(feeMonth);
  }

  /**
   * nav_report has no BlackRock rows for a savings fund — NavReportMapper writes them only for
   * pension funds — so the tier basis has to pick that term up from the ledger, or it disagrees
   * with the daily base, which takes it from the NAV components in memory.
   */
  @Test
  void calculate_addsTheSavingsFundsBlackrockAdjustmentToTheTierBasis() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.0004");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    when(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .thenReturn(Optional.of(MAY_END));
    when(fundNavQueryService.findAssetTotal(anyString(), eq(MAY_END)))
        .thenReturn(Optional.of(new BigDecimal("350000000")));
    when(publicHolidays.nextWorkingDay(MAY_END)).thenReturn(MAY_END.plusDays(1));
    when(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .thenReturn(new BigDecimal("1000000"));

    // 4 x 350M from nav_report, plus 1M of BlackRock adjustment for TKF100 only -- the three
    // pension funds short-circuit before the ledger read.
    when(tierRepository.findRateForAum(new BigDecimal("1401000000"), feeMonth))
        .thenReturn(tierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void getFeeType_returnsDepot() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.DEPOT);
  }

  /** Two month ends back from a July fee month. */
  private static final LocalDate MAY_END = LocalDate.of(2025, 5, 31);

  /** Every fund reports the same assets at the anchor, so the band sees four times the amount. */
  private void stubEveryFundsAssets(LocalDate anchor, BigDecimal perFundAssets) {
    when(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(anchor)))
        .thenReturn(Optional.of(anchor));
    when(fundNavQueryService.findAssetTotal(anyString(), eq(anchor)))
        .thenReturn(Optional.of(perFundAssets));
    // Only TKF100 reaches the ledger; the others short-circuit on isSavingsFund().
    when(publicHolidays.nextWorkingDay(anchor)).thenReturn(anchor.plusDays(1));
    when(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .thenReturn(ZERO);
  }

  private FeeRate tierRow(TulevaFund fund, LocalDate validFrom, LocalDate validTo) {
    return new FeeRate(1L, fund, FeeType.DEPOT, ZERO, FeeRateSource.TIER, validFrom, validTo);
  }
}
