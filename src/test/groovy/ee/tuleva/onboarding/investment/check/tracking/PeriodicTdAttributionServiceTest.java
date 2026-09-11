package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.instrumentReferenceService;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.BenchmarkCategoryProxy;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeRateSource;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeriodicTdAttributionServiceTest {

  private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 4, 30);
  private static final String FUND_CODE = "TUK75";
  private static final String ISIN_DW = "IE00BFG1TM61";
  private static final String EUNL_ISIN = "IE00B4L5Y983";
  private static final String ISIN_EUROPE_ETF = "IE00BFNM3D14";
  private static final BigDecimal BENCHMARK_MODEL_SUM = new BigDecimal("-0.00025");

  @Mock TrackingDifferenceEventRepository tdEventRepository;
  @Mock FeeAccrualRepository feeAccrualRepository;
  @Mock FeeRateRepository feeRateRepository;
  @Mock FeeChargedToFundPolicy feeChargedToFundPolicy;
  @Mock FundPositionRepository fundPositionRepository;
  @Mock FundNavQueryService fundNavQueryService;
  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock PeriodicTdAttributionRepository attributionRepository;
  @Mock TransactionExecutionRepository transactionExecutionRepository;
  @Mock InstrumentFeeRepository instrumentFeeRepository;
  @Mock PlatformTransactionManager transactionManager;

  private PeriodicTdAttributionService service;

  @Mock private InvestmentParameterRepository parameterRepository;
  @Mock private TrackingDifferenceNotifier notifier;

  @BeforeEach
  void setUp() {
    service =
        new PeriodicTdAttributionService(
            tdEventRepository,
            feeAccrualRepository,
            feeRateRepository,
            feeChargedToFundPolicy,
            new TdAttributionInputAssembler(fundPositionRepository, fundNavQueryService),
            modelPortfolioAllocationRepository,
            attributionRepository,
            transactionExecutionRepository,
            instrumentFeeRepository,
            transactionManager,
            new PublicHolidays(),
            new BenchmarkLegResolver(trackedInstruments()),
            parameterRepository,
            notifier);

    // Default lenient stubs for Phase 3 data sources (overridden in specific tests)
    given(transactionExecutionRepository.sumCommissionsForFundAndPeriod(anyString(), any(), any()))
        .willReturn(ZERO);
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                any(), eq(TrackingCheckType.BENCHMARK_MODEL), any(), any()))
        .willReturn(List.of());
    given(instrumentFeeRepository.findAllValidRates(any())).willReturn(List.of());
    given(feeChargedToFundPolicy.resolverFor(any(), any()))
        .willAnswer(call -> alwaysCharged(call.getArgument(1), true));
  }

  private static InstrumentReferenceService trackedInstruments() {
    return instrumentReferenceService(
        List.of(
            tracked(ISIN_DW, "IE00BFG1TM61.EUFUND", "EQUITY_DM"),
            tracked(ISIN_EUROPE_ETF, "SLMC.XETRA", "EQUITY_DM"),
            tracked(EUNL_ISIN, "EUNL.XETRA", null)),
        List.of(new BenchmarkCategoryProxy(1L, "EQUITY_DM", EUNL_ISIN, null, "MSCI_WORLD")));
  }

  private static InstrumentReference tracked(
      String isin, String eodhdTicker, String benchmarkCategory) {
    return anInstrument()
        .isin(isin)
        .displayName(isin)
        .eodhdTicker(eodhdTicker)
        .benchmarkCategory(benchmarkCategory)
        .active(true)
        .build();
  }

  private FeeChargedToFundPolicy.Resolver alwaysCharged(FeeType feeType, boolean chargedToFund) {
    return new FeeChargedToFundPolicy.Resolver(
        TUK75,
        feeType,
        List.of(
            new FeeChargedToFundPolicy.Policy(
                chargedToFund, LocalDate.of(2017, 3, 28), (LocalDate) null)));
  }

  @Test
  void computesAttributionAndSavesResult() {
    setupStandardMocks();

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.periodType()).isEqualTo(MONTHLY);
    assertThat(result.navEventCount()).isEqualTo(2);
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
    verify(attributionRepository).save(argThat(e -> e.getDetails().size() == 2));
  }

  @Test
  void aResidualTheAttributionCouldNotExplainIsAlerted() {
    setupStandardMocks();
    given(
            parameterRepository.findLatestValue(
                InvestmentParameter.TD_RESIDUAL_TOLERANCE_ANNUAL, PERIOD_END))
        .willReturn(new BigDecimal("0.00001"));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks()).containsEntry("residualWithinTolerance", false);
    then(notifier)
        .should()
        .notifyResidualOutsideTolerance(
            eq(TUK75), eq(PERIOD_START), eq(PERIOD_END), eq(result.residual()), any());
  }

  @Test
  void anUnconfiguredToleranceIsNotAnAlarmOnEveryPeriod() {
    setupStandardMocks();
    given(
            parameterRepository.findLatestValue(
                InvestmentParameter.TD_RESIDUAL_TOLERANCE_ANNUAL, PERIOD_END))
        .willThrow(new IllegalStateException("No investment parameter found"));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks()).doesNotContainKey("residualWithinTolerance");
    then(notifier)
        .should(never())
        .notifyResidualOutsideTolerance(any(), any(), any(), any(), any());
  }

  @Test
  void anEventWrittenBeforeTheDailyCheckStoredItsWeightsIsLeftOutOfThePeriodDetail() {
    setupStandardMocks();
    var legacyEvent =
        TrackingDifferenceEvent.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 1))
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.0001"))
            .fundReturn(new BigDecimal("0.001"))
            .benchmarkReturn(new BigDecimal("0.0009"))
            .breach(false)
            .result(
                Map.of(
                    "securityAttributions",
                    List.of(
                        Map.<String, Object>of(
                            "isin", ISIN_DW, "securityReturn", new BigDecimal("0.001")))))
            .createdAt(Instant.now())
            .build();
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(legacyEvent));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    // Reading a missing weight as zero would report a model that never held the instrument.
    // Leaving it out keeps its whole effect in the residual, where the tolerance can see it.
    assertThat(result.instrumentDetails()).isEmpty();
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
    assertThat(result.navEventCount()).isZero();
  }

  @Test
  void computesAttributionWhenDailyTdSeriesHasAGap() {
    // Events on Tue 2026-04-14 and Thu 2026-04-16 skip the working day Wed 2026-04-15, so the
    // daily series has a gap; the period attribution still computes (gap is surfaced, not fatal).
    var date1 = LocalDate.of(2026, 4, 14);
    var date2 = LocalDate.of(2026, 4, 16);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.0008", "0.001"), tdEvent(date2, "0.0005", "0.0007")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", date1)));
    given(fundNavQueryService.findAum(anyString(), any())).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.navEventCount()).isEqualTo(2);
    verify(attributionRepository).save(any(PeriodicTdAttribution.class));
  }

  @Test
  void computeForAllFundsIsolatesErrors() {
    setupStandardMocks();

    // All non-TUK75 funds return empty events (no data to process)
    for (var fund : ee.tuleva.onboarding.tulevafund.TulevaFund.values()) {
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

    assertThat(result.navEventCount()).isEqualTo(1);
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
                feeAccrual(date1, FeeType.DEPOT, "6.85")));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L,
                    TUK75,
                    FeeType.MANAGEMENT,
                    new BigDecimal("0.0027"),
                    FeeRateSource.FIXED,
                    PERIOD_START,
                    null)));
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", date1)));
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.depotFeeDrag()).isNegative();
    assertThat(result.mgmtFeeDrag()).isNegative();
    assertThat(result.mgmtFeeDrag()).isEqualByComparingTo(new BigDecimal("-0.00000027"));
  }

  @Test
  void reportsNoDepotFeeDragWhenTheFeeIsExcludedFromNav() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(feeChargedToFundPolicy.resolverFor(TUK75, FeeType.DEPOT))
        .willReturn(alwaysCharged(FeeType.DEPOT, false));
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.0008", "0.001")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                feeAccrual(date1, FeeType.MANAGEMENT, "27.40"),
                feeAccrual(date1, FeeType.DEPOT, "6.85")));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L,
                    TUK75,
                    FeeType.MANAGEMENT,
                    new BigDecimal("0.0027"),
                    FeeRateSource.FIXED,
                    PERIOD_START,
                    null)));
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", date1)));
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.depotFeeDrag()).isEqualByComparingTo(ZERO);
    assertThat(result.mgmtFeeDrag()).isNegative();
  }

  @Test
  void countsDepotFeeDragOnlyForTheDaysTheFundActuallyBoreIt() {
    var chargedDay = LocalDate.of(2026, 4, 10);
    var uncharged = LocalDate.of(2026, 4, 20);

    given(feeChargedToFundPolicy.resolverFor(TUK75, FeeType.DEPOT))
        .willReturn(
            new FeeChargedToFundPolicy.Resolver(
                TUK75,
                FeeType.DEPOT,
                List.of(
                    new FeeChargedToFundPolicy.Policy(
                        true, LocalDate.of(2017, 3, 28), LocalDate.of(2026, 4, 15)),
                    new FeeChargedToFundPolicy.Policy(
                        false, LocalDate.of(2026, 4, 16), (LocalDate) null))));
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(chargedDay, "0.0008", "0.001")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                feeAccrual(chargedDay, FeeType.DEPOT, "6.85"),
                feeAccrual(uncharged, FeeType.DEPOT, "6.85")));
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", chargedDay),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", chargedDay)));
    given(fundNavQueryService.findAum(FUND_CODE, chargedDay))
        .willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.depotFeeDrag()).isEqualByComparingTo(new BigDecimal("-0.00000007"));
  }

  @Test
  void toBigDecimalHandlesVariousTypes() {
    assertThat(TdAttributionInputAssembler.toBigDecimal(new BigDecimal("1.23")))
        .isEqualByComparingTo(new BigDecimal("1.23"));
    assertThat(TdAttributionInputAssembler.toBigDecimal(42))
        .isEqualByComparingTo(new BigDecimal("42"));
    assertThat(TdAttributionInputAssembler.toBigDecimal(1.5))
        .isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(TdAttributionInputAssembler.toBigDecimal("0.00123"))
        .isEqualByComparingTo(new BigDecimal("0.00123"));
    assertThat(TdAttributionInputAssembler.toBigDecimal(null)).isEqualByComparingTo(ZERO);
  }

  @Test
  void backfillMonthsComputesMultipleMonths() {
    var clock =
        Clock.fixed(
            LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(),
            ZoneId.of("Europe/Tallinn"));

    for (var fund : TulevaFund.values()) {
      given(tdEventRepository.findDeduplicatedEventsForPeriod(eq(fund), any(), any(), any()))
          .willReturn(List.of());
      given(feeAccrualRepository.findByFundAndDateRange(eq(fund), any(), any()))
          .willReturn(List.of());
      given(feeRateRepository.findValidRate(eq(fund), any(), any())).willReturn(Optional.empty());
      given(
              modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                  eq(fund), any(), any()))
          .willReturn(List.of());
    }

    service.backfillMonths(3, clock);

    // 3 months * 4 funds = 12 attribution saves
    verify(attributionRepository, times(3 * TulevaFund.values().length))
        .save(any(PeriodicTdAttribution.class));
  }

  @Test
  void computeQuarterlyDelegatesToComputeAttribution() {
    for (var fund : TulevaFund.values()) {
      given(tdEventRepository.findDeduplicatedEventsForPeriod(eq(fund), any(), any(), any()))
          .willReturn(List.of());
      given(feeAccrualRepository.findByFundAndDateRange(eq(fund), any(), any()))
          .willReturn(List.of());
      given(feeRateRepository.findValidRate(eq(fund), any(), any())).willReturn(Optional.empty());
      given(
              modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                  eq(fund), any(), any()))
          .willReturn(List.of());
    }

    service.computeQuarterly(TUK75, 2026, 2);

    verify(attributionRepository)
        .deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
            TUK75, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), PeriodType.QUARTERLY);
  }

  @Test
  void computeAnnualDelegatesToComputeAttribution() {
    for (var fund : TulevaFund.values()) {
      given(tdEventRepository.findDeduplicatedEventsForPeriod(eq(fund), any(), any(), any()))
          .willReturn(List.of());
      given(feeAccrualRepository.findByFundAndDateRange(eq(fund), any(), any()))
          .willReturn(List.of());
      given(feeRateRepository.findValidRate(eq(fund), any(), any())).willReturn(Optional.empty());
      given(
              modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                  eq(fund), any(), any()))
          .willReturn(List.of());
    }

    service.computeAnnual(TUK75, 2026);

    verify(attributionRepository)
        .deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
            TUK75, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), PeriodType.ANNUAL);
  }

  @Test
  void computeWeightedOcfUsesInstrumentFeeRates() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(List.of(instrumentFee(ISIN_DW, "0.0012")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.etfOcfDrag()).isNegative();
  }

  @Test
  void anEtfLayerWithNoMeasuredDayIsReportedAsZeroRatherThanAsOutperformance() {
    setupStandardMocks();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(List.of(instrumentFee(ISIN_DW, "0.0012")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.etfOcfDrag()).isEqualByComparingTo(ZERO);
    assertThat(result.etfTrackingResidual()).isEqualByComparingTo(ZERO);
    assertThat(result.tdVsBenchmark()).isEqualByComparingTo(result.tdGeometric());
    assertThat(result.checks()).containsEntry("etfLayerMeasured", false);
  }

  @Test
  void aProxyBenchmarkedHoldingHasTheProxyOwnOcfRestoredIntoTdVsBenchmark() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));
    var withoutProxyRate = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(
                instrumentFee(ISIN_DW, "0.0012"),
                instrumentFee(ISIN_EUROPE_ETF, "0.0020"),
                instrumentFee(EUNL_ISIN, "0.0020")));
    var withProxyRate = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(withProxyRate.tdVsBenchmark()).isLessThan(withoutProxyRate.tdVsBenchmark());
    assertThat(withProxyRate.etfOcfDrag()).isEqualByComparingTo(withoutProxyRate.etfOcfDrag());
    assertThat(withProxyRate.etfOcfDrag().add(withProxyRate.etfTrackingResidual()))
        .isEqualByComparingTo(withProxyRate.tdVsBenchmark().subtract(withProxyRate.tdGeometric()));
    assertThat(withoutProxyRate.tdVsBenchmark().subtract(withoutProxyRate.tdGeometric()))
        .isEqualByComparingTo(BENCHMARK_MODEL_SUM);
  }

  @Test
  void aProxyWithNoInstrumentFeeRowRecordsTheWeightItCouldNotRestore() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks())
        .containsEntry("etfLayerUnrestoredProxyWeight", new BigDecimal("0.300000"));
  }

  @Test
  void aProxyWithAnInstrumentFeeRowLeavesNoUnrestoredWeight() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(
                instrumentFee(ISIN_DW, "0.0012"),
                instrumentFee(ISIN_EUROPE_ETF, "0.0020"),
                instrumentFee(EUNL_ISIN, "0.0020")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks())
        .containsEntry("etfLayerUnrestoredProxyWeight", new BigDecimal("0.000000"));
  }

  @Test
  void modelWeightWithNoBenchmarkLegIsRecordedAsOutsideTheMeasuredLayer() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", PERIOD_START),
                modelAllocation("IE00NOTATRACKER", "0.30", PERIOD_START)));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks())
        .containsEntry("etfLayerUnbenchmarkedWeight", new BigDecimal("0.300000"));
  }

  @Test
  void anUnbenchmarkedHoldingsOwnOcfStaysOutOfTheEtfOcfDrag() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", PERIOD_START),
                modelAllocation("IE00NOTATRACKER", "0.30", PERIOD_START)));

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(List.of(instrumentFee(ISIN_DW, "0.0012")));
    var withoutRateForTheUnbenchmarkedHolding =
        service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee("IE00NOTATRACKER", "0.0090")));
    var withRateForTheUnbenchmarkedHolding =
        service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(withRateForTheUnbenchmarkedHolding.etfOcfDrag())
        .isEqualByComparingTo(withoutRateForTheUnbenchmarkedHolding.etfOcfDrag());
    assertThat(withRateForTheUnbenchmarkedHolding.etfTrackingResidual())
        .isEqualByComparingTo(withoutRateForTheUnbenchmarkedHolding.etfTrackingResidual());
  }

  @Test
  void aHoldingTheDailyCheckDroppedForAMissingPriceKeepsItsOcfOutOfTheEtfLayer() {
    setupStandardMocks();
    givenBenchmarkModelEventsMeasuring(List.of(ISIN_DW));

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(List.of(instrumentFee(ISIN_DW, "0.0012")));
    var withoutRateForTheDroppedHolding =
        service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));
    var withRateForTheDroppedHolding =
        service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(withRateForTheDroppedHolding.etfOcfDrag())
        .isEqualByComparingTo(withoutRateForTheDroppedHolding.etfOcfDrag());
    assertThat(withRateForTheDroppedHolding.checks())
        .containsEntry("etfLayerUnbenchmarkedWeight", new BigDecimal("0.300000"));
  }

  @Test
  void aBenchmarkModelEventWithoutSecurityAttributionsLeavesTheEtfOcfUncomputed() {
    setupStandardMocks();
    givenBenchmarkModelEventsMeasuring(List.of());
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.etfOcfDrag()).isEqualByComparingTo(ZERO);
    assertThat(result.checks())
        .containsEntry("etfLayerUnbenchmarkedWeight", new BigDecimal("1.000000"));
  }

  private void givenBenchmarkModelEvents() {
    givenBenchmarkModelEventsMeasuring(List.of(ISIN_DW, ISIN_EUROPE_ETF));
  }

  private void givenBenchmarkModelEventsMeasuring(List<String> measuredIsins) {
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, TrackingCheckType.BENCHMARK_MODEL, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                benchmarkModelEvent(LocalDate.of(2026, 4, 1), "-0.00010", measuredIsins),
                benchmarkModelEvent(LocalDate.of(2026, 4, 2), "-0.00015", measuredIsins)));
  }

  private TrackingDifferenceEvent benchmarkModelEvent(
      LocalDate date, String trackingDifference, List<String> measuredIsins) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(TrackingCheckType.BENCHMARK_MODEL)
        .trackingDifference(new BigDecimal(trackingDifference))
        .fundReturn(ZERO)
        .benchmarkReturn(ZERO)
        .breach(false)
        .result(
            Map.of(
                "securityAttributions",
                measuredIsins.stream().map(isin -> attribution(isin, "0.5")).toList()))
        .build();
  }

  private ee.tuleva.onboarding.investment.fees.InstrumentFee instrumentFee(
      String isin, String netOcf) {
    return ee.tuleva.onboarding.investment.fees.InstrumentFee.builder()
        .isin(isin)
        .instrumentName(isin)
        .netOcf(new BigDecimal(netOcf))
        .publishedOcf(new BigDecimal(netOcf))
        .rebateRate(ZERO)
        .validFrom(LocalDate.of(2025, 1, 1))
        .build();
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
                    1L,
                    TUK75,
                    FeeType.MANAGEMENT,
                    new BigDecimal("0.0027"),
                    FeeRateSource.FIXED,
                    PERIOD_START,
                    null)));

    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", date1)));

    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findAum(FUND_CODE, date2)).willReturn(new BigDecimal("100050000"));
    given(fundNavQueryService.findCashValue(anyString(), any()))
        .willReturn(new BigDecimal("1500000"));
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("98000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any()))
        .willReturn(new BigDecimal("-50000"));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));
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
                        "weightDifference", new BigDecimal("-0.006122"),
                        "securityReturn", new BigDecimal(fundReturn)),
                    Map.<String, Object>of(
                        "isin", ISIN_EUROPE_ETF,
                        "modelWeight", new BigDecimal("0.30"),
                        "actualWeight", new BigDecimal("0.30"),
                        "weightDifference", new BigDecimal("0.006122"),
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

  private TrackingDifferenceEvent singleAttributionEvent(LocalDate date, String isin) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.0001"))
        .fundReturn(new BigDecimal("0.001"))
        .benchmarkReturn(new BigDecimal("0.0009"))
        .breach(false)
        .result(
            Map.of(
                "securityAttributions",
                List.of(
                    Map.<String, Object>of(
                        "isin",
                        isin,
                        "securityReturn",
                        new BigDecimal("0.001"),
                        "modelWeight",
                        BigDecimal.ONE,
                        "actualWeight",
                        BigDecimal.ONE,
                        "weightDifference",
                        ZERO))))
        .createdAt(Instant.now())
        .build();
  }

  // What the daily check actually stores. A fixture holding only isin and securityReturn is a
  // pre-2026-05-06 event, which the period deliberately cannot reproduce.
  private static Map<String, Object> attribution(String isin, String weight) {
    return Map.<String, Object>of(
        "isin",
        isin,
        "securityReturn",
        new BigDecimal("0.001"),
        "modelWeight",
        new BigDecimal(weight),
        "actualWeight",
        new BigDecimal(weight),
        "weightDifference",
        ZERO);
  }

  private FeeAccrual feeAccrual(LocalDate date, FeeType type, String amount) {
    return FeeAccrual.builder()
        .fund(TUK75)
        .feeType(type)
        .accrualDate(date)
        .feeMonth(date.withDayOfMonth(1))
        .baseValue(new BigDecimal("100000000"))
        .annualRate(new BigDecimal("0.0027"))
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

  private FundPosition positionNamed(String isin, String accountName) {
    return FundPosition.builder()
        .fund(TUK75)
        .accountType(SECURITY)
        .accountId(isin)
        .accountName(accountName)
        .marketValue(new BigDecimal("1000000"))
        .build();
  }

  @Test
  void reportsNoWeightDeviationForTheLegsTheDailyCheckBlended() {
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
                    1L,
                    TUK75,
                    FeeType.MANAGEMENT,
                    new BigDecimal("0.0027"),
                    FeeRateSource.FIXED,
                    PERIOD_START,
                    null)));

    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", date1),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", date1),
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
        .willReturn(List.of(position(ISIN_DW, "68600000"), position(ISIN_EUROPE_ETF, "29400000")));

    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                eq(date2), eq(TUK75), eq(SECURITY)))
        .willReturn(
            List.of(
                position(ISIN_DW, "68600000"),
                position(ISIN_EUROPE_ETF, "20000000"),
                position(isinNew, "10000000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    var emDetail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(ISIN_EUROPE_ETF))
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

  @Test
  void reportsTheModelWeightTheDailyCheckStored() {
    setupStandardMocks();

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    var dwDetail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(ISIN_DW))
            .findFirst()
            .orElseThrow();
    assertThat(dwDetail.modelWeight()).isEqualByComparingTo(new BigDecimal("0.70"));
  }

  @Test
  void aHeldPositionTheDailyCheckDidNotAttributeStaysOutOfThePeriodDetail() {
    setupStandardMocks();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(
            List.of(
                position(ISIN_DW, "68600000"),
                position(ISIN_EUROPE_ETF, "29400000"),
                position("IE00ROGUE", "5000000")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.instrumentDetails())
        .extracting(TdAttributionResult.InstrumentAttribution::isin)
        .containsExactlyInAnyOrder(ISIN_DW, ISIN_EUROPE_ETF);
  }

  // A transition day as the daily check stores it: the legs moving have their model weight blended
  // to what the fund actually holds, and the rest of the model is rescaled back to 1.
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
                        "weightDifference", ZERO,
                        "securityReturn", new BigDecimal("0.001")),
                    Map.<String, Object>of(
                        "isin", ISIN_EUROPE_ETF,
                        "modelWeight", new BigDecimal("0.20"),
                        "actualWeight", new BigDecimal("0.20"),
                        "weightDifference", ZERO,
                        "securityReturn", new BigDecimal("0.0005")),
                    Map.<String, Object>of(
                        "isin", newIsin,
                        "modelWeight", new BigDecimal("0.10"),
                        "actualWeight", new BigDecimal("0.10"),
                        "weightDifference", ZERO,
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

  @Test
  void countSeriesGapsIgnoresWeekendsButCountsMissingWorkingDays() {
    var holidays = new PublicHolidays();

    // Mon, Tue, Wed — unbroken chain.
    assertThat(
            TdAttributionInputAssembler.countSeriesGaps(
                List.of(
                    LocalDate.of(2026, 6, 15),
                    LocalDate.of(2026, 6, 16),
                    LocalDate.of(2026, 6, 17)),
                holidays))
        .isZero();

    // Fri -> Mon spans a weekend, which is not a gap.
    assertThat(
            TdAttributionInputAssembler.countSeriesGaps(
                List.of(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 15)), holidays))
        .isZero();

    // Mon -> Wed skips Tue (a working day) — one gap that breaks geometric telescoping.
    assertThat(
            TdAttributionInputAssembler.countSeriesGaps(
                List.of(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17)), holidays))
        .isEqualTo(1);
  }

  @Test
  void feeXcheckReflectsTheExactCalendarDaysAndAnnualFeeRate() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0.001", "0.0012")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of(feeAccrual(date1, FeeType.MANAGEMENT, "100.00")));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L,
                    TUK75,
                    FeeType.MANAGEMENT,
                    new BigDecimal("0.0365"),
                    FeeRateSource.FIXED,
                    PERIOD_START,
                    null)));
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findCashValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any())).willReturn(ZERO);

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.checks()).containsEntry("feeXcheck", new BigDecimal("0.00299900"));
  }

  @Test
  void attributionForIsinWithNoMatchingPositionFallsBackToTheIsinAsItsName() {
    var date1 = LocalDate.of(2026, 4, 1);
    var isinNoPosition = "IE00NOPOS";

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(singleAttributionEvent(date1, isinNoPosition)));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("1000000"));
    given(fundNavQueryService.findCashValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("1000000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any())).willReturn(ZERO);
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(date1, TUK75, SECURITY))
        .willReturn(List.of());

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    var detail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(isinNoPosition))
            .findFirst()
            .orElseThrow();
    assertThat(detail.avgActualWeight()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(detail.instrumentName()).isEqualTo(isinNoPosition);
  }

  @Test
  void computeAvgAumOnlyCountsPositiveAumDaysWhenNormalizingFeeDrag() {
    var date1 = LocalDate.of(2026, 4, 1);
    var date2 = LocalDate.of(2026, 4, 2);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(tdEvent(date1, "0", "0"), tdEvent(date2, "0", "0")));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of(feeAccrual(date1, FeeType.MANAGEMENT, "100.00")));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("100000000"));
    given(fundNavQueryService.findAum(FUND_CODE, date2)).willReturn(ZERO);
    given(fundNavQueryService.findCashValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any())).willReturn(ZERO);

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.mgmtFeeDrag()).isEqualByComparingTo(new BigDecimal("-0.00000100"));
  }

  @Test
  void ignoresPositionsWithNullAccountIdWhenIndexingByIsin() {
    setupStandardMocks();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(any(), eq(TUK75), eq(SECURITY)))
        .willReturn(
            List.of(
                position(ISIN_DW, "68600000"),
                position(ISIN_EUROPE_ETF, "29400000"),
                FundPosition.builder()
                    .fund(TUK75)
                    .accountType(SECURITY)
                    .accountId(null)
                    .accountName("unknown")
                    .marketValue(new BigDecimal("1000000"))
                    .build()));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.instrumentDetails()).hasSize(2);
  }

  @Test
  void namesTheInstrumentAfterTheFirstPositionWhenDuplicateAccountIdsExistForADay() {
    var date1 = LocalDate.of(2026, 4, 1);

    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, MODEL_PORTFOLIO, PERIOD_START, PERIOD_END))
        .willReturn(List.of(singleAttributionEvent(date1, ISIN_DW)));
    given(feeAccrualRepository.findByFundAndDateRange(TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, PERIOD_END))
        .willReturn(Optional.empty());
    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(List.of());
    given(fundNavQueryService.findAum(FUND_CODE, date1)).willReturn(new BigDecimal("1000000"));
    given(fundNavQueryService.findCashValue(anyString(), any())).willReturn(ZERO);
    given(fundNavQueryService.findSecuritiesTotalValue(anyString(), any()))
        .willReturn(new BigDecimal("1000"));
    given(fundNavQueryService.findFeeAccrualLiabilities(anyString(), any())).willReturn(ZERO);
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(date1, TUK75, SECURITY))
        .willReturn(
            List.of(
                positionNamed(ISIN_DW, "Developed World"),
                positionNamed(ISIN_DW, "Developed World (duplicate)")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    var detail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals(ISIN_DW))
            .findFirst()
            .orElseThrow();
    assertThat(detail.instrumentName()).isEqualTo("Developed World");
  }

  @Test
  void usesFirstInstrumentFeeRateWhenDuplicateIsinRowsExist() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));
    var baseline = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(
                instrumentFee(ISIN_DW, "0.0012"),
                instrumentFee(ISIN_DW, "0.0090"),
                instrumentFee(ISIN_EUROPE_ETF, "0.0020")));
    var withDuplicate = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(withDuplicate.etfOcfDrag()).isEqualByComparingTo(baseline.etfOcfDrag());
  }

  @Test
  void etfLayerIgnoresFutureDatedModelAllocationsWhenSelectingTheLatestVersion() {
    setupStandardMocks();
    givenBenchmarkModelEvents();
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));
    var baseline = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    given(
            modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
                TUK75, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                modelAllocation(ISIN_DW, "0.70", PERIOD_START),
                modelAllocation(ISIN_EUROPE_ETF, "0.30", PERIOD_START),
                modelAllocation(ISIN_DW, "0.99", PERIOD_END.plusMonths(1))));
    var withFutureRow = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(withFutureRow.etfOcfDrag()).isEqualByComparingTo(baseline.etfOcfDrag());
  }

  @Test
  void etfLayerTreatsThePeriodAsUnmeasuredWhenAnyDayLacksSecurityAttributions() {
    setupStandardMocks();
    given(
            tdEventRepository.findDeduplicatedEventsForPeriod(
                TUK75, TrackingCheckType.BENCHMARK_MODEL, PERIOD_START, PERIOD_END))
        .willReturn(
            List.of(
                benchmarkModelEvent(
                    LocalDate.of(2026, 4, 1), "-0.00010", List.of(ISIN_DW, ISIN_EUROPE_ETF)),
                benchmarkModelEvent(LocalDate.of(2026, 4, 2), "-0.00015", List.of())));
    given(instrumentFeeRepository.findAllValidRates(PERIOD_END))
        .willReturn(
            List.of(instrumentFee(ISIN_DW, "0.0012"), instrumentFee(ISIN_EUROPE_ETF, "0.0020")));

    var result = service.computeAttribution(TUK75, PERIOD_START, PERIOD_END, MONTHLY);

    assertThat(result.etfOcfDrag()).isEqualByComparingTo(ZERO);
    assertThat(result.checks())
        .containsEntry("etfLayerUnbenchmarkedWeight", new BigDecimal("1.000000"));
  }
}
