package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import java.math.BigDecimal;

record OcfHolding(BigDecimal value, BigDecimal assetsUnderManagement, InstrumentRate rate) {

  private static final int WEIGHT_SCALE = 12;

  BigDecimal weight() {
    return value.divide(assetsUnderManagement, WEIGHT_SCALE, HALF_UP);
  }
}
