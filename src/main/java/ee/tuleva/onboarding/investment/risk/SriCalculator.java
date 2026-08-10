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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class SriCalculator {

  /**
   * Annex II p52: the SRI is read off the MRM/CRM grid, and it equals the MRM class only while the
   * credit risk measure is 1. TKF100 holds no credit-risky instruments, so this is an assumption
   * carried in the digest footnote rather than something computed here.
   */
  static final int ASSUMED_CREDIT_RISK_MEASURE = 1;

  static final double Z = 1.95996398454005;
  static final double SKEW_COEFFICIENT = 0.47357647;
  static final double KURTOSIS_COEFFICIENT = 0.068717874;
  static final double SKEW_SQUARED_COEFFICIENT = 0.146067276;

  private static final int RECOMMENDED_HOLDING_PERIOD_YEARS = 5;
  private static final int OBSERVATION_WINDOW_YEARS = 5;
  private static final int MINIMUM_RETURNS = 2;
  private static final int VOLATILITY_SCALE = 12;

  List<ReferencePoint> calculate(List<FundValue> prices, LocalDate from, LocalDate to) {
    var series = tradingDayPrices(prices);
    if (series.size() <= MINIMUM_RETURNS) {
      return List.of();
    }
    var returns = logReturns(series);
    return series.stream()
        .skip(1)
        .map(FundValue::date)
        .filter(date -> !date.isBefore(from) && !date.isAfter(to))
        .map(date -> referencePoint(returns, date))
        .filter(Objects::nonNull)
        .toList();
  }

  private List<FundValue> tradingDayPrices(List<FundValue> prices) {
    return prices.stream()
        .filter(price -> price.date().getDayOfWeek().getValue() <= 5)
        .filter(price -> price.value().signum() > 0)
        .sorted(Comparator.comparing(FundValue::date))
        .toList();
  }

  /**
   * A return is only defined between two prices of the same instrument. Where a proxy series is
   * spliced to the fund's own NAV, the two sides are on completely different scales — an index
   * level around 2000 against a NAV around 1 — so a return across the join would be a fiction large
   * enough to dominate sigma and drive the class to 7.
   */
  private List<DatedReturn> logReturns(List<FundValue> series) {
    var returns = new ArrayList<DatedReturn>(series.size());
    for (int i = 1; i < series.size(); i++) {
      var current = series.get(i);
      var previous = series.get(i - 1);
      if (!current.key().equals(previous.key())) {
        continue;
      }
      returns.add(
          new DatedReturn(
              current.date(),
              Math.log(current.value().doubleValue() / previous.value().doubleValue())));
    }
    return returns;
  }

  private @Nullable ReferencePoint referencePoint(List<DatedReturn> returns, LocalDate evalDate) {
    var windowStart = evalDate.minusYears(OBSERVATION_WINDOW_YEARS);
    var window =
        returns.stream()
            .filter(r -> r.date().isAfter(windowStart) && !r.date().isAfter(evalDate))
            .mapToDouble(DatedReturn::value)
            .toArray();
    if (window.length < MINIMUM_RETURNS) {
      return null;
    }
    return referencePoint(evalDate, window);
  }

  private ReferencePoint referencePoint(LocalDate evalDate, double[] window) {
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

    var valueAtRisk = valueAtRisk(sigma, skew, excessKurtosis, n);
    var discriminant = Z * Z - 2 * valueAtRisk;
    if (discriminant < 0) {
      throw new IllegalStateException(
          "Cornish-Fisher quantile out of domain: date=%s, valueAtRisk=%s, discriminant=%s"
              .formatted(evalDate, valueAtRisk, discriminant));
    }
    var vev = (Math.sqrt(discriminant) - Z) / Math.sqrt(RECOMMENDED_HOLDING_PERIOD_YEARS);

    var volatility = BigDecimal.valueOf(vev).setScale(VOLATILITY_SCALE, RoundingMode.HALF_UP);
    return new ReferencePoint(
        evalDate,
        RiskClassBucket.mrmClass(vev),
        n,
        volatility,
        Map.of(
            "dailySigma", sigma,
            "skew", skew,
            "excessKurtosis", excessKurtosis,
            "valueAtRisk", valueAtRisk));
  }

  private double valueAtRisk(double sigma, double skew, double excessKurtosis, int n) {
    var rootN = Math.sqrt(n);
    return -sigma
            * rootN
            * (Z
                - SKEW_COEFFICIENT * (skew / rootN)
                + KURTOSIS_COEFFICIENT * (excessKurtosis / n)
                - SKEW_SQUARED_COEFFICIENT * (skew * skew / n))
        - 0.5 * sigma * sigma * n;
  }

  private record DatedReturn(LocalDate date, double value) {}
}
