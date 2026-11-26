package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.ROUNDING_ONLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.ledger.UnitDealing;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

record PriceCheckRun(BookingDays days, BigDecimal deviationThreshold, List<DealingPrice> dealings) {

  private static final int REALISED_PRICE_DECIMALS = 6;
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final String NEXT_STEP =
      "Next: find the cause; a holder who got a wrong price is corrected and compensated as for a"
          + " NAV error";

  List<DealingPrice> reported() {
    return dealings.stream().filter(price -> verdictOf(price).isReported()).toList();
  }

  List<DealingPrice> roundingOnly() {
    return dealings.stream().filter(price -> verdictOf(price) == ROUNDING_ONLY).toList();
  }

  Optional<String> alert() {
    var reported = reported();
    if (reported.isEmpty()) {
      return Optional.empty();
    }
    String header =
        "⚠️ TKF100 issue/redemption prices: %d of %d deals booked %s are not at their NAV"
            .formatted(reported.size(), dealings.size(), days.describe());
    String lines = reported.stream().map(this::line).collect(joining("\n"));
    return Optional.of(header + "\n" + lines + "\n" + NEXT_STEP);
  }

  private DealingPriceVerdict verdictOf(DealingPrice price) {
    return price.verdict(deviationThreshold);
  }

  private String line(DealingPrice price) {
    UnitDealing dealing = price.dealing();
    String deal =
        "• %s %s"
            .formatted(
                dealing.type() == REDEMPTION_REQUEST ? "redemption" : "subscription",
                dealing.transactionId());
    return switch (verdictOf(price)) {
      case PRICE_DEVIATES ->
          deal
              + ": price %s vs NAV %s of %s (%s)"
                  .formatted(
                      price
                          .realisedPrice()
                          .setScale(REALISED_PRICE_DECIMALS, HALF_UP)
                          .toPlainString(),
                      nav(price.nav()),
                      dealing.navDate(),
                      percent(price.deviation()));
      case NAV_DIFFERS_FROM_PUBLISHED ->
          deal
              + ": booked at NAV %s, the published NAV of %s is %s"
                  .formatted(
                      nav(price.nav()),
                      dealing.navDate(),
                      nav(requireNonNull(price.publishedNav())));
      case NAV_NOT_PUBLISHED ->
          deal + ": no published NAV of %s to compare with".formatted(dealing.navDate());
      case INCOMPLETE_BOOKING -> deal + ": booking lacks its units, cash, NAV or NAV date";
      case PRICED_AT_THE_NAV, ROUNDING_ONLY ->
          throw new IllegalStateException(
              "Deal at its NAV has no alert line: transactionId=" + dealing.transactionId());
    };
  }

  private static String nav(BigDecimal nav) {
    return nav.setScale(TKF100.getNavScale(), HALF_UP).toPlainString();
  }

  private static String percent(BigDecimal deviation) {
    return String.format(Locale.ROOT, "%+.2f%%", deviation.multiply(ONE_HUNDRED));
  }
}
