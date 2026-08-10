package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties.Source;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RiskIndicatorServiceTest {

  private static final LocalDate START = LocalDate.of(2024, 1, 1);
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 6);

  private final RiskIndicatorSeriesService seriesService =
      Mockito.mock(RiskIndicatorSeriesService.class);
  private final RiskIndicatorPointRepository pointRepository =
      Mockito.mock(RiskIndicatorPointRepository.class);
  private final RiskIndicatorPublicationRepository publicationRepository =
      Mockito.mock(RiskIndicatorPublicationRepository.class);

  private final RiskIndicatorProperties properties =
      new RiskIndicatorProperties(
          Map.of(
              TKF100, List.of(new Source("MSCI_ACWI", null)),
              TUK75, List.of(new Source(TUK75.getIsin(), null))),
          Map.of());

  private final RiskIndicatorService service =
      new RiskIndicatorService(
          seriesService,
          pointRepository,
          publicationRepository,
          properties,
          new MajorityPublicationRule(),
          new PersistencePublicationRule(),
          Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(seriesService.refreshSeries(any(), any(), anyInt()))
        .willReturn(RiskIndicatorSeriesService.SeriesRefresh.empty());
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(any(), any()))
        .willReturn(List.of());
    given(
            publicationRepository
                .findFirstByIndicatorTypeAndFundAndNotifiedTrueOrderByEvaluationDateDesc(
                    any(), any()))
        .willReturn(Optional.empty());
    given(publicationRepository.findByIndicatorTypeAndFundAndEvaluationDate(any(), any(), any()))
        .willReturn(Optional.empty());
    given(publicationRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void eachFundIsEvaluatedUnderTheIndicatorItsRegulationPrescribes() {
    storedPoints(SRI, TKF100, daily(200, 4));
    storedPoints(SRRI, TUK75, weekly(40, 5));

    var run = service.evaluateAllFunds(28);

    assertThat(run.outcomes())
        .extracting(
            outcome -> outcome.indicator().fund(), outcome -> outcome.indicator().indicatorType())
        .containsExactly(tuple(TUK75, SRRI), tuple(TKF100, SRI));
  }

  @Test
  void thePublicationIsPersistedWithTheAnalysedFigures() {
    storedPoints(SRI, TKF100, daily(200, 4));

    var run = service.evaluateAllFunds(28);

    var indicator = onlyIndicator(run);
    assertThat(indicator.publishedClass()).isEqualTo(4);
    assertThat(indicator.rawLatestClass()).isEqualTo(4);
    assertThat(indicator.status()).isEqualTo(STABLE);
    Mockito.verify(publicationRepository)
        .save(
            RiskIndicatorPublication.builder()
                .indicatorType(SRI)
                .fund(TKF100)
                .evaluationDate(indicator.evaluationDate())
                .publishedClass(4)
                .rawLatestClass(4)
                .previousPublishedClass(null)
                .publishedSince(indicator.publishedSince())
                .streakReferencePoints(indicator.streakReferencePoints())
                .windowReferencePoints(indicator.windowReferencePoints())
                .matchingReferencePoints(indicator.matchingReferencePoints())
                .status(STABLE)
                .details(
                    Map.of(
                        "latestObservationCount",
                        "260",
                        "latestVolatility",
                        "0.15",
                        "rawClassSince",
                        String.valueOf(indicator.rawClassSince()),
                        "rawStreakReferencePoints",
                        String.valueOf(indicator.rawStreakReferencePoints())))
                .build());
  }

  @Test
  void thePreviousPublicationIsReadBeforeTodayIsWritten() {
    storedPoints(SRI, TKF100, daily(200, 4));
    given(
            publicationRepository
                .findFirstByIndicatorTypeAndFundAndNotifiedTrueOrderByEvaluationDateDesc(
                    SRI, TKF100))
        .willReturn(
            Optional.of(
                RiskIndicatorPublication.builder()
                    .indicatorType(SRI)
                    .fund(TKF100)
                    .evaluationDate(START)
                    .publishedClass(3)
                    .status(STABLE)
                    .build()));

    var run = service.evaluateAllFunds(28);

    assertThat(run.outcomes().getFirst().previous())
        .isEqualTo(new RiskIndicatorService.PublicationSnapshot(START, 3, STABLE));
  }

  @Test
  void oneFailingFundDoesNotStopTheRest() {
    storedPoints(SRI, TKF100, daily(200, 4));
    willThrow(new IllegalStateException("no source data"))
        .given(seriesService)
        .refreshSeries(eq(TUK75), any(), anyInt());

    var run = service.evaluateAllFunds(28);

    assertThat(run.outcomes()).hasSize(1);
    assertThat(run.failures()).containsExactly("TUK75 SRRI: no source data");
  }

  @Test
  void aFundWithoutReferencePointsProducesNoOutcome() {
    var run = service.evaluateAllFunds(28);

    assertThat(run.outcomes()).isEmpty();
    assertThat(run.failures()).isEmpty();
  }

  @Test
  void aSeriesWithoutAnyClassifiedPointReportsInsufficientData() {
    storedPoints(SRRI, TUK75, weekly(10, null));

    var run = service.evaluateAllFunds(28);

    var indicator = onlyIndicator(run);
    assertThat(indicator.hasClass()).isFalse();
    assertThat(indicator.latestObservationCount()).isEqualTo(260);
  }

  private PublishedRiskIndicator onlyIndicator(RiskIndicatorService.RiskIndicatorRun run) {
    assertThat(run.outcomes()).hasSize(1);
    return run.outcomes().getFirst().indicator();
  }

  private void storedPoints(
      RiskIndicatorType indicatorType, TulevaFund fund, List<RiskIndicatorPoint> points) {
    given(pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(indicatorType, fund))
        .willReturn(points);
  }

  private List<RiskIndicatorPoint> daily(int count, @Nullable Integer riskClass) {
    return series(count, riskClass, 1);
  }

  private List<RiskIndicatorPoint> weekly(int count, @Nullable Integer riskClass) {
    return series(count, riskClass, 7);
  }

  private List<RiskIndicatorPoint> series(int count, @Nullable Integer riskClass, int dayStep) {
    var points = new ArrayList<RiskIndicatorPoint>(count);
    for (int i = 0; i < count; i++) {
      points.add(
          RiskIndicatorPoint.builder()
              .asOfDate(START.plusDays((long) i * dayStep))
              .riskClass(riskClass)
              .observationCount(260)
              .volatility(new BigDecimal("0.15"))
              .metrics(Map.of())
              .build());
    }
    return points;
  }

  private static org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
