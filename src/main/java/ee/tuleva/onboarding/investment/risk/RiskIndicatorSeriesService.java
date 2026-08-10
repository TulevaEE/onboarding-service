package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class RiskIndicatorSeriesService {

  private static final int OBSERVATION_WINDOW_YEARS = 5;
  private static final Period SRI_PRECEDING_PRICE_BUFFER = Period.ofWeeks(2);
  private static final Period SRRI_PRECEDING_PRICE_BUFFER = Period.ofMonths(1);

  private final java.time.Clock clock;
  private final FundValueRepository fundValueRepository;
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

    var evaluationStart = anchor.minusMonths(lookbackMonths);
    var loadStart =
        evaluationStart
            .minusYears(OBSERVATION_WINDOW_YEARS)
            .minus(indicatorType == SRI ? SRI_PRECEDING_PRICE_BUFFER : SRRI_PRECEDING_PRICE_BUFFER);

    var prices = loadSegments(segments, loadStart, anchor);
    if (prices.isEmpty()) {
      return SeriesRefresh.empty();
    }

    var points =
        indicatorType == SRI
            ? sriCalculator.calculate(prices, evaluationStart, anchor)
            : srriCalculator.calculate(prices, evaluationStart, anchor);

    return new SeriesRefresh(points, save(fund, indicatorType, sourceKeys(segments), points));
  }

  private List<FundValue> loadSegments(
      List<RiskIndicatorProperties.Source> segments, LocalDate loadStart, LocalDate anchor) {
    var prices = new ArrayList<FundValue>();
    for (int i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      var segmentStart = maxDate(segment.from(), loadStart);
      var segmentEnd = minDate(nextSegmentStart(segments, i), anchor);
      if (segmentStart.isAfter(segmentEnd)) {
        continue;
      }
      prices.addAll(
          fundValueRepository.findValuesBetweenDates(segment.key(), segmentStart, segmentEnd));
    }
    return prices;
  }

  private @org.jspecify.annotations.Nullable LocalDate nextSegmentStart(
      List<RiskIndicatorProperties.Source> segments, int index) {
    if (index + 1 >= segments.size()) {
      return null;
    }
    var next = segments.get(index + 1).from();
    return next == null ? null : next.minusDays(1);
  }

  private LocalDate maxDate(@org.jspecify.annotations.Nullable LocalDate a, LocalDate b) {
    return a == null || a.isBefore(b) ? b : a;
  }

  private LocalDate minDate(@org.jspecify.annotations.Nullable LocalDate a, LocalDate b) {
    return a == null || a.isAfter(b) ? b : a;
  }

  private @org.jspecify.annotations.Nullable LocalDate anchorDate(
      List<RiskIndicatorProperties.Source> segments) {
    var activeKey = segments.getLast().key();
    return fundValueRepository.findLastValueForFund(activeKey).map(FundValue::date).orElse(null);
  }

  private String sourceKeys(List<RiskIndicatorProperties.Source> segments) {
    return segments.stream()
        .map(RiskIndicatorProperties.Source::key)
        .collect(Collectors.joining(","));
  }

  private List<LocalDate> save(
      TulevaFund fund,
      RiskIndicatorType indicatorType,
      String sourceKeys,
      List<ReferencePoint> points) {
    var existing =
        pointRepository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(indicatorType, fund).stream()
            .collect(Collectors.toMap(RiskIndicatorPoint::getAsOfDate, point -> point));

    var toSave = new ArrayList<RiskIndicatorPoint>();
    var drifted = new ArrayList<LocalDate>();
    for (var point : points) {
      var stored = existing.get(point.date());
      if (stored == null) {
        toSave.add(newPoint(fund, indicatorType, sourceKeys, point));
      } else if (hasDrifted(stored, point)) {
        toSave.add(applyDrift(stored, point));
        drifted.add(point.date());
      }
    }
    pointRepository.saveAll(toSave);
    return drifted;
  }

  /**
   * A recomputation that moved an already-stored point means the source data changed underneath us.
   * The dates travel back to the caller so the digest can say so — drift that only ever reaches the
   * log is drift nobody finds.
   */
  record SeriesRefresh(List<ReferencePoint> points, List<LocalDate> driftedDates) {
    static SeriesRefresh empty() {
      return new SeriesRefresh(List.of(), List.of());
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
    return !java.util.Objects.equals(stored.getRiskClass(), recomputed.riskClass())
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

    var metrics = new java.util.HashMap<String, Object>(stringKeyed(recomputed.metrics()));
    var history = new ArrayList<>(driftHistory(stored));
    history.add(
        java.util.Map.of(
            "detectedAt",
            LocalDate.now(clock).toString(),
            "previousClass",
            String.valueOf(stored.getRiskClass()),
            "previousVolatility",
            String.valueOf(stored.getVolatility()),
            "newClass",
            String.valueOf(recomputed.riskClass())));
    metrics.put("driftHistory", history);

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

  private java.util.Map<String, Object> stringKeyed(java.util.Map<String, Object> metrics) {
    return metrics.entrySet().stream()
        .collect(Collectors.toMap(java.util.Map.Entry::getKey, e -> String.valueOf(e.getValue())));
  }
}
