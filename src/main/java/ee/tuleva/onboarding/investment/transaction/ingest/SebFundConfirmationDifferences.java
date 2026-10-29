package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebReportedPrecision.asSebReportsIt;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class SebFundConfirmationDifferences {

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
      String name,
      BigDecimal confirmed,
      BigDecimal normalizedByTheImport,
      @Nullable BigDecimal stored) {
    if (stored == null
        || !(isStoredAs(confirmed, stored)
            || isStoredAs(inSebsReport(confirmed), stored)
            || isStoredAs(normalizedByTheImport, stored))) {
      differences.add(
          name + " " + plain(confirmed) + " vs " + (stored == null ? null : plain(stored)));
    }
    return this;
  }

  List<String> list() {
    return List.copyOf(differences);
  }

  static BigDecimal inSebsReport(BigDecimal confirmed) {
    return asSebReportsIt(confirmed.abs());
  }

  private static boolean isStoredAs(BigDecimal value, BigDecimal stored) {
    return value.abs().setScale(stored.scale(), HALF_UP).compareTo(stored.abs()) == 0;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static boolean equalToSebsPrecision(BigDecimal confirmed, BigDecimal other) {
    return inSebsReport(confirmed).compareTo(inSebsReport(other)) == 0;
  }
}
