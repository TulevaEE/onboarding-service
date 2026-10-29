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
  private static final BigDecimal TWO = BigDecimal.valueOf(2);

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
    if (stored == null || !isStoredAs(confirmed, stored)) {
      differences.add(
          name + " " + plain(confirmed) + " vs " + (stored == null ? null : plain(stored)));
    }
    return this;
  }

  List<String> list() {
    return List.copyOf(differences);
  }

  private static boolean isStoredAs(BigDecimal confirmed, BigDecimal stored) {
    BigDecimal largestRoundingError =
        sebsRoundingStep(confirmed).add(stored.ulp()).divide(TWO, MathContext.DECIMAL64);
    return confirmed.abs().subtract(stored.abs()).abs().compareTo(largestRoundingError) <= 0;
  }

  private static BigDecimal sebsRoundingStep(BigDecimal confirmed) {
    if (confirmed.signum() == 0) {
      return BigDecimal.ZERO;
    }
    int magnitude = confirmed.precision() - confirmed.scale() - 1;
    return BigDecimal.ONE.scaleByPowerOfTen(
        magnitude - SEB_REPORT_SIGNIFICANT_DIGITS.getPrecision() + 1);
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
