package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class IntegrityCheckResult {
  @Singular List<Discrepancy> discrepancies;

  @Singular("missingData")
  List<MissingData> missingData;

  @Singular("orphanedData")
  List<OrphanedData> orphanedData;

  public static IntegrityCheckResult empty() {
    return IntegrityCheckResult.builder().build();
  }

  public boolean hasIssues() {
    return !discrepancies.isEmpty() || !missingData.isEmpty() || !orphanedData.isEmpty();
  }

  public record Discrepancy(
      String fundTicker,
      LocalDate date,
      BigDecimal dbValue,
      BigDecimal yahooValue,
      BigDecimal difference,
      BigDecimal percentageDifference) {}

  public record MissingData(String fundTicker, LocalDate date, BigDecimal yahooValue) {}

  public record OrphanedData(String fundTicker, LocalDate date) {}
}
