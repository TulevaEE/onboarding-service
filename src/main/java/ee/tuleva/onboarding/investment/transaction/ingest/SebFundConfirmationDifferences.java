package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class SebFundConfirmationDifferences {

  private static final MathContext SEB_REPORT_SIGNIFICANT_DIGITS = new MathContext(10, HALF_UP);

  private final List<String> differences = new ArrayList<>();

  SebFundConfirmationDifferences field(String name, Object confirmed, @Nullable Object other) {
    if (!Objects.equals(confirmed, other)) {
      differences.add(name + " " + confirmed + " vs " + other);
    }
    return this;
  }

  SebFundConfirmationDifferences amount(
      String name, BigDecimal confirmed, @Nullable BigDecimal other) {
    if (other == null || !equalToSebsPrecision(confirmed, other)) {
      differences.add(
          name
              + " "
              + confirmed.toPlainString()
              + " vs "
              + (other == null ? null : other.toPlainString()));
    }
    return this;
  }

  SebFundConfirmationDifferences storedAmount(
      String name, BigDecimal confirmed, @Nullable BigDecimal stored) {
    return storedAmount(name, confirmed, inSebsReport(confirmed), stored);
  }

  SebFundConfirmationDifferences storedAmount(
      String name, BigDecimal confirmed, BigDecimal fromSebsReport, @Nullable BigDecimal stored) {
    if (stored == null || !(isStoredAs(confirmed, stored) || isStoredAs(fromSebsReport, stored))) {
      differences.add(
          name + " " + plain(confirmed) + " vs " + (stored == null ? null : plain(stored)));
    }
    return this;
  }

  List<String> list() {
    return List.copyOf(differences);
  }

  static BigDecimal inSebsReport(BigDecimal confirmed) {
    return confirmed.abs().round(SEB_REPORT_SIGNIFICANT_DIGITS);
  }

  private static boolean isStoredAs(BigDecimal value, BigDecimal stored) {
    return value.abs().setScale(stored.scale(), HALF_UP).compareTo(stored.abs()) == 0;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static boolean equalToSebsPrecision(BigDecimal confirmed, BigDecimal other) {
    return confirmed
            .abs()
            .round(SEB_REPORT_SIGNIFICANT_DIGITS)
            .compareTo(other.abs().round(SEB_REPORT_SIGNIFICANT_DIGITS))
        == 0;
  }
}
