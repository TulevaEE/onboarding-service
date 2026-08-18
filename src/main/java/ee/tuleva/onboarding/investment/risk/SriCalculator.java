package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SriCalculator {

  // PRIIPs Annex II point 52: SRI equals the MRM class only while the credit risk measure is 1.
  static final int ASSUMED_CREDIT_RISK_MEASURE = 1;

  static final double Z = 1.95996398454005;
  static final double SKEW_COEFFICIENT = 0.47357647;
  static final double KURTOSIS_COEFFICIENT = 0.068717874;
  static final double SKEW_SQUARED_COEFFICIENT = 0.146067276;

  // 256 = 365 - 104 weekend - 5 public holidays, per the ESAs' PRIIPs flow diagram JC 2017 49.
  private static final int TRADING_DAYS_PER_YEAR = 256;

  // PRIIPs Annex II point 10: never fewer than two years of observed returns.
  private static final int MINIMUM_OBSERVATION_YEARS = 2;
  private static final int MINIMUM_OBSERVATIONS = 400;
  private static final int RECOMMENDED_HOLDING_PERIOD_YEARS = 5;
  private static final int OBSERVATION_WINDOW_YEARS = 5;
  private static final int MINIMUM_RETURNS = 2;
  private static final int VOLATILITY_SCALE = 12;

  static final int HOLDING_PERIOD_TRADING_DAYS =
      RECOMMENDED_HOLDING_PERIOD_YEARS * TRADING_DAYS_PER_YEAR;

  static final String HOLDING_PERIOD_METRIC = "holdingPeriodTradingDays";

  CalculatedSeries calculate(List<FundValue> prices, LocalDate from, LocalDate to) {
    var series = tradingDayPrices(prices);
    if (series.size() <= MINIMUM_RETURNS) {
      return CalculatedSeries.empty();
    }
    var returns = logReturnsWithinOneInstrument(series);
    var evaluations =
        series.stream()
            .skip(1)
            .map(FundValue::date)
            .filter(date -> !date.isBefore(from) && !date.isAfter(to))
            .map(date -> evaluate(returns, date))
            .toList();
    return new CalculatedSeries(
        evaluations.stream().map(Evaluation::point).filter(Objects::nonNull).toList(),
        evaluations.stream().filter(Evaluation::skipped).map(Evaluation::date).toList());
  }

  private List<FundValue> tradingDayPrices(List<FundValue> prices) {
    return prices.stream()
        .filter(price -> price.date().getDayOfWeek().getValue() <= 5)
        .filter(price -> price.value().signum() > 0)
        .sorted(Comparator.comparing(FundValue::date))
        .toList();
  }

  private List<DatedReturn> logReturnsWithinOneInstrument(List<FundValue> series) {
    var returns = new ArrayList<DatedReturn>(series.size());
    for (int i = 1; i < series.size(); i++) {
      var current = series.get(i);
      var previous = series.get(i - 1);
      if (!isSameInstrument(previous, current)) {
        continue;
      }
      returns.add(
          new DatedReturn(
              current.date(),
              previous.date(),
              Math.log(current.value().doubleValue() / previous.value().doubleValue())));
    }
    return returns;
  }

  private boolean isSameInstrument(FundValue previous, FundValue current) {
    return current.key().equals(previous.key());
  }

  private Evaluation evaluate(List<DatedReturn> returns, LocalDate evalDate) {
    var windowStart = evalDate.minusYears(OBSERVATION_WINDOW_YEARS);
    var window =
        returns.stream()
            .filter(r -> r.date().isAfter(windowStart) && !r.date().isAfter(evalDate))
            .toList();
    if (window.size() < MINIMUM_RETURNS) {
      return Evaluation.none(evalDate);
    }
    return evaluate(
        evalDate,
        window.stream().mapToDouble(DatedReturn::value).toArray(),
        window.getFirst().previousDate());
  }

  private boolean isPublishable(int observations, LocalDate observedFrom, LocalDate evalDate) {
    var minimumHistoryStart = evalDate.minusYears(MINIMUM_OBSERVATION_YEARS);
    return observations >= MINIMUM_OBSERVATIONS && !observedFrom.isAfter(minimumHistoryStart);
  }

  private Evaluation evaluate(LocalDate evalDate, double[] window, LocalDate observedFrom) {
    int n = window.length;
    var mean = Arrays.stream(window).average().orElseThrow();

    double s2 = 0;
    double s3 = 0;
    double s4 = 0;
    for (double value : window) {
      var deviation = value - mean;
      var squared = deviation * deviation;
      s2 += squared;
      s3 += squared * deviation;
      s4 += squared * squared;
    }

    var m2 = s2 / n;
    var sigma = Math.sqrt(m2);
    var skew = m2 > 0 ? (s3 / n) / (sigma * sigma * sigma) : 0.0;
    var excessKurtosis = m2 > 0 ? (s4 / n) / (sigma * sigma * sigma * sigma) - 3.0 : 0.0;

    var valueAtRisk = valueAtRiskOverHoldingPeriod(sigma, skew, excessKurtosis);
    var vev =
        (Math.sqrt(Z * Z - 2 * valueAtRisk) - Z) / Math.sqrt(RECOMMENDED_HOLDING_PERIOD_YEARS);
    if (!Double.isFinite(vev)) {
      log.warn(
          "Skipping unusable risk indicator reference point: date={}, observations={}, sigma={},"
              + " skew={}, excessKurtosis={}, valueAtRisk={}",
          evalDate,
          n,
          sigma,
          skew,
          excessKurtosis,
          valueAtRisk);
      return Evaluation.skipped(evalDate);
    }

    var volatility = BigDecimal.valueOf(vev).setScale(VOLATILITY_SCALE, RoundingMode.HALF_UP);
    return Evaluation.of(
        new ReferencePoint(
            evalDate,
            isPublishable(n, observedFrom, evalDate) ? RiskClassBucket.mrmClass(vev) : null,
            n,
            volatility,
            Map.of(
                "dailySigma",
                sigma,
                "skew",
                skew,
                "excessKurtosis",
                excessKurtosis,
                "valueAtRisk",
                valueAtRisk,
                HOLDING_PERIOD_METRIC,
                HOLDING_PERIOD_TRADING_DAYS)));
  }

  private double valueAtRiskOverHoldingPeriod(double sigma, double skew, double excessKurtosis) {
    var horizon = HOLDING_PERIOD_TRADING_DAYS;
    var rootHorizon = Math.sqrt(horizon);
    return -sigma
            * rootHorizon
            * (Z
                - SKEW_COEFFICIENT * (skew / rootHorizon)
                + KURTOSIS_COEFFICIENT * (excessKurtosis / horizon)
                - SKEW_SQUARED_COEFFICIENT * (skew * skew / horizon))
        - 0.5 * sigma * sigma * horizon;
  }

  private record DatedReturn(LocalDate date, LocalDate previousDate, double value) {}

  private record Evaluation(LocalDate date, @Nullable ReferencePoint point, boolean skipped) {

    static Evaluation of(ReferencePoint point) {
      return new Evaluation(point.date(), point, false);
    }

    static Evaluation none(LocalDate date) {
      return new Evaluation(date, null, false);
    }

    static Evaluation skipped(LocalDate date) {
      return new Evaluation(date, null, true);
    }
  }
}
