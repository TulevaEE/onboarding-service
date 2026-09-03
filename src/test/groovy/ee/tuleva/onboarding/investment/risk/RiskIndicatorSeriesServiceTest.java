package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskIndicatorSeriesServiceTest {

  private static final String ACWI = "MSCI_ACWI";
  private static final String TKF_ISIN = "EE0000003283";
  private static final LocalDate ANCHOR = LocalDate.of(2026, 6, 30);
  private static final String CURRENT_HOLDING_PERIOD =
      String.valueOf(SriCalculator.HOLDING_PERIOD_TRADING_DAYS);

  @Mock private FundValueQueries fundValueQueries;
  @Mock private RiskIndicatorPointRepository pointRepository;

  private RiskIndicatorSeriesService service;

  @BeforeEach
  void setUp() {
    service = serviceWith(singleSourceProperties());
  }

  @Test
  void loadsPricesFromBeforeTheWindowSoTheFirstReturnCanBeComputed() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    givenPrices(prices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());

    service.refreshSeries(TKF100, SRI, 1);

    var start = ArgumentCaptor.forClass(LocalDate.class);
    verify(fundValueQueries).findValuesBetweenDates(eq(ACWI), start.capture(), eq(ANCHOR));
    assertThat(start.getValue()).isEqualTo(ANCHOR.minusMonths(1).minusYears(5).minusWeeks(2));
  }

  @Test
  void anchorsOnTheLatestDataDateNotTheClock() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR.minusDays(4));
    given(fundValueQueries.findLastValueForFund(ACWI)).willReturn(Optional.of(prices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(anyString(), any(), any())).willReturn(prices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());

    var points = service.refreshSeries(TKF100, SRI, 1).points();

    assertThat(points.getLast().date()).isEqualTo(prices.getLast().date()).isNotEqualTo(ANCHOR);
  }

  @Test
  void aSourceThatStoppedUpdatingFailsInsteadOfRecomputingTheSameSeries() {
    var lastPriceDate = ANCHOR.minusMonths(5);
    var prices = dailyPrices(ACWI, lastPriceDate.minusYears(7), lastPriceDate);
    given(fundValueQueries.findLastValueForFund(ACWI)).willReturn(Optional.of(prices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(anyString(), any(), any())).willReturn(prices);

    assertThatThrownBy(() -> service.refreshSeries(TKF100, SRI, 1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void savesNothingTwiceForTheSameDate() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    givenPrices(prices);
    var stored = new ArrayList<RiskIndicatorPoint>();
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willAnswer(invocation -> List.copyOf(stored));
    given(pointRepository.saveAll(any()))
        .willAnswer(
            invocation -> {
              stored.addAll(invocation.getArgument(0));
              return invocation.getArgument(0);
            });

    service.refreshSeries(TKF100, SRI, 1);
    var afterFirstRun = stored.size();
    service.refreshSeries(TKF100, SRI, 1);

    assertThat(stored).hasSize(afterFirstRun);
  }

  @Test
  void updatesDriftedPointsAndKeepsTheirHistory() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    givenPrices(prices);
    var drifted = storedPoint(Map.of("holdingPeriodTradingDays", CURRENT_HOLDING_PERIOD));
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of(drifted));

    var refresh = service.refreshSeries(TKF100, SRI, 1);

    assertThat(drifted.getRiskClass()).isNotEqualTo(1);
    assertThat(drifted.getObservationCount()).isNotEqualTo(1);
    assertThat(drifted.getVolatility()).isNotEqualByComparingTo(valueOf(0.999));
    assertThat(drifted.getMetrics()).containsKey("driftHistory");
    assertThat(refresh.driftedDates()).containsExactly(ANCHOR);
    assertThat(refresh.redefinitions()).isEmpty();
    assertThat((List<Map<String, String>>) drifted.getMetrics().get("driftHistory"))
        .singleElement()
        .satisfies(entry -> assertThat(entry).containsEntry("detectedAt", ANCHOR.toString()));
    var saved = ArgumentCaptor.forClass(List.class);
    verify(pointRepository).saveAll(saved.capture());
    assertThat((List<RiskIndicatorPoint>) saved.getValue()).contains(drifted);
  }

  @Test
  void aPointStoredBeforeTheHoldingPeriodWasRecordedIsRedefinedRatherThanReportedAsDrift() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    givenPrices(prices);
    var stored = storedPoint(Map.of());
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of(stored));

    var refresh = service.refreshSeries(TKF100, SRI, 1);

    assertThat(refresh.driftedDates()).isEmpty();
    assertThat(refresh.redefinitions())
        .containsExactly(new Redefinition.HoldingPeriod(ANCHOR, null, CURRENT_HOLDING_PERIOD));
    assertThat(stored.getRiskClass()).isNotEqualTo(1);
    assertThat(stored.getMetrics())
        .doesNotContainKey("driftHistory")
        .containsEntry("holdingPeriodTradingDays", CURRENT_HOLDING_PERIOD);
    var saved = ArgumentCaptor.forClass(List.class);
    verify(pointRepository).saveAll(saved.capture());
    assertThat((List<RiskIndicatorPoint>) saved.getValue()).contains(stored);
  }

  // Restoring the SRRI coverage gate withdraws the class from points whose window does not reach
  // back five years. Nothing upstream moved - the volatility and the observation count are the
  // ones already stored - so reporting it as drift would tell the reader the source data changed
  // retroactively, which is the one thing that did not happen.
  @Test
  void aClassThatChangedWhileTheInputsDidNotIsRedefinedRatherThanReportedAsDrift() {
    givenPrices(dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR));
    var stored = new ArrayList<RiskIndicatorPoint>();
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willAnswer(invocation -> List.copyOf(stored));
    given(pointRepository.saveAll(any()))
        .willAnswer(
            invocation -> {
              stored.addAll(invocation.getArgument(0));
              return invocation.getArgument(0);
            });
    service.refreshSeries(TKF100, SRI, 1);

    var withheld = stored.getFirst();
    var previouslyPublished = withheld.getRiskClass();
    withheld.setRiskClass(null);

    var refresh = service.refreshSeries(TKF100, SRI, 1);

    assertThat(refresh.driftedDates()).isEmpty();
    assertThat(refresh.redefinitions())
        .containsExactly(
            new Redefinition.PublicationRule(withheld.getAsOfDate(), null, previouslyPublished));
    assertThat(withheld.getMetrics()).doesNotContainKey("driftHistory");
  }

  @Test
  void aPointStoredUnderAnOlderHoldingPeriodIsRedefinedRatherThanReportedAsDrift() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    givenPrices(prices);
    var stored = storedPoint(Map.of("holdingPeriodTradingDays", "1300"));
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of(stored));

    var refresh = service.refreshSeries(TKF100, SRI, 1);

    assertThat(refresh.driftedDates()).isEmpty();
    assertThat(refresh.redefinitions())
        .containsExactly(new Redefinition.HoldingPeriod(ANCHOR, "1300", CURRENT_HOLDING_PERIOD));
    assertThat(stored.getMetrics())
        .doesNotContainKey("driftHistory")
        .containsEntry("holdingPeriodTradingDays", CURRENT_HOLDING_PERIOD);
  }

  private static RiskIndicatorPoint storedPoint(Map<String, Object> metrics) {
    return RiskIndicatorPoint.builder()
        .indicatorType(SRI)
        .fund(TKF100)
        .asOfDate(ANCHOR)
        .sourceKeys(ACWI)
        .riskClass(1)
        .observationCount(1)
        .volatility(valueOf(0.999))
        .metrics(metrics)
        .build();
  }

  @Test
  void doesNotComputeAReturnAcrossASegmentJoin() {
    var acwiPrices =
        dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR.minusYears(2).minusDays(1), 2000.0);
    var navPrices = dailyPrices(TKF_ISIN, ANCHOR.minusYears(2), ANCHOR, 1.05);
    given(fundValueQueries.findLastValueForFund(TKF_ISIN))
        .willReturn(Optional.of(navPrices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(eq(ACWI), any(), any())).willReturn(acwiPrices);
    given(fundValueQueries.findValuesBetweenDates(eq(TKF_ISIN), any(), any()))
        .willReturn(navPrices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());
    var splicedService = serviceWith(splicedProperties());

    var points = splicedService.refreshSeries(TKF100, SRI, 1).points();

    var saved = ArgumentCaptor.forClass(List.class);
    verify(pointRepository).saveAll(saved.capture());
    assertThat(((List<RiskIndicatorPoint>) saved.getValue()).getFirst().getSourceKeys())
        .isEqualTo(ACWI + "," + TKF_ISIN);

    // Without the same-key guard the join yields ln(1.05 / 2000) ≈ -7.55, which dominates sigma
    // and saturates the class at 7 for every evaluation date whose window spans the join.
    assertThat(points).isNotEmpty();
    assertThat(points).allSatisfy(point -> assertThat(point.riskClass()).isLessThan(7));
    assertThat(points.getLast().volatility().doubleValue()).isLessThan(0.5);
  }

  @Test
  void skipsFundsWhoseSourceHasAnAnchorButNoPricesInTheLoadWindow() {
    var prices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR);
    given(fundValueQueries.findLastValueForFund(anyString()))
        .willReturn(Optional.of(prices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(anyString(), any(), any())).willReturn(List.of());

    var refresh = service.refreshSeries(TKF100, SRI, 1);

    assertThat(refresh.points()).isEmpty();
    verify(pointRepository, never()).saveAll(any());
  }

  @Test
  void aSegmentThatEndsBeforeTheLoadWindowOpensIsNotQueriedAtAll() {
    var navPrices = dailyPrices(TKF_ISIN, ANCHOR.minusYears(7), ANCHOR, 1.05);
    given(fundValueQueries.findLastValueForFund(TKF_ISIN))
        .willReturn(Optional.of(navPrices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(eq(TKF_ISIN), any(), any()))
        .willReturn(navPrices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());
    var longRetiredProxy =
        new RiskIndicatorProperties(
            Map.of(
                TKF100,
                List.of(
                    new RiskIndicatorProperties.Source(ACWI, null),
                    new RiskIndicatorProperties.Source(TKF_ISIN, ANCHOR.minusYears(20)))),
            Map.of());

    var points = serviceWith(longRetiredProxy).refreshSeries(TKF100, SRI, 1).points();

    verify(fundValueQueries, never()).findValuesBetweenDates(eq(ACWI), any(), any());
    assertThat(points).isNotEmpty();
  }

  @Test
  void querySegmentUsesItsOwnExplicitStartWhenItIsLaterThanTheLoadWindow() {
    var explicitSegmentStart = ANCHOR.minusYears(2);
    var navPrices = dailyPrices(TKF_ISIN, explicitSegmentStart, ANCHOR, 1.05);
    given(fundValueQueries.findLastValueForFund(TKF_ISIN))
        .willReturn(Optional.of(navPrices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(eq(TKF_ISIN), any(), any()))
        .willReturn(navPrices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());
    var onlyOwnHistory =
        new RiskIndicatorProperties(
            Map.of(
                TKF100,
                List.of(new RiskIndicatorProperties.Source(TKF_ISIN, explicitSegmentStart))),
            Map.of());

    serviceWith(onlyOwnHistory).refreshSeries(TKF100, SRI, 1);

    var start = ArgumentCaptor.forClass(LocalDate.class);
    verify(fundValueQueries).findValuesBetweenDates(eq(TKF_ISIN), start.capture(), eq(ANCHOR));
    assertThat(start.getValue()).isEqualTo(explicitSegmentStart);
  }

  @Test
  void skipsFundsWithoutSourceData() {
    given(fundValueQueries.findLastValueForFund(anyString())).willReturn(Optional.empty());

    var points = service.refreshSeries(TUK75, SRRI, 1).points();

    assertThat(points).isEmpty();
  }

  private void givenPrices(List<FundValue> prices) {
    given(fundValueQueries.findLastValueForFund(anyString()))
        .willReturn(Optional.of(prices.getLast()));
    given(fundValueQueries.findValuesBetweenDates(anyString(), any(), any())).willReturn(prices);
  }

  private RiskIndicatorSeriesService serviceWith(RiskIndicatorProperties properties) {
    return new RiskIndicatorSeriesService(
        java.time.Clock.fixed(
            ANCHOR.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), java.time.ZoneOffset.UTC),
        fundValueQueries,
        pointRepository,
        properties,
        new SriCalculator(),
        new SrriCalculator());
  }

  private static RiskIndicatorProperties singleSourceProperties() {
    return new RiskIndicatorProperties(
        Map.of(
            TKF100,
            List.of(new RiskIndicatorProperties.Source(ACWI, null)),
            TUK75,
            List.of(new RiskIndicatorProperties.Source("EE3600109435", null))),
        Map.of());
  }

  private static RiskIndicatorProperties splicedProperties() {
    return new RiskIndicatorProperties(
        Map.of(
            TKF100,
            List.of(
                new RiskIndicatorProperties.Source(ACWI, null),
                new RiskIndicatorProperties.Source(TKF_ISIN, ANCHOR.minusYears(2)))),
        Map.of());
  }

  private static List<FundValue> dailyPrices(String key, LocalDate from, LocalDate to) {
    return dailyPrices(key, from, to, 100.0);
  }

  private static List<FundValue> dailyPrices(
      String key, LocalDate from, LocalDate to, double startValue) {
    var prices = new ArrayList<FundValue>();
    var value = startValue;
    var date = from;
    var i = 0;
    while (!date.isAfter(to)) {
      if (date.getDayOfWeek().getValue() <= 5) {
        value *= Math.exp(0.003 * Math.sin(i) - 0.0002);
        prices.add(new FundValue(key, date, valueOf(value), "TEST", Instant.EPOCH));
        i++;
      }
      date = date.plusDays(1);
    }
    return prices;
  }
}
