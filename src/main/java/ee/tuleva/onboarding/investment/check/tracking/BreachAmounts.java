package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class BreachAmounts {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private BreachAmounts() {}

  static String formatEur(BigDecimal value) {
    return String.format("%18s", formatAmount(value));
  }

  static String formatAmount(BigDecimal value) {
    return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.UK)).format(value);
  }

  static String formatUnits(BigDecimal value) {
    return new DecimalFormat("+#,##0.###;-#,##0.###", DecimalFormatSymbols.getInstance(Locale.UK))
        .format(value);
  }

  static String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
