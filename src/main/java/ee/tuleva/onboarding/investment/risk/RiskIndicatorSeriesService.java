package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties.Source;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class RiskIndicatorSeriesService {

  private static final int OBSERVATION_WINDOW_YEARS = 5;
  private static final int MAX_SOURCE_AGE_DAYS = 10;
  private static final Period SRI_PRECEDING_PRICE_BUFFER = Period.ofWeeks(2);
  private static final Period SRRI_PRECEDING_PRICE_BUFFER = Period.ofMonths(1);

  private final Clock clock;
  private final FundValueQueries fundValueQueries;
  private final RiskIndicatorPointRepository pointRepository;
  private final RiskIndicatorProperties properties;
  private final SriCalculator sriCalculator;
  private final SrriCalculator srriCalculator;

  SeriesRefresh refreshSeries(
      TulevaFund fund, RiskIndicatorType indicatorType, int lookbackMonths) {
    var segments = properties.sourcesFor(fund);
    var anchor = anchorDate(segments);
    if (anchor == null) {
      log.warn(
          "No source data for risk indicator: fund={}, type={}, sources={}",
          fund,
          indicatorType,
          sourceKeys(segments));
      return SeriesRefresh.empty();
    }
    failIfSourceStoppedUpdating(anchor, segments);

    var evaluationStart = anchor.minusMonths(lookbackMonths);
    var loadStart =
        evaluationStart
            .minusYears(OBSERVATION_WINDOW_YEARS)
            .minus(indicatorType == SRI ? SRI_PRECEDING_PRICE_BUFFER : SRRI_PRECEDING_PRICE_BUFFER);

    var prices = loadSegments(segments, loadStart, anchor);
    if (prices.isEmpty()) {
      return SeriesRefresh.empty();
    }

    var calculated =
        indicatorType == SRI
            ? sriCalculator.calculate(prices, evaluationStart, anchor)
            : new CalculatedSeries(
                srriCalculator.calculate(prices, evaluationStart, anchor), List.of());
    var points = calculated.points();

    var changes = save(fund, indicatorType, sourceKeys(segments), points);
    return new SeriesRefresh(
        points, changes.driftedDates(), changes.redefinitions(), calculated.skippedDates());
  }

  private void failIfSourceStoppedUpdating(LocalDate anchor, List<Source> segments) {
    if (anchor.isBefore(LocalDate.now(clock).minusDays(MAX_SOURCE_AGE_DAYS))) {
      throw new IllegalStateException(
          "source data is stale: lastValue=%s, maxAgeDays=%d, sources=%s"
              .formatted(anchor, MAX_SOURCE_AGE_DAYS, sourceKeys(segments)));
    }
  }

  private List<FundValue> loadSegments(
      List<Source> segments, LocalDate loadStart, LocalDate anchor) {
    var prices = new ArrayList<FundValue>();
    for (int i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      var segmentStart = maxDate(segment.from(), loadStart);
      var segmentEnd = minDate(nextSegmentStart(segments, i), anchor);
      if (segmentStart.isAfter(segmentEnd)) {
        continue;
      }
      prices.addAll(
          fundValueQueries.findValuesBetweenDates(segment.key(), segmentStart, segmentEnd));
    }
    return prices;
  }

  private @Nullable LocalDate nextSegmentStart(List<Source> segments, int index) {
    if (index + 1 >= segments.size()) {
      return null;
    }
    var next = segments.get(index + 1).from();
    return next == null ? null : next.minusDays(1);
  }

  private LocalDate maxDate(@Nullable LocalDate a, LocalDate b) {
    return a == null || a.isBefore(b) ? b : a;
  }

  private LocalDate minDate(@Nullable LocalDate a, LocalDate b) {
    return a == null || a.isAfter(b) ? b : a;
  }

  private @Nullable LocalDate anchorDate(List<Source> segments) {
    var activeKey = segments.getLast().key();
    return fundValueQueries.findLastValueForFund(activeKey).map(FundValue::date).orElse(null);
  }

  private String sourceKeys(List<Source> segments) {
    return segments.stream().map(Source::key).collect(Collectors.joining(","));
  }

  private StoredChanges save(
      TulevaFund fund,
      RiskIndicatorType indicatorType,
      String sourceKeys,
      List<ReferencePoint> points) {
    var existing =
        pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(indicatorType, fund).stream()
            .collect(Collectors.toMap(RiskIndicatorPoint::getAsOfDate, point -> point));

    var toSave = new ArrayList<RiskIndicatorPoint>();
    var drifted = new ArrayList<LocalDate>();
    var redefined = new ArrayList<Redefinition>();
    for (var point : points) {
      var stored = existing.get(point.date());
      if (stored == null) {
        toSave.add(newPoint(fund, indicatorType, sourceKeys, point));
      } else if (!hasDrifted(stored, point)) {
        continue;
      } else if (holdingPeriodChanged(stored, point)) {
        var redefinition = holdingPeriodRedefinition(stored, point);
        toSave.add(applyRedefinition(stored, point, redefinition));
        redefined.add(redefinition);
      } else if (publishedClassChangedWhileTheMeasurementsDidNot(stored, point)) {
        var redefinition = publicationRuleRedefinition(stored, point);
        toSave.add(applyRedefinition(stored, point, redefinition));
        redefined.add(redefinition);
      } else {
        toSave.add(applyDrift(stored, point));
        drifted.add(point.date());
      }
    }
    pointRepository.saveAll(toSave);
    return new StoredChanges(List.copyOf(drifted), List.copyOf(redefined));
  }

  private record StoredChanges(List<LocalDate> driftedDates, List<Redefinition> redefinitions) {}

  private boolean holdingPeriodChanged(RiskIndicatorPoint stored, ReferencePoint recomputed) {
    var current = recomputedHoldingPeriod(recomputed);
    return current != null && !current.equals(storedHoldingPeriod(stored));
  }

  private Redefinition holdingPeriodRedefinition(
      RiskIndicatorPoint stored, ReferencePoint recomputed) {
    return new Redefinition.HoldingPeriod(
        stored.getAsOfDate(),
        storedHoldingPeriod(stored),
        Objects.requireNonNull(recomputedHoldingPeriod(recomputed)));
  }

  private boolean publishedClassChangedWhileTheMeasurementsDidNot(
      RiskIndicatorPoint stored, ReferencePoint recomputed) {
    var storedVolatility = stored.getVolatility();
    return storedVolatility != null
        && storedVolatility.compareTo(recomputed.volatility()) == 0
        && Objects.equals(stored.getObservationCount(), recomputed.observationCount());
  }

  private Redefinition publicationRuleRedefinition(
      RiskIndicatorPoint stored, ReferencePoint recomputed) {
    return new Redefinition.PublicationRule(
        stored.getAsOfDate(), stored.getRiskClass(), recomputed.riskClass());
  }

  private @Nullable String storedHoldingPeriod(RiskIndicatorPoint stored) {
    var value = stored.getMetrics().get(SriCalculator.HOLDING_PERIOD_METRIC);
    return value == null ? null : String.valueOf(value);
  }

  private @Nullable String recomputedHoldingPeriod(ReferencePoint recomputed) {
    var value = recomputed.metrics().get(SriCalculator.HOLDING_PERIOD_METRIC);
    return value == null ? null : String.valueOf(value);
  }

  private RiskIndicatorPoint applyRedefinition(
      RiskIndicatorPoint stored, ReferencePoint recomputed, Redefinition redefinition) {
    log.info(
        "Risk indicator point recomputed under a new definition: fund={}, type={}, date={},"
            + " storedClass={}, recomputedClass={}, storedVolatility={}, recomputedVolatility={},"
            + " redefinition={}",
        stored.getFund(),
        stored.getIndicatorType(),
        stored.getAsOfDate(),
        stored.getRiskClass(),
        recomputed.riskClass(),
        stored.getVolatility(),
        recomputed.volatility(),
        redefinition);

    var metrics = new HashMap<String, Object>(stringKeyed(recomputed.metrics()));
    var history = driftHistory(stored);
    if (!history.isEmpty()) {
      metrics.put("driftHistory", history);
    }
    return apply(stored, recomputed, metrics);
  }

  record SeriesRefresh(
      List<ReferencePoint> points,
      List<LocalDate> driftedDates,
      List<Redefinition> redefinitions,
      List<LocalDate> skippedDates) {
    static SeriesRefresh empty() {
      return new SeriesRefresh(List.of(), List.of(), List.of(), List.of());
    }
  }

  private RiskIndicatorPoint newPoint(
      TulevaFund fund, RiskIndicatorType indicatorType, String sourceKeys, ReferencePoint point) {
    return RiskIndicatorPoint.builder()
        .indicatorType(indicatorType)
        .fund(fund)
        .asOfDate(point.date())
        .sourceKeys(sourceKeys)
        .riskClass(point.riskClass())
        .observationCount(point.observationCount())
        .volatility(point.volatility())
        .metrics(stringKeyed(point.metrics()))
        .build();
  }

  private boolean hasDrifted(RiskIndicatorPoint stored, ReferencePoint recomputed) {
    var storedVolatility = stored.getVolatility();
    return !Objects.equals(stored.getRiskClass(), recomputed.riskClass())
        || !Objects.equals(stored.getObservationCount(), recomputed.observationCount())
        || storedVolatility == null
        || storedVolatility.compareTo(recomputed.volatility()) != 0;
  }

  private RiskIndicatorPoint applyDrift(RiskIndicatorPoint stored, ReferencePoint recomputed) {
    log.warn(
        "Risk indicator point drifted: fund={}, type={}, date={}, storedClass={},"
            + " recomputedClass={}, storedVolatility={}, recomputedVolatility={}",
        stored.getFund(),
        stored.getIndicatorType(),
        stored.getAsOfDate(),
        stored.getRiskClass(),
        recomputed.riskClass(),
        stored.getVolatility(),
        recomputed.volatility());

    var metrics = new HashMap<String, Object>(stringKeyed(recomputed.metrics()));
    var history = new ArrayList<>(driftHistory(stored));
    history.add(
        Map.of(
            "detectedAt",
            LocalDate.now(clock).toString(),
            "previousClass",
            String.valueOf(stored.getRiskClass()),
            "previousVolatility",
            String.valueOf(stored.getVolatility()),
            "newClass",
            String.valueOf(recomputed.riskClass())));
    metrics.put("driftHistory", history);
    return apply(stored, recomputed, metrics);
  }

  private RiskIndicatorPoint apply(
      RiskIndicatorPoint stored, ReferencePoint recomputed, Map<String, Object> metrics) {
    stored.setRiskClass(recomputed.riskClass());
    stored.setObservationCount(recomputed.observationCount());
    stored.setVolatility(recomputed.volatility());
    stored.setMetrics(metrics);
    return stored;
  }

  @SuppressWarnings("unchecked")
  private List<Object> driftHistory(RiskIndicatorPoint stored) {
    var history = stored.getMetrics().get("driftHistory");
    return history instanceof List<?> list ? List.copyOf((List<Object>) list) : List.of();
  }

  private Map<String, Object> stringKeyed(Map<String, Object> metrics) {
    return metrics.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
  }
}
