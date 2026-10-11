package ee.tuleva.onboarding.investment.check.health;

import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import java.math.BigDecimal;
import java.util.List;

record ExitLeg(String isin, BigDecimal quantity, BigDecimal previousPrice, ExitMark exitMark) {

  private static final int EUR_SCALE = 2;

  BigDecimal marketEffect() {
    return quantity.multiply(exitMark.executedPrice().subtract(previousPrice));
  }

  private String describeDealingCost() {
    var published = exitMark.published();
    if (published == null) {
      return "pending, no published price to compare against yet";
    }
    var dealingCost = quantity.multiply(exitMark.executedPrice().subtract(published.price()));
    return "%s EUR (against %s published for %s)"
        .formatted(
            dealingCost.setScale(EUR_SCALE, HALF_UP).toPlainString(),
            published.price().toPlainString(),
            published.date());
  }

  private String describe() {
    return ("isin=%s, quantity=%s, previousPrice=%s, exitPrice=%s, marketEffect=%s EUR,"
            + " dealingCost=%s")
        .formatted(
            isin,
            quantity.toPlainString(),
            previousPrice.toPlainString(),
            exitMark.executedPrice().toPlainString(),
            marketEffect().setScale(EUR_SCALE, HALF_UP).toPlainString(),
            describeDealingCost());
  }

  static String describeAll(List<ExitLeg> exitLegs) {
    return exitLegs.stream().map(ExitLeg::describe).collect(joining("; "));
  }
}
