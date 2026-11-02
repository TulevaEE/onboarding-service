package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.fees.ocf.OcfRunOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.rate.ApplicableRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

record ComputedFundLine(Computed computed) {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int PERCENT_DECIMAL_PLACES = 2;
  private static final int MONTH_FIGURE_PERCENT_DECIMAL_PLACES = 4;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  static String percent(BigDecimal rate, int decimalPlaces) {
    return rate.multiply(HUNDRED).setScale(decimalPlaces, HALF_UP).toPlainString();
  }

  boolean needsReview() {
    return notes().findAny().isPresent();
  }

  String detail() {
    var figure =
        computed.incomplete()
            ? "%s%%, incomplete — %s"
                .formatted(
                    totalOcfPercent(),
                    computed.gaps().stream().map(Enum::name).collect(joining(", ")))
            : figureBesideTheKid();
    return Stream.concat(Stream.of(figure), notes()).collect(joining(" — "));
  }

  private String figureBesideTheKid() {
    var figure = "%s%%".formatted(totalOcfPercent());
    return Optional.ofNullable(computed.kid().kidInForce())
        .map(kid -> "%s (%s)".formatted(figure, theKid(kid)))
        .orElse("%s (no KID recorded)".formatted(figure));
  }

  private String theKid(KidFigure kid) {
    var publishedFigure =
        "KID %s%% from %s"
            .formatted(
                kid.precision().percent(kid.publishedOcf()).toPlainString(), kid.validFrom());
    return Stream.concat(Stream.of(publishedFigure), keptOnReview().stream())
        .collect(joining(", "));
  }

  private Optional<String> keptOnReview() {
    return Optional.ofNullable(computed.kid().keptOnReview())
        .filter(review -> computed.differsFromTheKid())
        .map(
            review ->
                "kept on review %s"
                    .formatted(LocalDate.ofInstant(review.reviewedAt(), ESTONIAN_ZONE)));
  }

  private Stream<String> notes() {
    return Stream.of(
            monthAverageWeighting().stream(),
            standardRatesAfterAgreements(),
            kidDifference().stream())
        .flatMap(note -> note);
  }

  private Optional<String> kidDifference() {
    return computed.awaitsAKidReview()
        ? Optional.of("differs from the KID at the published precision, review the KID")
        : Optional.empty();
  }

  private Stream<String> standardRatesAfterAgreements() {
    return computed.holdings().stream()
        .map(OcfHolding::rate)
        .filter(ApplicableRate::isTheStandardRateAfterAnAgreement)
        .map(rate -> "%s: %s".formatted(rate.isin(), rate.reason()))
        .distinct();
  }

  private Optional<String> monthAverageWeighting() {
    var audit = computed.snapshot().audit();
    var monthEnd = audit.investedRatioMonthEnd();
    var average = audit.investedRatioMonthAverage();
    if (audit.underlyingWeightBasis() != MONTH_AVERAGE || monthEnd == null || average == null) {
      return Optional.empty();
    }
    return Optional.of(
        ("month-end invested %s%% is not representative of the month average %s%% (%s pp), so"
                + " the underlying funds are weighed by their month-average share")
            .formatted(
                percent(monthEnd, PERCENT_DECIMAL_PLACES),
                percent(average, PERCENT_DECIMAL_PLACES),
                percent(monthEnd.subtract(average), PERCENT_DECIMAL_PLACES)));
  }

  private String totalOcfPercent() {
    return percent(computed.snapshot().totalOcf(), MONTH_FIGURE_PERCENT_DECIMAL_PLACES);
  }
}
