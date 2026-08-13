package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private FeeRateRepository feeRateRepository;

  @InjectMocks private DepotFeeCalculator calculator;

  @Test
  void calculate_returnsDailyFeeWithoutVat() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal fundRate = new BigDecimal("0.01");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.fund()).isEqualTo(TKF100);
    assertThat(result.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualTo(baseValue);
    assertThat(result.annualRate()).isEqualTo(fundRate);
    assertThat(result.referenceDate()).isEqualTo(date);
    assertThat(result.daysInYear()).isEqualTo(365);

    BigDecimal expectedDailyGross =
        baseValue.multiply(fundRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
  }

  @Test
  void calculate_usesCalendar365EvenInLeapYear() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);
    BigDecimal baseValue = new BigDecimal("100000000");
    BigDecimal fundRate = new BigDecimal("0.01");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.daysInYear()).isEqualTo(365);

    BigDecimal expectedDailyGross =
        baseValue.multiply(fundRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
  }

  @Test
  void calculate_returnsZeroFeeWhenFundRateIsZero() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal baseValue = new BigDecimal("500000000");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, ZERO, feeMonth, null)));

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_usesTierWhenTheRowSaysTier() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    LocalDate previousMonthEnd = LocalDate.of(2025, 6, 30);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("1400000000");
    BigDecimal tierRate = new BigDecimal("0.005");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    when(fundPositionRepository.findLatestSecurityNavDateUpTo(LocalDate.of(2025, 6, 30)))
        .thenReturn(Optional.of(previousMonthEnd));
    when(fundPositionRepository.sumSecurityMarketValueAllFunds(previousMonthEnd))
        .thenReturn(totalAum);
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, baseValue);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void calculate_accruesNothingWhenNoRateRowIsValid() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal baseValue = new BigDecimal("500000000");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date)).thenReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date, baseValue);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
    verifyNoInteractions(tierRepository);
  }

  @Test
  void calculate_usesTierRateAsIsWithoutAnyFloor() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    LocalDate previousMonthEnd = LocalDate.of(2025, 6, 30);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("1400000000");
    BigDecimal tinyTierRate = new BigDecimal("0.00001");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, date))
        .thenReturn(Optional.of(tierRow(TUK75, LocalDate.of(2025, 1, 1), null)));
    when(fundPositionRepository.findLatestSecurityNavDateUpTo(LocalDate.of(2025, 6, 30)))
        .thenReturn(Optional.of(previousMonthEnd));
    when(fundPositionRepository.sumSecurityMarketValueAllFunds(previousMonthEnd))
        .thenReturn(totalAum);
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tinyTierRate);

    FeeAccrual result = calculator.calculate(TUK75, date, baseValue);

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
    LocalDate previousMonthEnd = LocalDate.of(2025, 8, 31);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("1400000000");
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
    when(fundPositionRepository.findLatestSecurityNavDateUpTo(previousMonthEnd))
        .thenReturn(Optional.of(previousMonthEnd));
    when(fundPositionRepository.sumSecurityMarketValueAllFunds(previousMonthEnd))
        .thenReturn(totalAum);
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);

    FeeAccrual before = calculator.calculate(TUK75, beforeStart, baseValue);
    FeeAccrual on = calculator.calculate(TUK75, onStart, baseValue);

    assertThat(before.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(before.dailyAmountGross()).isEqualByComparingTo(ZERO);
    assertThat(before.feeMonth()).isEqualTo(feeMonth);

    assertThat(on.annualRate()).isEqualByComparingTo(tierRate);
    assertThat(on.dailyAmountGross())
        .isEqualByComparingTo(
            baseValue.multiply(tierRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP));
    assertThat(on.feeMonth()).isEqualTo(feeMonth);
  }

  private FeeRate tierRow(TulevaFund fund, LocalDate validFrom, LocalDate validTo) {
    return new FeeRate(1L, fund, FeeType.DEPOT, ZERO, FeeRateSource.TIER, validFrom, validTo);
  }

  @Test
  void getFeeType_returnsDepot() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.DEPOT);
  }
}
