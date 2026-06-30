package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingDifferenceQueryServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 6, 5);

  @Mock private TrackingDifferenceEventRepository eventRepository;
  @Mock private TrackingDifferenceCalculator calculator;
  @InjectMocks private TrackingDifferenceQueryService queryService;

  @Test
  void findLatestBenchmarkModel_returnsTrackingDifferenceAndLimit() {
    var event =
        TrackingDifferenceEvent.builder()
            .fund(TUK00)
            .checkDate(DATE)
            .checkType(BENCHMARK_MODEL)
            .trackingDifference(new BigDecimal("0.001073"))
            .fundReturn(new BigDecimal("0.000170"))
            .benchmarkReturn(new BigDecimal("-0.000903"))
            .build();
    given(eventRepository.findDeduplicatedEventsForPeriod(TUK00, BENCHMARK_MODEL, DATE, DATE))
        .willReturn(List.of(event));
    given(calculator.breachThreshold(DATE)).willReturn(new BigDecimal("0.001"));

    var result = queryService.findLatestBenchmarkModel(TUK00, DATE);

    assertThat(result)
        .contains(
            new TrackingDifferenceSummary(new BigDecimal("0.001073"), new BigDecimal("0.001")));
  }

  @Test
  void findLatestBenchmarkModel_returnsEmptyWhenNoEvent() {
    given(eventRepository.findDeduplicatedEventsForPeriod(TUK00, BENCHMARK_MODEL, DATE, DATE))
        .willReturn(List.of());

    var result = queryService.findLatestBenchmarkModel(TUK00, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void findLatestModelPortfolio_returnsTrackingDifferenceAndLimit() {
    var event =
        TrackingDifferenceEvent.builder()
            .fund(TUK00)
            .checkDate(DATE)
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.001500"))
            .fundReturn(new BigDecimal("0.010000"))
            .benchmarkReturn(new BigDecimal("0.008500"))
            .build();
    given(eventRepository.findDeduplicatedEventsForPeriod(TUK00, MODEL_PORTFOLIO, DATE, DATE))
        .willReturn(List.of(event));
    given(calculator.breachThreshold(DATE)).willReturn(new BigDecimal("0.001"));

    var result = queryService.findLatestModelPortfolio(TUK00, DATE);

    assertThat(result)
        .contains(
            new TrackingDifferenceSummary(new BigDecimal("0.001500"), new BigDecimal("0.001")));
  }

  @Test
  void findLatestModelPortfolio_returnsEmptyWhenNoEvent() {
    given(eventRepository.findDeduplicatedEventsForPeriod(TUK00, MODEL_PORTFOLIO, DATE, DATE))
        .willReturn(List.of());

    var result = queryService.findLatestModelPortfolio(TUK00, DATE);

    assertThat(result).isEmpty();
  }
}
