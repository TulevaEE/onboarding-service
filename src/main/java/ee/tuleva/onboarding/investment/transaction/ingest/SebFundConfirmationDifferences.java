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
      String name,
      BigDecimal confirmed,
      List<BigDecimal> figuresItsWriterStores,
      @Nullable BigDecimal stored) {
    if (stored == null || !isOneOf(stored, figuresItsWriterStores)) {
      differences.add(
          name + " " + plain(confirmed) + " vs " + (stored == null ? null : plain(stored)));
    }
    return this;
  }

  List<String> list() {
    return List.copyOf(differences);
  }

  private static boolean isOneOf(BigDecimal stored, List<BigDecimal> figuresItsWriterStores) {
    return figuresItsWriterStores.stream().anyMatch(figure -> isStoredAs(figure, stored));
  }

  private static boolean isStoredAs(BigDecimal value, BigDecimal stored) {
    return value.abs().setScale(stored.scale(), HALF_UP).compareTo(stored.abs()) == 0;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static boolean equalToSebsPrecision(BigDecimal confirmed, BigDecimal other) {
    return asSebReportsIt(confirmed.abs()).compareTo(asSebReportsIt(other.abs())) == 0;
  }
}
