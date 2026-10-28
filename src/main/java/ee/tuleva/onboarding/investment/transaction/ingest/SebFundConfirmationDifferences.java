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

  List<String> list() {
    return List.copyOf(differences);
  }

  private static boolean equalToSebsPrecision(BigDecimal confirmed, BigDecimal other) {
    return confirmed
            .abs()
            .round(SEB_REPORT_SIGNIFICANT_DIGITS)
            .compareTo(other.abs().round(SEB_REPORT_SIGNIFICANT_DIGITS))
        == 0;
  }
}
