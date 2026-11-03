package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;

record PublishedPrecision(int decimals) {

  private static final int PENSION_FUND_KID_PERCENT_DECIMALS = 2;
  private static final int PRIIPS_ANNEX_VI_POINT_78_PERCENT_DECIMALS = 1;
  private static final int FRACTION_TO_PERCENT = 2;

  static PublishedPrecision of(TulevaFund fund) {
    return new PublishedPrecision(
        fund.isSavingsFund()
            ? PRIIPS_ANNEX_VI_POINT_78_PERCENT_DECIMALS
            : PENSION_FUND_KID_PERCENT_DECIMALS);
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
