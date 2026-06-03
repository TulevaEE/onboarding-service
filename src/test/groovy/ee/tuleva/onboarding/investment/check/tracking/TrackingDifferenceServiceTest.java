package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingDifferenceServiceTest {

  @Mock FundPositionRepository fundPositionRepository;
  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock FundValueProvider fundValueProvider;
  @Mock PriorityPriceProvider priorityPriceProvider;
  @Mock PositionPriceResolver positionPriceResolver;
  final PublicHolidays publicHolidays = new PublicHolidays();
  @Mock FeeRateRepository feeRateRepository;
  @Mock TrackingDifferenceEventRepository eventRepository;
  @Mock InvestmentParameterRepository parameterRepository;
  @Mock ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService fundNavQueryService;

  private TrackingDifferenceService service;

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 10);
  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 4, 9);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-10T16:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @BeforeEach
  void setUp() {
    lenient()
        .when(fundValueProvider.getLatestValue(anyString(), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(fundNavQueryService.findNavPerUnit(anyString(), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(fundNavQueryService.findLatestNavDateOnOrBefore(anyString(), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            parameterRepository.findLatestValue(
                eq(InvestmentParameter.TRACKING_BREACH_THRESHOLD), any(LocalDate.class)))
        .thenReturn(new BigDecimal("0.005"));
    lenient()
        .when(
            parameterRepository.findLatestValue(
                eq(InvestmentParameter.TRACKING_MAX_DAILY_RETURN), any(LocalDate.class)))
        .thenReturn(new BigDecimal("0.5"));
    service =
        new TrackingDifferenceService(
            FIXED_CLOCK,
            fundPositionRepository,
            modelPortfolioAllocationRepository,
            fundValueProvider,
            priorityPriceProvider,
            positionPriceResolver,
            publicHolidays,
            feeRateRepository,
            eventRepository,
            new TrackingDifferenceCalculator(parameterRepository),
            fundNavQueryService);
  }

  @Test
  void calculatesModelPortfolioTrackingDifference() {
    setupFundData(TUK75);

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().fund()).isEqualTo(TUK75);
    assertThat(modelResult.get().fundReturn()).isEqualByComparingTo(new BigDecimal("0.01"));
    assertThat(modelResult.get().benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.02"));
    verify(eventRepository).save(any(TrackingDifferenceEvent.class));
  }

  @Test
  void modelPortfolioWeightDeviationUsesSecuritiesOnlyBasisSoCashIsNotDoubleCounted() {
    // 950k single security (100% of the securities sleeve), 50k cash, model weight 1.0.
    // The security tracks the model perfectly, so the weight-deviation contribution must be 0
    // even though cash dilutes the holding to 95% of total NAV. The cash effect is attributed
    // once, via cashDrag — not embedded again in the per-instrument deviation.
    setupFundData(TUK75);

    var results = service.runChecksAsOf(CHECK_DATE);

    var model =
        results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst().orElseThrow();
    var attr = model.securityAttributions().getFirst();
    assertThat(attr.actualWeight()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(attr.weightDifference()).isEqualByComparingTo(ZERO);
    assertThat(attr.contribution()).isEqualByComparingTo(ZERO);
    // cashDrag = -cashWeight * modelReturn = -0.05 * 0.02
    assertThat(model.cashDrag()).isEqualByComparingTo(new BigDecimal("-0.001"));
  }

  @Test
  void calculatesBenchmarkTrackingDifference() {
    setupFundData(TUK75);
    setupBenchmarkData("MSCI_ACWI");

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmResult = results.stream().filter(r -> r.checkType() == BENCHMARK).findFirst();
    assertThat(bmResult).isPresent();
    assertThat(bmResult.get().fund()).isEqualTo(TUK75);
    assertThat(bmResult.get().securityAttributions()).isEmpty();
  }

  @Test
  void calculatesBenchmarkModelForEquityFund() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var etfIsin = "IE00BFNM3G45";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin(etfIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    var todayPriceDate = CHECK_DATE;
    var previousPriceDate = PREVIOUS_DATE;
    given(positionPriceResolver.resolve(eq(etfIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("51.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(todayPriceDate)
                    .build()));
    given(positionPriceResolver.resolve(eq(etfIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("50.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(previousPriceDate)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(etfIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, TUK75, FeeType.MANAGEMENT, new BigDecimal("0.0034"), CHECK_DATE, null)));
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    // IWDA.DE benchmark looked up on instrument's actual price dates
    given(fundValueProvider.getLatestValue("IE00B4L5Y983.XETR", todayPriceDate))
        .willReturn(Optional.of(fundValue("81.60")));
    given(fundValueProvider.getLatestValue("IE00B4L5Y983.XETR", previousPriceDate))
        .willReturn(Optional.of(fundValue("80.00")));

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isPresent();
    assertThat(bmModel.get().fund()).isEqualTo(TUK75);
    // ETF return = 51/50 - 1 = 0.02, IWDA return = 81.6/80 - 1 = 0.02, TD ≈ 0
    assertThat(bmModel.get().trackingDifference().abs()).isLessThan(new BigDecimal("0.001"));

    var attributions = bmModel.get().securityAttributions();
    assertThat(attributions).hasSize(1);
    var attr = attributions.getFirst();
    assertThat(attr.isin()).isEqualTo(etfIsin);
    assertThat(attr.modelWeight()).isNull();
    // securities-only basis: the single holding is 100% of the securities sleeve
    assertThat(attr.actualWeight()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(attr.securityReturn()).isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(attr.benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(attr.contribution().abs()).isLessThan(new BigDecimal("0.001"));
  }

  @Test
  void calculatesBenchmarkModelForBondFund() {
    skipOtherFunds(TUK00);

    given(fundNavQueryService.findNavPerUnit(TUK00.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK00.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var bondIsin = "LU0826455353";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK00)
            .isin(bondIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK00, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(bondIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("116.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(bondIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("115.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK00)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(bondIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK00, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK00, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK00, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK00, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK00), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    // IEAG.DE benchmark for Euro Aggregate bonds
    given(fundValueProvider.getLatestValue("IE00B3DKXQ41.XETR", CHECK_DATE))
        .willReturn(Optional.of(fundValue("105.30")));
    given(fundValueProvider.getLatestValue("IE00B3DKXQ41.XETR", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("105.00")));

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isPresent();
    assertThat(bmModel.get().fund()).isEqualTo(TUK00);
  }

  @Test
  void calculatesBenchmarkModelForEmMutualFund() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    // EM mutual fund ISIN → should resolve to MSCI_EM
    var emIsin = "IE00BKPTWY98";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin(emIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(emIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("20.40"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(emIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("20.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(emIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    // MSCI_EM benchmark for EM mutual fund
    given(fundValueProvider.getLatestValue("MSCI_EM", CHECK_DATE))
        .willReturn(Optional.of(fundValue("500.00")));
    given(fundValueProvider.getLatestValue("MSCI_EM", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("490.00")));

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isPresent();
    assertThat(bmModel.get().fund()).isEqualTo(TUK75);
  }

  @Test
  void benchmarkModelSkipsWhenBenchmarkDataMissing() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var etfIsin = "IE00BFNM3G45";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin(etfIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(etfIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("51.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(etfIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("50.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(etfIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    // No IWDA.DE benchmark data — BENCHMARK_MODEL should be skipped
    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isEmpty();
  }

  @Test
  void runChecksForFundsOnlyChecksSpecifiedFunds() {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(CHECK_DATE));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    setupFundDataForFund(TUK75);

    var results = service.runChecksForFunds(List.of(TUK75));

    assertThat(results).allMatch(r -> r.fund() == TUK75);
  }

  @Test
  void calculatesBenchmarkModelForEmEtf() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    // EM ETF ISIN → should resolve to EUNM.DE (IE00B4L5YC18.XETR)
    var emEtfIsin = "IE00BMDBMY19";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin(emEtfIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(emEtfIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("30.60"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(emEtfIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("30.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(emEtfIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    // EUNM.DE benchmark for EM ETF
    given(fundValueProvider.getLatestValue("IE00B4L5YC18.XETR", CHECK_DATE))
        .willReturn(Optional.of(fundValue("40.80")));
    given(fundValueProvider.getLatestValue("IE00B4L5YC18.XETR", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("40.00")));

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isPresent();
  }

  @Test
  void benchmarkModelSkipsUnknownIsinInEquityFund() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    // Unknown ISIN not in FundTicker → resolveBenchmarkKey returns null → skipped
    var unknownIsin = "IE00UNKNOWN00";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin(unknownIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(unknownIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("10.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(unknownIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("9.90"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(unknownIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    // All securities skipped → no BENCHMARK_MODEL result
    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isEmpty();
  }

  @Test
  void benchmarkModelSkipsUnknownBondIsin() {
    skipOtherFunds(TUK00);

    given(fundNavQueryService.findNavPerUnit(TUK00.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK00.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    // Bond ISIN not in BOND_BENCHMARK_KEYS → skipped
    var unknownBondIsin = "LU0000000000";
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK00)
            .isin(unknownBondIsin)
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK00, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq(unknownBondIsin), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("100.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(CHECK_DATE)
                    .build()));
    given(positionPriceResolver.resolve(eq(unknownBondIsin), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("99.00"))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(PREVIOUS_DATE)
                    .build()));

    var position =
        FundPosition.builder()
            .fund(TUK00)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId(unknownBondIsin)
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK00, SECURITY))
        .willReturn(List.of(position));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK00, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK00, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));
    given(feeRateRepository.findValidRate(TUK00, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK00), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmModel = results.stream().filter(r -> r.checkType() == BENCHMARK_MODEL).findFirst();
    assertThat(bmModel).isEmpty();
  }

  @Test
  void calculatesCompositeBondBenchmarkForTUK00() {
    setupFundData(TUK00);
    given(priorityPriceProvider.resolve("LU0826455353", CHECK_DATE))
        .willReturn(Optional.of(fundValue("115.45")));
    given(priorityPriceProvider.resolve("LU0826455353", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("115.00")));
    given(priorityPriceProvider.resolve("LU0839970364", CHECK_DATE))
        .willReturn(Optional.of(fundValue("93.73")));
    given(priorityPriceProvider.resolve("LU0839970364", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("93.50")));

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmResult = results.stream().filter(r -> r.checkType() == BENCHMARK).findFirst();
    assertThat(bmResult).isPresent();
    assertThat(bmResult.get().benchmarkReturn().signum()).isPositive();
  }

  @Test
  void skipsWhenNoNavData() {
    // Lenient default already returns Optional.empty() for findLatestNavDateOnOrBefore,
    // so every fund is skipped before findNavPerUnit is even called.
    var results = service.runChecksAsOf(CHECK_DATE);

    assertThat(results).isEmpty();
  }

  @Test
  void skipsWhenNoPreviousNavDate() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.empty());

    var results = service.runChecksAsOf(CHECK_DATE);

    assertThat(results).isEmpty();
  }

  @Test
  void skipsWhenBenchmarkDataMissing() {
    setupFundData(TUK75);
    // No MSCI_ACWI data set up — benchmark check should be skipped

    var results = service.runChecksAsOf(CHECK_DATE);

    var bmResults = results.stream().filter(r -> r.checkType() == BENCHMARK).toList();
    assertThat(bmResults).isEmpty();
  }

  @Test
  void consecutiveBreachCountingStopsAtNonBreach() {
    setupFundData(TUK75);

    var breachEvent = breachEvent(LocalDate.of(2026, 4, 2), new BigDecimal("0.0020"));
    var nonBreachEvent = nonBreachEvent(LocalDate.of(2026, 4, 1));

    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 2))
        .willReturn(List.of(breachEvent, nonBreachEvent));

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    // Prior count = 1 (day 2 breach, day 1 non-breach stops counting)
    // Today breaches (TD = 0.01 - 0.02 = -0.01, |0.01| > 0.001)
    // So consecutiveBreachDays = 1 + 1 = 2
    assertThat(modelResult.get().consecutiveBreachDays()).isEqualTo(2);
  }

  @Test
  void consecutiveNetTdSumsOverBreachDays() {
    setupFundData(TUK75);

    var breachEvent1 = breachEvent(LocalDate.of(2026, 4, 2), new BigDecimal("0.0020"));
    var breachEvent2 = breachEvent(LocalDate.of(2026, 4, 1), new BigDecimal("0.0015"));

    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 2))
        .willReturn(List.of(breachEvent1, breachEvent2));

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().consecutiveBreachDays()).isEqualTo(3);
    // net = prior net (0.0020 + 0.0015) + today's TD (-0.01)
    assertThat(modelResult.get().consecutiveNetTd()).isNotNull();
  }

  @Test
  void handlesZeroTotalNav() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of());
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(ZERO);
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(ZERO);

    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());

    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().cashDrag()).isEqualByComparingTo(ZERO);
  }

  @Test
  void savesEventWithConsecutiveBreachDays() {
    setupFundData(TUK75);

    service.runChecksAsOf(CHECK_DATE);

    verify(eventRepository).save(any(TrackingDifferenceEvent.class));
  }

  @Test
  void usesCutoffAdjustedPriceForSecurityReturn() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("101.00")));
    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    var todayPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00B4L5Y983")
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(todayPosition));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());

    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    // Cutoff-adjusted prices: (101 - 100) / 100 = 0.01
    assertThat(modelResult.get().benchmarkReturn()).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void throwsWhenSecurityPriceDataIncomplete() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var allocation1 =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("0.70"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    var allocation2 =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00MISSING1")
            .weight(new BigDecimal("0.30"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation1, allocation2));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of());
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00MISSING1"), any(LocalDate.class), any()))
        .willReturn(Optional.empty());

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.runChecksAsOf(CHECK_DATE))
        .isInstanceOf(TrackingDifferenceService.IncompletePriceDataException.class)
        .hasMessageContaining("IE00MISSING1");
  }

  private void setupFundData(TulevaFund fund) {
    skipOtherFunds(fund);

    given(fundNavQueryService.findNavPerUnit(fund.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(fund.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(fund)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    var position =
        FundPosition.builder()
            .fund(fund)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00B4L5Y983")
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, fund, SECURITY))
        .willReturn(List.of(position));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                fund, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                fund, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(feeRateRepository.findValidRate(fund, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, fund, FeeType.MANAGEMENT, new BigDecimal("0.0034"), CHECK_DATE, null)));

    given(eventRepository.findMostRecentEvents(eq(fund), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());
  }

  private void setupFundDataForFund(TulevaFund fund) {
    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(fund)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, CHECK_DATE))
        .willReturn(List.of(allocation));

    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00B4L5Y983"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    var position =
        FundPosition.builder()
            .fund(fund)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00B4L5Y983")
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, fund, SECURITY))
        .willReturn(List.of(position));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                fund, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                fund, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(feeRateRepository.findValidRate(fund, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());

    given(eventRepository.findMostRecentEvents(eq(fund), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());
  }

  private void setupBenchmarkData(String key) {
    given(fundValueProvider.getLatestValue(key, CHECK_DATE))
        .willReturn(Optional.of(fundValue("1050.00")));
    given(fundValueProvider.getLatestValue(key, PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("1000.00")));
  }

  private void skipOtherFunds(TulevaFund fund) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), CHECK_DATE))
        .willReturn(Optional.of(CHECK_DATE));
    for (var f : TulevaFund.values()) {
      if (f != fund) {
        given(fundNavQueryService.findLatestNavDateOnOrBefore(f.getCode(), CHECK_DATE))
            .willReturn(Optional.empty());
      }
    }
  }

  private TrackingDifferenceEvent breachEvent(LocalDate date, BigDecimal td) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(td)
        .fundReturn(new BigDecimal("0.01"))
        .benchmarkReturn(new BigDecimal("0.008"))
        .breach(true)
        .consecutiveBreachDays(1)
        .build();
  }

  private TrackingDifferenceEvent nonBreachEvent(LocalDate date) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.0005"))
        .fundReturn(new BigDecimal("0.01"))
        .benchmarkReturn(new BigDecimal("0.0095"))
        .breach(false)
        .consecutiveBreachDays(0)
        .build();
  }

  private FundValue fundValue(String value) {
    return fundValue(value, CHECK_DATE);
  }

  private FundValue fundValue(String value, LocalDate date) {
    return new FundValue("test", date, new BigDecimal(value), "TEST", Instant.now());
  }

  @Test
  void blendsModelWeightsDuringInstrumentTransition() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(previousAllocation));

    var oldPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00OLD")
            .marketValue(new BigDecimal("500000"))
            .build();
    var newPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00NEW")
            .marketValue(new BigDecimal("500000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(oldPosition, newPosition));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(ZERO);

    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("51.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("50.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().securityAttributions()).hasSize(2);

    var oldAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00OLD"))
            .findFirst()
            .orElseThrow();
    assertThat(oldAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(oldAttr.actualWeight()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(oldAttr.contribution()).isEqualByComparingTo(ZERO);

    var newAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00NEW"))
            .findFirst()
            .orElseThrow();
    assertThat(newAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(newAttr.actualWeight()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(newAttr.contribution()).isEqualByComparingTo(ZERO);
  }

  @Test
  void doesNotThrowForInRunoffSecuritiesWithMissingPrices() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(previousAllocation));

    var oldPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00OLD")
            .marketValue(new BigDecimal("100000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(oldPosition));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("900000"));

    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), any(LocalDate.class), any()))
        .willReturn(Optional.empty());

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    assertThat(results).isNotEmpty();
  }

  @Test
  void skipsBlendingWhenUnexpectedPositionHeld() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(previousAllocation));

    var oldPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00OLD")
            .marketValue(new BigDecimal("400000"))
            .build();
    var newPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00NEW")
            .marketValue(new BigDecimal("400000"))
            .build();
    var roguePosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00ROGUE")
            .marketValue(new BigDecimal("200000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(oldPosition, newPosition, roguePosition));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(ZERO);

    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("51.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("50.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();

    var oldAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00OLD"))
            .findFirst()
            .orElseThrow();
    assertThat(oldAttr.modelWeight()).isEqualByComparingTo(ZERO);
  }

  @Test
  void blendsOnlyTransitioningInstrumentsKeepsStableWeights() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var stableAlloc =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00STABLE")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    var newAlloc =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00NEW")
            .weight(new BigDecimal("0.40"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(stableAlloc, newAlloc));

    var prevStableAlloc =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00STABLE")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    var oldAlloc =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00OLD")
            .weight(new BigDecimal("0.40"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(prevStableAlloc, oldAlloc));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00STABLE")
                    .marketValue(new BigDecimal("580000"))
                    .build(),
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00NEW")
                    .marketValue(new BigDecimal("200000"))
                    .build(),
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00OLD")
                    .marketValue(new BigDecimal("200000"))
                    .build()));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("20000"));

    given(positionPriceResolver.resolve(eq("IE00STABLE"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00STABLE"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("99.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("50.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("49.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("30.00")));
    given(positionPriceResolver.resolve(eq("IE00OLD"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("29.50")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().securityAttributions()).hasSize(3);

    var stableAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00STABLE"))
            .findFirst()
            .orElseThrow();
    assertThat(stableAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("0.60"));

    var newAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00NEW"))
            .findFirst()
            .orElseThrow();
    // transition ISIN: modelWeight is blended to actualWeight, now securities-only
    // (200000 / 980000 securities sleeve = 0.204082, was 0.2 on total-NAV basis)
    assertThat(newAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("0.204082"));

    var oldAttr =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00OLD"))
            .findFirst()
            .orElseThrow();
    assertThat(oldAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("0.204082"));
  }

  @Test
  void stopsBlendingWhenOldInstrumentFullySold() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(previousAllocation));

    var newPosition =
        FundPosition.builder()
            .fund(TUK75)
            .navDate(CHECK_DATE)
            .accountType(SECURITY)
            .accountId("IE00NEW")
            .marketValue(new BigDecimal("950000"))
            .build();
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(List.of(newPosition));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00NEW"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    assertThat(modelResult.get().securityAttributions()).hasSize(1);

    var newAttr = modelResult.get().securityAttributions().getFirst();
    assertThat(newAttr.isin()).isEqualTo("IE00NEW");
    assertThat(newAttr.modelWeight()).isEqualByComparingTo(new BigDecimal("1.00"));
  }

  @Test
  void skipsBlendingWhenNoPreviousAllocations() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00A")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation));
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of());

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00A")
                    .marketValue(new BigDecimal("950000"))
                    .build()));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(new BigDecimal("50000"));

    given(positionPriceResolver.resolve(eq("IE00A"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00A"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();

    var attr = modelResult.get().securityAttributions().getFirst();
    assertThat(attr.modelWeight()).isEqualByComparingTo(new BigDecimal("1.00"));
  }

  @Test
  void skipsBlendingWhenRebalanceOnlyNoInstrumentChange() {
    skipOtherFunds(TUK75);

    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), CHECK_DATE))
        .willReturn(Optional.of(new BigDecimal("10.10")));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), PREVIOUS_DATE))
        .willReturn(Optional.of(new BigDecimal("10.00")));

    var currentA =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00A")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    var currentB =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B")
            .weight(new BigDecimal("0.40"))
            .effectiveDate(LocalDate.of(2026, 4, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(currentA, currentB));

    var prevA =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00A")
            .weight(new BigDecimal("0.50"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    var prevB =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B")
            .weight(new BigDecimal("0.50"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUK75, CHECK_DATE))
        .willReturn(List.of(prevA, prevB));

    given(fundPositionRepository.findByNavDateAndFundAndAccountType(CHECK_DATE, TUK75, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00A")
                    .marketValue(new BigDecimal("550000"))
                    .build(),
                FundPosition.builder()
                    .fund(TUK75)
                    .navDate(CHECK_DATE)
                    .accountType(SECURITY)
                    .accountId("IE00B")
                    .marketValue(new BigDecimal("450000"))
                    .build()));

    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY)))
        .willReturn(new BigDecimal("1000000"));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                TUK75, CHECK_DATE, List.of(CASH)))
        .willReturn(ZERO);

    given(positionPriceResolver.resolve(eq("IE00A"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("102.00")));
    given(positionPriceResolver.resolve(eq("IE00A"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("100.00")));
    given(positionPriceResolver.resolve(eq("IE00B"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("51.00")));
    given(positionPriceResolver.resolve(eq("IE00B"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.of(resolvedPrice("50.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());
    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();

    var attrA =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00A"))
            .findFirst()
            .orElseThrow();
    assertThat(attrA.modelWeight()).isEqualByComparingTo(new BigDecimal("0.60"));

    var attrB =
        modelResult.get().securityAttributions().stream()
            .filter(a -> a.isin().equals("IE00B"))
            .findFirst()
            .orElseThrow();
    assertThat(attrB.modelWeight()).isEqualByComparingTo(new BigDecimal("0.40"));
  }

  private ResolvedPrice resolvedPrice(String value) {
    return ResolvedPrice.builder()
        .usedPrice(new BigDecimal(value))
        .validationStatus(ValidationStatus.OK)
        .build();
  }
}
