package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class RiskClassRangeFormatter {

  private static final Locale ESTONIAN = Locale.of("et", "EE");

  String rangeLine(PublishedRiskIndicator indicator) {
    var publishedClass = Objects.requireNonNull(indicator.publishedClass());
    var range = RiskClassBucket.range(indicator.indicatorType(), publishedClass);
    var volatility = indicator.latestVolatility();
    if (volatility == null) {
      return "Klassi %d vahemik %s.".formatted(publishedClass, rangeText(indicator, range));
    }
    var distance =
        RiskClassBucket.distanceToNearestBound(
            indicator.indicatorType(), publishedClass, volatility);
    return "%s %s (klassi %d vahemik %s); lähim piir on %s kaugusel."
        .formatted(
            volatilityLabel(indicator),
            volatility(indicator),
            publishedClass,
            rangeText(indicator, range),
            distance == null ? "—" : number(distance, indicator));
  }

  private String rangeText(PublishedRiskIndicator indicator, RiskClassBucket.ClassRange range) {
    var lower = range.lowerInclusive();
    var upper = range.upperExclusive();
    return "%s–%s"
        .formatted(
            lower == null ? "0" : number(lower, indicator),
            upper == null ? "∞" : number(upper, indicator));
  }

  String volatilityLabel(PublishedRiskIndicator indicator) {
    return indicator.indicatorType() == SRI ? "VEV" : "Aastane volatiilsus";
  }

  String volatility(PublishedRiskIndicator indicator) {
    var volatility = indicator.latestVolatility();
    return volatility == null ? "—" : number(volatility, indicator);
  }

  private String number(BigDecimal value, PublishedRiskIndicator indicator) {
    return indicator.indicatorType() == SRI
        ? vevInDecimals(value)
        : annualisedVolatilityInPerCent(value);
  }

  private String vevInDecimals(BigDecimal value) {
    return String.format(ESTONIAN, "%.4f", value);
  }

  private String annualisedVolatilityInPerCent(BigDecimal value) {
    return String.format(
            ESTONIAN,
            "%.2f",
            value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
        + "%";
  }
}
