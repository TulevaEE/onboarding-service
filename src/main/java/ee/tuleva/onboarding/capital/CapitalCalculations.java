package ee.tuleva.onboarding.capital;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CapitalCalculations {

  private static final int PRECISION = 5;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  public static BigDecimal calculateCapitalValue(
      BigDecimal ownershipUnits, BigDecimal ownershipUnitPrice) {

    if (ownershipUnits == null || ownershipUnitPrice == null) {
      throw new IllegalArgumentException(
          "Ownership units and price must not be null. "
              + "ownershipUnits="
              + ownershipUnits
              + ", ownershipUnitPrice="
              + ownershipUnitPrice);
    }

    return ownershipUnits.multiply(ownershipUnitPrice).setScale(PRECISION, ROUNDING);
  }
}
