package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

record PublishedPrecision(int decimals) {

  private static final int KID_PERCENT_DECIMALS = 2;
  private static final int FRACTION_TO_PERCENT = 2;

  static PublishedPrecision forANewKid() {
    return new PublishedPrecision(KID_PERCENT_DECIMALS);
  }

  BigDecimal percent(BigDecimal fraction) {
    return fraction.movePointRight(FRACTION_TO_PERCENT).setScale(decimals, HALF_UP);
  }

  BigDecimal printedFraction(BigDecimal fraction) {
    return percent(fraction).movePointLeft(FRACTION_TO_PERCENT);
  }

  boolean differ(BigDecimal oneFraction, BigDecimal otherFraction) {
    return percent(oneFraction).compareTo(percent(otherFraction)) != 0;
  }
}
