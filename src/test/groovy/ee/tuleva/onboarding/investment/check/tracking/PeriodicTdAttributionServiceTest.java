package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeriodicTdAttributionServiceTest {

  private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 4, 30);
  private static final String FUND_CODE = "TUK75";
  private static final String ISIN_DW = "IE00BFG1TM61";
  private static final String ISIN_EM = "IE00BFNM3D14";

  @Mock TrackingDifferenceEventRepository tdEventRepository;
  @Mock FeeAccrualRepository feeAccrualRepository;
  @Mock FeeRateRepository feeRateRepository;
  @Mock FundPositionRepository fundPositionRepository;
  @Mock FundNavQueryService fundNavQueryService;
  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock PeriodicTdAttributionRepository attributionRepository;

  private PeriodicTdAttributionService service;

  @BeforeEach
  void setUp() {
    service =
        new PeriodicTdAttributionService(
            tdEventRepository,
            feeAccrualRepository,
            feeRateRepository,
            fundPositionRepository,
            fundNavQueryService,
            modelPortfolioAllocationRepository,
            attributionRepository);
  }

  @Test
  void computesAttributionAndSavesResult() {
    setupStandardMocks();

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.periodType()).isEqualTo(MONTHLY);
    assertThat(result.businessDays()).isEqualTo(2);
    assertThat(result.mgmtFeeDrag()).isNegative();
    assertThat(result.cashDrag()).isNegative();

    var componentSum =
        result
            .mgmtFeeDrag()
            .add(result.depotFeeDrag())
            .add(result.cashDrag())
            .add(result.nonSecurityDrag())
            .add(result.weightDeviation())
            .add(result.transactionCosts())
            .add(result.residual());
    assertThat(componentSum).isCloseTo(result.tdGeometric(), within(new BigDecimal("0.00000001")));

    verify(attributionRepository)
        .deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
            TUK75, PERIOD_START, PERIOD_END, MONTHLY);
    verify(attributionRepository).save(any(PeriodicTdAttribution.class));
  }

  @Test
  void rerunDeletesBeforeInserting() {
    setupStandardMocks();

    service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);
    service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    verify(attributionRepository, times(2))
        .deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
            TUK75, PERIOD_START, PERIOD_END, MONTHLY);
    verify(attributionRepository, times(2)).save(any(PeriodicTdAttribution.class));
  }

  @Test
  void handlesEmptyTdEvents() {
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.tdGeometric()).isEqualByComparingTo(ZERO);
    assertThat(result.businessDays()).isZero();
  }

  @Test
  void computeForAllFundsIsolatesErrors() {
    setupStandardMocks();

    // All non-TUK75 funds return empty events (no data to process)
    for (var fund : ee.tuleva.onboarding.fund.TulevaFund.values()) {
      if (fund == TUK75) continue;
      if (fund == TUK00) {
        given(tdEventRepository.findDeduplicatedEventsForPeriod(eq(fund), any(), any(), any()))
            .willThrow(new RuntimeException("DB error for " + fund));
      } else {
        given(tdEventRepository.findDeduplicatedEventsForPeriod(eq(fund), any(), any(), any()))
            .willReturn(List.of());
      }
      given(feeAccrualRepository.findByFundAndDateRange(eq(fund), any(), any()))
          .willReturn(List.of());
      given(feeRateRepository.findValidRate(eq(fund), any(), any())).willReturn(Optional.empty());
      given(
              modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                  eq(fund), any(), any()))
          .willReturn(List.of());
    }

    service.computeForAllFunds(PERIOD_START, PERIOD_END, MONTHLY);

    // TUK00 failed but TUK75 (and others) still saved
    verify(attributionRepository, times(TulevaFund.values().length - 1))
        .save(any(PeriodicTdAttribution.class));
  }

  @Test
  void handlesMissingAumGracefully() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.001", "0.0012")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(ZERO);

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.businessDays()).isEqualTo(1);
    assertThat(result.avgAum()).isEqualByComparingTo(ZERO);
  }

  @Test
  void handlesEmptySecurityAttributions() {
    var date1 = LocalDate.of(2026, 4, 1);
    var eventNoAttrs =
        TrackingDifferenceEvent.builder()
            .fund(TUK75)
            .checkDate(date1)
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("-0.0002"))
            .fundReturn(new BigDecimal("0.0008"))
            .benchmarkReturn(new BigDecimal("0.001"))
            .breach(false)
            .result(
                Map.of(
                    "securityAttributions",
                    List.of(),
                    "cashDrag",
                    ZERO,
                    "feeDrag",
                    ZERO,
                    "residual",
                    ZERO))
            .createdAt(Instant.now())
            .build();

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(eventNoAttrs));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1000000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("99000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.instrumentDetails()).isEmpty();
    assertThat(result.weightDeviation()).isEqualByComparingTo(ZERO);
  }

  @Test
  void handlesZeroSecurityValue() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.001", "0.0012")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any())).willReturn(ZERO);
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of());

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.instrumentDetails()).isEmpty();
  }

  @Test
  void handlesDepotFeeAccruals() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.0008", "0.001")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                feeAccrual(date1, FeeType.MANAGEMENT, "27.40"),
                FeeAccrual.builder()
                    .fund(TUK75)
                    .feeType(FeeType.DEPOT)
                    .accrualDate(date1)
                    .feeMonth(date1.withDayOfMonth(1))
                    .baseValue(new BigDecimal("100000000"))
                    .annualRate(new BigDecimal("0.0003"))
                    .dailyAmountNet(new BigDecimal("6.85"))
                    .dailyAmountGross(new BigDecimal("8.36"))
                    .daysInYear(365)
                    .build()));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, TUK75, FeeType.MANAGEMENT, new BigDecimal("0.0027"), PERIOD_START, null)));
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1), modelAllocation(ISIN_EM, "0.30", date1)));
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EM, "29400000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.depotFeeDrag()).isNegative();
    assertThat(result.mgmtFeeDrag()).isNegative();
  }

  @Test
  void toBigDecimalHandlesVariousTypes() {
    assertThat(PeriodicTdAttributionService.toBigDecimal(new BigDecimal("1.23")))
        .isEqualByComparingTo(new BigDecimal("1.23"));
    assertThat(PeriodicTdAttributionService.toBigDecimal(42))
        .isEqualByComparingTo(new BigDecimal("42"));
    assertThat(PeriodicTdAttributionService.toBigDecimal(1.5))
        .isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(PeriodicTdAttributionService.toBigDecimal("0.00123"))
        .isEqualByComparingTo(new BigDecimal("0.00123"));
    assertThat(PeriodicTdAttributionService.toBigDecimal(null)).isEqualByComparingTo(ZERO);
  }

  // --- shared setup ---

  private void setupStandardMocks() {
    var date1 = LocalDate.of(2026, 4, 1);
    var date2 = LocalDate.of(2026, 4, 2);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.0008", "0.001"), tdEvent(date2, "0.0005", "0.0007")));

    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                feeAccrual(date1, FeeType.MANAGEMENT, "27.40"),
                feeAccrual(date2, FeeType.MANAGEMENT, "27.40")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, TUK75, FeeType.MANAGEMENT, new BigDecimal("0.0027"), PERIOD_START, null)));

    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1), modelAllocation(ISIN_EM, "0.30", date1)));

    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findAum(FUND_CODE, date2)).willReturn(new BigDecimal("100050000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EM, "29400000")));
  }

  // --- helpers ---

  private TrackingDifferenceEvent tdEvent(
      LocalDate date, String fundReturn, String benchmarkReturn) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal(fundReturn).subtract(new BigDecimal(benchmarkReturn)))
        .fundReturn(new BigDecimal(fundReturn))
        .benchmarkReturn(new BigDecimal(benchmarkReturn))
        .breach(false)
        .result(
            Map.of(
                "securityAttributions",
                List.of(
                    Map.<String, Object>of(
                        "isin", ISIN_DW,
                        "modelWeight", new BigDecimal("0.70"),
                        "actualWeight", new BigDecimal("0.68"),
                        "securityReturn", new BigDecimal(fundReturn)),
                    Map.<String, Object>of(
                        "isin", ISIN_EM,
                        "modelWeight", new BigDecimal("0.30"),
                        "actualWeight", new BigDecimal("0.30"),
                        "securityReturn", new BigDecimal(benchmarkReturn))),
                "cashDrag",
                ZERO,
                "feeDrag",
                ZERO,
                "residual",
                ZERO))
        .createdAt(Instant.now())
        .build();
  }

  private FeeAccrual feeAccrual(LocalDate date, FeeType type, String amount) {
    return FeeAccrual.builder()
        .fund(TUK75)
        .feeType(type)
        .accrualDate(date)
        .feeMonth(date.withDayOfMonth(1))
        .baseValue(new BigDecimal("100000000"))
        .annualRate(new BigDecimal("0.0027"))
        .dailyAmountNet(new BigDecimal(amount))
        .dailyAmountGross(new BigDecimal(amount))
        .daysInYear(365)
        .build();
  }

  private ModelPortfolioAllocation modelAllocation(
      String isin, String weight, LocalDate effectiveDate) {
    return ModelPortfolioAllocation.builder()
        .fund(TUK75)
        .isin(isin)
        .weight(new BigDecimal(weight))
        .effectiveDate(effectiveDate)
        .build();
  }

  private FundPosition position(String isin, String marketValue) {
    return FundPosition.builder()
        .fund(TUK75)
        .accountType(SECURITY)
        .accountId(isin)
        .accountName(isin)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }

  @Test
  void blendsModelWeightsDuringTransitionInPeriodicAttribution() {
    var date1 = LocalDate.of(2026, 4, 1);
    var date2 = LocalDate.of(2026, 4, 2);

    var isinNew = "IE00NEW";

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                tdEvent(date1, "0.0008", "0.001"),
                tdEventWithTransition(date2, "0.0005", "0.0007", isinNew)));

    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                feeAccrual(date1, FeeType.MANAGEMENT, "27.40"),
                feeAccrual(date2, FeeType.MANAGEMENT, "27.40")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, TUK75, FeeType.MANAGEMENT, new BigDecimal("0.0027"), PERIOD_START, null)));

    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EM, "0.30", date1),
                modelAllocation(ISIN_DW, "0.70", date2),
                modelAllocation(isinNew, "0.30", date2)));

    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findAum(FUND_CODE, date2)).willReturn(new BigDecimal("100050000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));

    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                eq(date1), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EM, "29400000")));

    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                eq(date2), eq(TUK75), eq(SECURITY)))
        .willReturn(
            List.of(
                position(ISIN_DW, "68600000"),
                position(ISIN_EM, "20000000"),
                position(isinNew, "10000000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    var emDetail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(ISIN_EM))
            .findFirst()
            .orElseThrow();
    var newDetail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(isinNew))
            .findFirst()
            .orElseThrow();

    assertThat(emDetail.weightDevContribution().abs()).isLessThan(new BigDecimal("0.001"));
    assertThat(newDetail.weightDevContribution().abs()).isLessThan(new BigDecimal("0.001"));
  }

  private TrackingDifferenceEvent tdEventWithTransition(
      LocalDate date, String fundReturn, String benchmarkReturn, String newIsin) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal(fundReturn).subtract(new BigDecimal(benchmarkReturn)))
        .fundReturn(new BigDecimal(fundReturn))
        .benchmarkReturn(new BigDecimal(benchmarkReturn))
        .breach(false)
        .result(
            Map.of(
                "securityAttributions",
                List.of(
                    Map.<String, Object>of(
                        "isin", ISIN_DW,
                        "modelWeight", new BigDecimal("0.70"),
                        "actualWeight", new BigDecimal("0.70"),
                        "securityReturn", new BigDecimal("0.001")),
                    Map.<String, Object>of(
                        "isin",
                        ISIN_EM,
                        "modelWeight",
                        ZERO,
                        "actualWeight",
                        new BigDecimal("0.20"),
                        "securityReturn",
                        new BigDecimal("0.0005")),
                    Map.<String, Object>of(
                        "isin", newIsin,
                        "modelWeight", new BigDecimal("0.30"),
                        "actualWeight", new BigDecimal("0.10"),
                        "securityReturn", new BigDecimal("0.002"))),
                "cashDrag",
                ZERO,
                "feeDrag",
                ZERO,
                "residual",
                ZERO))
        .createdAt(Instant.now())
        .build();
  }
}
