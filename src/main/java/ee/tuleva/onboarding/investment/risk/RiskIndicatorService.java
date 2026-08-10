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

  /**
   * Deliberately not transactional: each fund is caught individually so that one failure cannot
   * take the run down, and a shared transaction would be marked rollback-only by the very exception
   * we just swallowed, failing every other fund at commit.
   */
  RiskIndicatorRun evaluateAllFunds(int lookbackMonths) {
    var outcomes = new ArrayList<RiskIndicatorOutcome>();
    var failures = new ArrayList<String>();

    for (var fund : configuredFunds()) {
      var indicatorType = RiskIndicatorType.forFund(fund);
      try {
        var outcome = evaluate(fund, indicatorType, lookbackMonths);
        if (outcome != null) {
          outcomes.add(outcome);
        }
      } catch (Exception e) {
        log.error("Risk indicator evaluation failed: fund={}, type={}", fund, indicatorType, e);
        failures.add("%s %s: %s".formatted(fund, indicatorType, e.getMessage()));
      }
    }

    return new RiskIndicatorRun(LocalDate.now(clock), outcomes, failures);
  }

  private List<TulevaFund> configuredFunds() {
    return properties.sources().keySet().stream().sorted(Comparator.naturalOrder()).toList();
  }

  private @Nullable RiskIndicatorOutcome evaluate(
      TulevaFund fund, RiskIndicatorType indicatorType, int lookbackMonths) {
    var refresh = seriesService.refreshSeries(fund, indicatorType, lookbackMonths);

    var storedSeries = storedSeries(fund, indicatorType);
    if (storedSeries.isEmpty()) {
      log.warn("No risk indicator reference points: fund={}, type={}", fund, indicatorType);
      return null;
    }

    var previous = snapshotOfLastNotifiedPublication(fund, indicatorType);
    var published = rule(indicatorType).publish(storedSeries);
    var indicator =
        published.isEmpty()
            ? insufficientData(fund, indicatorType, storedSeries)
            : published.analyse(fund, indicatorType, storedSeries);

    return new RiskIndicatorOutcome(indicator, previous, save(indicator), refresh.driftedDates());
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

  /**
   * The baseline for a transition alert is the last publication we actually got out to Slack, not
   * merely the last one stored. If a send failed, the alert has to still be pending against the
   * state the reader last saw.
   */
  private @Nullable PublicationSnapshot snapshotOfLastNotifiedPublication(
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

    // Compare before overwriting. A row whose content moved has not been notified, whatever it
    // said before: recomputing the same evaluation date -- SRRI keeps one for a whole week --
    // must not inherit yesterday's "already told Slack", or a failed send would have nothing left
    // to retry against.
    if (hasChanged(publication, indicator)) {
      publication.setNotified(false);
      publication.setNotifiedDisclosedClass(null);
    }

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

  private boolean hasChanged(
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
      List<LocalDate> driftedDates) {}

  record RiskIndicatorRun(
      LocalDate runDate, List<RiskIndicatorOutcome> outcomes, List<String> failures) {}
}
