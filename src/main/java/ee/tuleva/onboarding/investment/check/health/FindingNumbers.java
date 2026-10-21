package ee.tuleva.onboarding.investment.check.health;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

final class FindingNumbers {

  private FindingNumbers() {}

  static String eur(BigDecimal amount) {
    return amount.setScale(2, HALF_UP).toPlainString();
  }

  static String signedEur(BigDecimal amount) {
    return signed(amount, eur(amount));
  }

  static String units(BigDecimal units) {
    return units.setScale(3, HALF_UP).toPlainString();
  }

  static String signedUnits(BigDecimal units) {
    return signed(units, units(units));
  }

  static String signedQuantity(BigDecimal quantity) {
    return signed(quantity, quantity.stripTrailingZeros().toPlainString());
  }

  private static String signed(BigDecimal value, String formatted) {
    return value.signum() < 0 ? formatted : "+" + formatted;
  }
}
