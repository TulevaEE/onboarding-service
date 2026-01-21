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

  public enum Severity {
    CRITICAL,
    WARNING,
    INFO
  }

  public record Discrepancy(
      String fundTicker,
      LocalDate date,
      BigDecimal anchorValue,
      BigDecimal comparedValue,
      BigDecimal difference,
      BigDecimal percentageDifference,
      Severity severity,
      String comparisonDescription) {

    public Discrepancy(
        String fundTicker,
        LocalDate date,
        BigDecimal anchorValue,
        BigDecimal comparedValue,
        BigDecimal difference,
        BigDecimal percentageDifference) {
      this(
          fundTicker,
          date,
          anchorValue,
          comparedValue,
          difference,
          percentageDifference,
          Severity.WARNING,
          "");
    }
  }

  public record MissingData(
      String fundTicker, LocalDate date, BigDecimal referenceValue, Severity severity) {

    public MissingData(String fundTicker, LocalDate date, BigDecimal referenceValue) {
      this(fundTicker, date, referenceValue, Severity.WARNING);
    }
  }

  public record OrphanedData(String fundTicker, LocalDate date, Severity severity) {

    public OrphanedData(String fundTicker, LocalDate date) {
      this(fundTicker, date, Severity.WARNING);
    }
  }
}
