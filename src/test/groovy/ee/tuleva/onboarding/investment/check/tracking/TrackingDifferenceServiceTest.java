package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
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
  @Mock FeeRateRepository feeRateRepository;
  @Mock TrackingDifferenceEventRepository eventRepository;
  @Mock PublicHolidays publicHolidays;

  private TrackingDifferenceService service;

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 3);
  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 4, 2);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-03T16:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @BeforeEach
  void setUp() {
    service =
        new TrackingDifferenceService(
            FIXED_CLOCK,
            fundPositionRepository,
            modelPortfolioAllocationRepository,
            fundValueProvider,
            priorityPriceProvider,
            feeRateRepository,
            eventRepository,
            new TrackingDifferenceCalculator(),
            publicHolidays);
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
  void skipsWhenNoPositionData() {
    for (var fund : TulevaFund.values()) {
      given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, CHECK_DATE))
          .willReturn(Optional.empty());
    }

    var results = service.runChecksAsOf(CHECK_DATE);

    assertThat(results).isEmpty();
  }

  @Test
  void skipsWhenNoPreviousNavDate() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, CHECK_DATE))
        .willReturn(Optional.of(CHECK_DATE));
    given(publicHolidays.previousWorkingDay(CHECK_DATE)).willReturn(PREVIOUS_DATE);
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, PREVIOUS_DATE))
        .willReturn(Optional.empty());

    skipOtherFunds(TUK75);

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
  void usesPriceCutoffForSecurityPrices() {
    setupFundData(TUK75);

    service.runChecksAsOf(CHECK_DATE);

    var expectedCutoff =
        CHECK_DATE.atTime(TUK75.getNavCutoffTime()).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
    verify(priorityPriceProvider).resolve("IE00B4L5Y983", CHECK_DATE, expectedCutoff);
  }

  @Test
  void usesPriceCutoffForFundNav() {
    setupFundData(TUK75);

    service.runChecksAsOf(CHECK_DATE);

    var expectedCutoff =
        CHECK_DATE.atTime(TUK75.getNavCutoffTime()).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
    verify(fundValueProvider).getLatestValue(TUK75.getIsin(), CHECK_DATE, expectedCutoff);
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

    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, CHECK_DATE))
        .willReturn(Optional.of(CHECK_DATE));
    given(publicHolidays.previousWorkingDay(CHECK_DATE)).willReturn(PREVIOUS_DATE);
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, PREVIOUS_DATE))
        .willReturn(Optional.of(PREVIOUS_DATE));

    var priceCutoff =
        CHECK_DATE.atTime(TUK75.getNavCutoffTime()).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
    given(fundValueProvider.getLatestValue(TUK75.getIsin(), CHECK_DATE, priceCutoff))
        .willReturn(Optional.of(fundValue("10.10")));
    given(fundValueProvider.getLatestValue(TUK75.getIsin(), PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75))
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

    given(priorityPriceProvider.resolve("IE00B4L5Y983", CHECK_DATE, priceCutoff))
        .willReturn(Optional.of(fundValue("102.00")));
    given(priorityPriceProvider.resolve("IE00B4L5Y983", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("100.00")));

    given(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(Optional.empty());

    given(eventRepository.findMostRecentEvents(eq(TUK75), any(), eq(CHECK_DATE), eq(2)))
        .willReturn(List.of());

    var results = service.runChecksAsOf(CHECK_DATE);

    var modelResult = results.stream().filter(r -> r.checkType() == MODEL_PORTFOLIO).findFirst();
    assertThat(modelResult).isPresent();
    // Cash weight should be 0 when totalNav is 0
    assertThat(modelResult.get().cashDrag()).isEqualByComparingTo(ZERO);
  }

  @Test
  void savesEventWithConsecutiveBreachDays() {
    setupFundData(TUK75);

    service.runChecksAsOf(CHECK_DATE);

    verify(eventRepository)
        .deleteByFundAndCheckDateAndCheckType(TUK75, CHECK_DATE, MODEL_PORTFOLIO);
    verify(eventRepository).save(any(TrackingDifferenceEvent.class));
  }

  private void setupFundData(TulevaFund fund) {
    skipOtherFunds(fund);

    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, CHECK_DATE))
        .willReturn(Optional.of(CHECK_DATE));
    given(publicHolidays.previousWorkingDay(CHECK_DATE)).willReturn(PREVIOUS_DATE);
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, PREVIOUS_DATE))
        .willReturn(Optional.of(PREVIOUS_DATE));

    var cutoff =
        CHECK_DATE.atTime(fund.getNavCutoffTime()).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
    given(fundValueProvider.getLatestValue(fund.getIsin(), CHECK_DATE, cutoff))
        .willReturn(Optional.of(fundValue("10.10")));
    given(fundValueProvider.getLatestValue(fund.getIsin(), PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("10.00")));

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(fund)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("1.00"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFund(fund))
        .willReturn(List.of(allocation));

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

    given(priorityPriceProvider.resolve("IE00B4L5Y983", CHECK_DATE, cutoff))
        .willReturn(Optional.of(fundValue("102.00")));
    given(priorityPriceProvider.resolve("IE00B4L5Y983", PREVIOUS_DATE))
        .willReturn(Optional.of(fundValue("100.00")));

    given(feeRateRepository.findValidRate(fund, FeeType.MANAGEMENT, CHECK_DATE))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L, fund, FeeType.MANAGEMENT, new BigDecimal("0.0034"), CHECK_DATE, null)));

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
    for (var f : TulevaFund.values()) {
      if (f != fund) {
        given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(f, CHECK_DATE))
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
    return new FundValue("test", CHECK_DATE, new BigDecimal(value), "TEST", Instant.now());
  }
}
