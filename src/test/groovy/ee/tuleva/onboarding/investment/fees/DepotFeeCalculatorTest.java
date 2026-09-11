package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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

  private static final BigDecimal NAV_FEE_BASE = new BigDecimal("480000000");
  private static final BigDecimal ASSET_VALUE = new BigDecimal("500000000");
  private static final FeeBases BASES = new FeeBases(NAV_FEE_BASE, ASSET_VALUE);
  private static final LocalDate MAY_END = LocalDate.of(2025, 5, 31);

  @Test
  void calculate_returnsDailyFeeWithoutVat() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .willReturn(Optional.of(fixedRow(TKF100, fundRate, feeMonth)));

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

  @Test
  void calculate_chargesTheGrossAssetValueTheDepoolepingNamesAndNotTheNetNavBase() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .willReturn(Optional.of(fixedRow(TKF100, fundRate, feeMonth)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.baseValue()).isEqualByComparingTo(ASSET_VALUE);
    assertThat(result.baseValue()).isNotEqualByComparingTo(NAV_FEE_BASE);
  }

  @Test
  void calculate_usesActualDaysInYearSoALeapYearDividesBy366AndNotBy365() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);
    BigDecimal fundRate = new BigDecimal("0.01");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .willReturn(Optional.of(fixedRow(TKF100, fundRate, feeMonth)));

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

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .willReturn(Optional.of(fixedRow(TKF100, ZERO, feeMonth)));

    FeeAccrual result = calculator.calculate(TKF100, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_usesTierWhenTheRowSaysTier() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.005");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    stubEveryFundsAssets(MAY_END, new BigDecimal("350000000"));
    given(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .willReturn(Optional.of(tierRate));

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void calculate_anchorsTheTierOnMayEndForAJulyFeeMonthTwoMonthEndsBack() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.0004");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    given(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .willReturn(Optional.of(LocalDate.of(2025, 5, 30)));
    given(fundNavQueryService.findAssetTotal(anyString(), eq(LocalDate.of(2025, 5, 30))))
        .willReturn(Optional.of(new BigDecimal("250000000")));
    given(publicHolidays.nextWorkingDay(LocalDate.of(2025, 5, 30)))
        .willReturn(LocalDate.of(2025, 6, 2));
    given(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .willReturn(ZERO);
    given(tierRepository.findRateForAum(new BigDecimal("1000000000"), feeMonth))
        .willReturn(Optional.of(tierRate));

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void calculate_skipsFundsWithNoCalculationAtTheAnchor() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    given(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .willReturn(Optional.empty());
    given(tierRepository.findRateForAum(ZERO, feeMonth)).willReturn(Optional.of(ZERO));

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_accruesNothingWhenNoRateRowIsValid() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date)).willReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
    verifyNoInteractions(tierRepository);
    verifyNoInteractions(fundNavQueryService);
  }

  @Test
  void calculate_accruesNothingWhenNoTierRowCoversTheAssetsInsteadOfFailingTheNavRun() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    stubEveryFundsAssets(MAY_END, new BigDecimal("350000000"));
    given(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .willReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
    assertThat(result.baseValue()).isEqualByComparingTo(ASSET_VALUE);
  }

  @Test
  void calculate_usesTierRateAsIsWithoutAnyFloor() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tinyTierRate = new BigDecimal("0.00001");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    stubEveryFundsAssets(MAY_END, new BigDecimal("350000000"));
    given(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .willReturn(Optional.of(tinyTierRate));

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tinyTierRate);
  }

  @Test
  void calculate_resolvesTheRowPerDaySoAFeeCanStartMidMonth() {
    LocalDate feeMonth = LocalDate.of(2025, 9, 1);
    LocalDate beforeStart = LocalDate.of(2025, 9, 9);
    LocalDate onStart = LocalDate.of(2025, 9, 10);
    LocalDate julyEnd = LocalDate.of(2025, 7, 31);
    BigDecimal tierRate = new BigDecimal("0.0004");

    given(feeMonthResolver.resolveFeeMonth(beforeStart)).willReturn(feeMonth);
    given(feeMonthResolver.resolveFeeMonth(onStart)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, beforeStart))
        .willReturn(Optional.of(fixedRow(TUK75, ZERO, LocalDate.of(2025, 1, 1), beforeStart)));
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, onStart))
        .willReturn(Optional.of(tierRow(TUK75, onStart, null)));
    stubEveryFundsAssets(julyEnd, new BigDecimal("350000000"));
    given(tierRepository.findRateForAum(new BigDecimal("1400000000"), feeMonth))
        .willReturn(Optional.of(tierRate));

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

  @Test
  void calculate_addsTheSavingsFundsLedgerBlackrockAdjustmentToTheTierBasis() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal tierRate = new BigDecimal("0.0004");

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(feeMonth);
    given(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .willReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    given(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(MAY_END)))
        .willReturn(Optional.of(MAY_END));
    given(fundNavQueryService.findAssetTotal(anyString(), eq(MAY_END)))
        .willReturn(Optional.of(new BigDecimal("350000000")));
    given(publicHolidays.nextWorkingDay(MAY_END)).willReturn(MAY_END.plusDays(1));
    given(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .willReturn(new BigDecimal("1000000"));
    given(tierRepository.findRateForAum(new BigDecimal("1401000000"), feeMonth))
        .willReturn(Optional.of(tierRate));

    FeeAccrual result = calculator.calculate(TUK75, date, BASES);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void getFeeType_returnsDepot() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.DEPOT);
  }

  private void stubEveryFundsAssets(LocalDate anchor, BigDecimal perFundAssets) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), eq(anchor)))
        .willReturn(Optional.of(anchor));
    given(fundNavQueryService.findAssetTotal(anyString(), eq(anchor)))
        .willReturn(Optional.of(perFundAssets));
    given(publicHolidays.nextWorkingDay(anchor)).willReturn(anchor.plusDays(1));
    given(navLedgerRepository.getSystemAccountBalanceBefore(anyString(), any(Instant.class)))
        .willReturn(ZERO);
  }

  private FeeRate fixedRow(TulevaFund fund, BigDecimal annualRate, LocalDate validFrom) {
    return fixedRow(fund, annualRate, validFrom, null);
  }

  private FeeRate fixedRow(
      TulevaFund fund, BigDecimal annualRate, LocalDate validFrom, LocalDate validTo) {
    return new FeeRate(
        1L, fund, FeeType.DEPOT, annualRate, FeeRateSource.FIXED, validFrom, validTo);
  }

  private FeeRate tierRow(TulevaFund fund, LocalDate validFrom, LocalDate validTo) {
    return new FeeRate(1L, fund, FeeType.DEPOT, ZERO, FeeRateSource.TIER, validFrom, validTo);
  }
}
