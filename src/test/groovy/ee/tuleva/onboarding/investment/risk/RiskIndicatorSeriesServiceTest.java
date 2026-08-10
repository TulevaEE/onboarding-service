package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
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

  @Mock private FundValueRepository fundValueRepository;
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
    verify(fundValueRepository).findValuesBetweenDates(eq(ACWI), start.capture(), eq(ANCHOR));
    assertThat(start.getValue()).isBefore(ANCHOR.minusMonths(1).minusYears(5));
  }

  @Test
  void anchorsOnTheLatestDataDateNotTheClock() {
    var staleAnchor = LocalDate.of(2026, 1, 15);
    var prices = dailyPrices(ACWI, staleAnchor.minusYears(7), staleAnchor);
    given(fundValueRepository.findLastValueForFund(ACWI)).willReturn(Optional.of(prices.getLast()));
    given(fundValueRepository.findValuesBetweenDates(anyString(), any(), any())).willReturn(prices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());

    var points = service.refreshSeries(TKF100, SRI, 1);

    assertThat(points.getLast().date()).isEqualTo(staleAnchor);
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
    var drifted =
        RiskIndicatorPoint.builder()
            .indicatorType(SRI)
            .fund(TKF100)
            .asOfDate(ANCHOR)
            .sourceKeys(ACWI)
            .riskClass(1)
            .observationCount(1)
            .volatility(valueOf(0.999))
            .metrics(Map.of())
            .build();
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of(drifted));

    service.refreshSeries(TKF100, SRI, 1);

    assertThat(drifted.getRiskClass()).isNotEqualTo(1);
    assertThat(drifted.getMetrics()).containsKey("driftHistory");
  }

  @Test
  void doesNotComputeAReturnAcrossASegmentJoin() {
    var acwiPrices = dailyPrices(ACWI, ANCHOR.minusYears(7), ANCHOR.minusYears(2).minusDays(1));
    var navPrices = dailyPrices(TKF_ISIN, ANCHOR.minusYears(2), ANCHOR);
    given(fundValueRepository.findLastValueForFund(TKF_ISIN))
        .willReturn(Optional.of(navPrices.getLast()));
    given(fundValueRepository.findValuesBetweenDates(eq(ACWI), any(), any()))
        .willReturn(acwiPrices);
    given(fundValueRepository.findValuesBetweenDates(eq(TKF_ISIN), any(), any()))
        .willReturn(navPrices);
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100))
        .willReturn(List.of());
    var splicedService = serviceWith(splicedProperties());

    var points = splicedService.refreshSeries(TKF100, SRI, 1);

    var saved = ArgumentCaptor.forClass(List.class);
    verify(pointRepository).saveAll(saved.capture());
    assertThat(((List<RiskIndicatorPoint>) saved.getValue()).getFirst().getSourceKeys())
        .isEqualTo(ACWI + "," + TKF_ISIN);
    assertThat(points).isNotEmpty();
  }

  @Test
  void skipsFundsWithoutSourceData() {
    given(fundValueRepository.findLastValueForFund(anyString())).willReturn(Optional.empty());

    var points = service.refreshSeries(TUK75, SRRI, 1);

    assertThat(points).isEmpty();
  }

  private void givenPrices(List<FundValue> prices) {
    given(fundValueRepository.findLastValueForFund(anyString()))
        .willReturn(Optional.of(prices.getLast()));
    given(fundValueRepository.findValuesBetweenDates(anyString(), any(), any())).willReturn(prices);
  }

  private RiskIndicatorSeriesService serviceWith(RiskIndicatorProperties properties) {
    return new RiskIndicatorSeriesService(
        fundValueRepository,
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
    var prices = new ArrayList<FundValue>();
    var value = 100.0;
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
