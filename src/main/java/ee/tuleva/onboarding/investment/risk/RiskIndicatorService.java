package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskIndicatorService {

  static final int DEFAULT_LOOKBACK_MONTHS = 28;

  private final RiskIndicatorSeriesService seriesService;
  private final RiskIndicatorPointRepository pointRepository;
  private final RiskIndicatorPublicationRepository publicationRepository;
  private final RiskIndicatorProperties properties;
  private final MajorityPublicationRule majorityRule;
  private final PersistencePublicationRule persistenceRule;
  private final Clock clock;

  // Not @Transactional: a caught exception would mark a shared transaction rollback-only.
  RiskIndicatorRun evaluateAllFunds(int lookbackMonths) {
    var outcomes = new ArrayList<RiskIndicatorOutcome>();
    var failures = new ArrayList<String>();

    for (var fund : configuredFunds()) {
      var indicatorType = RiskIndicatorType.forFund(fund);
      try {
        outcomes.add(evaluate(fund, indicatorType, lookbackMonths));
      } catch (Exception e) {
        failures.add(failureOfOneFundThatMustNotFailTheRest(fund, indicatorType, e));
      }
    }

    return new RiskIndicatorRun(LocalDate.now(clock), outcomes, failures);
  }

  private String failureOfOneFundThatMustNotFailTheRest(
      TulevaFund fund, RiskIndicatorType indicatorType, Exception e) {
    log.error("Risk indicator evaluation failed: fund={}, type={}", fund, indicatorType, e);
    return "%s %s: %s".formatted(fund, indicatorType, e.getMessage());
  }

  private List<TulevaFund> configuredFunds() {
    return properties.sources().keySet().stream().sorted(Comparator.naturalOrder()).toList();
  }

  private RiskIndicatorOutcome evaluate(
      TulevaFund fund, RiskIndicatorType indicatorType, int lookbackMonths) {
    var refresh = seriesService.refreshSeries(fund, indicatorType, lookbackMonths);

    var storedSeries = storedSeries(fund, indicatorType);
    if (storedSeries.isEmpty()) {
      throw new IllegalStateException("no reference points stored");
    }

    var previous = snapshotOfLastPublicationTheReaderSaw(fund, indicatorType);
    var published = rule(indicatorType).publish(storedSeries);
    var indicator =
        published.isEmpty()
            ? insufficientData(fund, indicatorType, storedSeries)
            : published.analyse(fund, indicatorType, storedSeries);

    return new RiskIndicatorOutcome(
        indicator,
        previous,
        save(indicator),
        refresh.driftedDates(),
        refresh.redefinitions(),
        refresh.skippedDates());
  }

  private PublishedRiskIndicator insufficientData(
      TulevaFund fund, RiskIndicatorType indicatorType, List<ReferencePoint> storedSeries) {
    var latest = storedSeries.getLast();
    return PublishedRiskIndicator.insufficientData(
        fund, indicatorType, latest.date(), latest.observationCount(), latest.volatility());
  }

  private List<ReferencePoint> storedSeries(TulevaFund fund, RiskIndicatorType indicatorType) {
    return pointRepository
        .findByIndicatorTypeAndFundOrderByAsOfDateAsc(indicatorType, fund)
        .stream()
        .map(
            point ->
                new ReferencePoint(
                    point.getAsOfDate(),
                    point.getRiskClass(),
                    point.getObservationCount(),
                    point.getVolatility(),
                    point.getMetrics()))
        .toList();
  }

  private PublicationRule rule(RiskIndicatorType indicatorType) {
    return indicatorType == SRI ? majorityRule : persistenceRule;
  }

  private @Nullable PublicationSnapshot snapshotOfLastPublicationTheReaderSaw(
      TulevaFund fund, RiskIndicatorType indicatorType) {
    return publicationRepository
        .findFirstByIndicatorTypeAndFundAndNotifiedTrueOrderByEvaluationDateDesc(
            indicatorType, fund)
        .map(
            publication ->
                new PublicationSnapshot(
                    publication.getEvaluationDate(),
                    publication.getPublishedClass(),
                    publication.getNotifiedDisclosedClass(),
                    publication.getStatus()))
        .orElse(null);
  }

  private RiskIndicatorPublication save(PublishedRiskIndicator indicator) {
    var publication =
        publicationRepository
            .findByIndicatorTypeAndFundAndEvaluationDate(
                indicator.indicatorType(), indicator.fund(), indicator.evaluationDate())
            .orElseGet(
                () ->
                    RiskIndicatorPublication.builder()
                        .indicatorType(indicator.indicatorType())
                        .fund(indicator.fund())
                        .evaluationDate(indicator.evaluationDate())
                        .build());

    clearNotifiedStateWhenContentMoved(publication, indicator);

    publication.setPublishedClass(indicator.publishedClass());
    publication.setRawLatestClass(indicator.rawLatestClass());
    publication.setPreviousPublishedClass(indicator.previousPublishedClass());
    publication.setPublishedSince(indicator.publishedSince());
    publication.setStreakReferencePoints(indicator.streakReferencePoints());
    publication.setWindowReferencePoints(indicator.windowReferencePoints());
    publication.setMatchingReferencePoints(indicator.matchingReferencePoints());
    publication.setStatus(indicator.status());
    publication.setDetails(details(indicator));

    var saved = publicationRepository.save(publication);

    log.info(
        "Risk indicator published: fund={}, type={}, date={}, publishedClass={}, rawClass={},"
            + " since={}, status={}, window={}/{}",
        indicator.fund(),
        indicator.indicatorType(),
        indicator.evaluationDate(),
        indicator.publishedClass(),
        indicator.rawLatestClass(),
        indicator.publishedSince(),
        indicator.status(),
        indicator.matchingReferencePoints(),
        indicator.windowReferencePoints());

    return saved;
  }

  private void clearNotifiedStateWhenContentMoved(
      RiskIndicatorPublication publication, PublishedRiskIndicator indicator) {
    if (contentMovedSinceLastPublication(publication, indicator)) {
      publication.setNotified(false);
      publication.setNotifiedDisclosedClass(null);
    }
  }

  private boolean contentMovedSinceLastPublication(
      RiskIndicatorPublication publication, PublishedRiskIndicator indicator) {
    return !Objects.equals(publication.getPublishedClass(), indicator.publishedClass())
        || publication.getStatus() != indicator.status();
  }

  private Map<String, Object> details(PublishedRiskIndicator indicator) {
    var details = new HashMap<String, Object>();
    details.put("latestObservationCount", String.valueOf(indicator.latestObservationCount()));
    details.put("latestVolatility", String.valueOf(indicator.latestVolatility()));
    details.put("rawClassSince", String.valueOf(indicator.rawClassSince()));
    details.put("rawStreakReferencePoints", String.valueOf(indicator.rawStreakReferencePoints()));
    return details;
  }

  record PublicationSnapshot(
      LocalDate evaluationDate,
      @Nullable Integer publishedClass,
      @Nullable Integer notifiedDisclosedClass,
      RiskIndicatorStatus status) {}

  record RiskIndicatorOutcome(
      PublishedRiskIndicator indicator,
      @Nullable PublicationSnapshot previous,
      RiskIndicatorPublication publication,
      List<LocalDate> driftedDates,
      List<Redefinition> redefinitions,
      List<LocalDate> skippedDates) {}

  record RiskIndicatorRun(
      LocalDate runDate, List<RiskIndicatorOutcome> outcomes, List<String> failures) {}
}
