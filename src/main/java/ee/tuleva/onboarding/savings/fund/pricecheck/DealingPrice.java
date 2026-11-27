package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.INCOMPLETE_BOOKING;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_DIFFERS_FROM_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_NOT_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICED_AT_THE_NAV;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICE_DEVIATES;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.ROUNDING_ONLY;
import static java.math.RoundingMode.HALF_DOWN;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.UnitDealing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

record DealingPrice(UnitDealing dealing, @Nullable BigDecimal publishedNav) {

  private static final int ISSUED_UNITS_DECIMALS = 5;
  private static final RoundingMode ISSUED_UNITS_ROUNDING = HALF_DOWN;
  private static final int REDEEMED_CASH_DECIMALS = 2;
  private static final RoundingMode REDEEMED_CASH_ROUNDING = HALF_UP;
  private static final int PRICE_SCALE = 16;

  DealingPriceVerdict verdict(BigDecimal deviationThreshold) {
    if (isIncomplete()) {
      return INCOMPLETE_BOOKING;
    }
    if (publishedNav == null) {
      return NAV_NOT_PUBLISHED;
    }
    if (nav().compareTo(publishedNav) != 0) {
      return NAV_DIFFERS_FROM_PUBLISHED;
    }
    if (deviation().abs().compareTo(deviationThreshold) < 0) {
      return PRICED_AT_THE_NAV;
    }
    return isTheIssueOrRedemptionRuleAppliedToTheNav() ? ROUNDING_ONLY : PRICE_DEVIATES;
  }

  BigDecimal realisedPrice() {
    return requireNonNull(dealing.cashAmount()).divide(units(), PRICE_SCALE, HALF_UP);
  }

  BigDecimal deviation() {
    return realisedPrice().subtract(nav()).divide(nav(), PRICE_SCALE, HALF_UP);
  }

  BigDecimal nav() {
    return requireNonNull(dealing.navPerUnit());
  }

  private BigDecimal units() {
    return requireNonNull(dealing.units());
  }

  private boolean isIncomplete() {
    return dealing.units() == null
        || dealing.units().signum() <= 0
        || dealing.cashAmount() == null
        || dealing.navPerUnit() == null
        || dealing.navPerUnit().signum() <= 0
        || dealing.navDate() == null;
  }

  private boolean isTheIssueOrRedemptionRuleAppliedToTheNav() {
    BigDecimal cash = requireNonNull(dealing.cashAmount());
    return switch (dealing.type()) {
      case REDEMPTION_REQUEST ->
          units()
                  .multiply(nav())
                  .setScale(REDEEMED_CASH_DECIMALS, REDEEMED_CASH_ROUNDING)
                  .compareTo(cash)
              == 0;
      default ->
          cash.divide(nav(), ISSUED_UNITS_DECIMALS, ISSUED_UNITS_ROUNDING).compareTo(units()) == 0;
    };
  }
}
