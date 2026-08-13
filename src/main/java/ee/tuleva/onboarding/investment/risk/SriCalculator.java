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

  /**
   * Annex II leaves the length of a trading year to the manufacturer, so the number comes from the
   * ESAs' own worked example instead (JC 2017 49, the flow diagram published as PRIIPs Q&A
   * material): 365 days less 104 weekend days less 5 public holidays, giving N = 5 * 256 = 1280 for
   * a five-year holding period. 52 * 5 would be a house convention with nothing behind it, and the
   * choice moves the VEV by about 0,8%, which is enough to cross a class boundary.
   */
  private static final int TRADING_DAYS_PER_YEAR = 256;

  /**
   * Annex II p10: below five years of daily prices a shorter period may be used, but never less
   * than two years of observed returns. Under that, the reference point still carries its
   * volatility — the digest needs to show something — but no class, because the class would not be
   * one we may stand behind.
   *
   * <p>Two years is a period, so it is measured as one. Multiplying it by the trading year would
   * demand 512 prices, which is what a two-year stretch holds only if no exchange ever closes:
   * every public holiday puts a genuine two-year history under the threshold and withholds a class
   * the Annex allows. The tolerance absorbs the same calendar drift at the other end.
   */
  private static final int MINIMUM_OBSERVATION_YEARS = 2;

  private static final int MINIMUM_HISTORY_TOLERANCE_DAYS = 7;

  /**
   * Not the Annex test — a floor beneath which the four moments say nothing however wide a period
   * they are spread across, set well below the roughly 500 trading days a real two-year history
   * carries.
   */
  private static final int MINIMUM_OBSERVATIONS = 400;

  private static final int RECOMMENDED_HOLDING_PERIOD_YEARS = 5;
  private static final int OBSERVATION_WINDOW_YEARS = 5;
  private static final int MINIMUM_RETURNS = 2;
  private static final int VOLATILITY_SCALE = 12;

  static final int HOLDING_PERIOD_TRADING_DAYS =
      RECOMMENDED_HOLDING_PERIOD_YEARS * TRADING_DAYS_PER_YEAR;

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
            .toList();
    if (window.size() < MINIMUM_RETURNS) {
      return null;
    }
    return referencePoint(
        evalDate,
        window.stream().mapToDouble(DatedReturn::value).toArray(),
        window.getFirst().date());
  }

  private boolean isPublishable(int observations, LocalDate earliestReturn, LocalDate evalDate) {
    var minimumHistoryStart =
        evalDate.minusYears(MINIMUM_OBSERVATION_YEARS).plusDays(MINIMUM_HISTORY_TOLERANCE_DAYS);
    return observations >= MINIMUM_OBSERVATIONS && !earliestReturn.isAfter(minimumHistoryStart);
  }

  private @Nullable ReferencePoint referencePoint(
      LocalDate evalDate, double[] window, LocalDate earliestReturn) {
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

    var valueAtRisk = valueAtRisk(sigma, skew, excessKurtosis);
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
      return null;
    }

    var volatility = BigDecimal.valueOf(vev).setScale(VOLATILITY_SCALE, RoundingMode.HALF_UP);
    return new ReferencePoint(
        evalDate,
        isPublishable(n, earliestReturn, evalDate) ? RiskClassBucket.mrmClass(vev) : null,
        n,
        volatility,
        Map.of(
            "dailySigma", sigma,
            "skew", skew,
            "excessKurtosis", excessKurtosis,
            "valueAtRisk", valueAtRisk));
  }

  /**
   * N is the number of trading periods in the recommended holding period, not the number of
   * observations the moments were estimated from. Feeding the sample size in scales the whole
   * quantile to whatever history happens to be loaded: a short series collapses the VEV several
   * fold and reports a risk class low enough to demand a KID reissue.
   */
  private double valueAtRisk(double sigma, double skew, double excessKurtosis) {
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

  private record DatedReturn(LocalDate date, double value) {}
}
