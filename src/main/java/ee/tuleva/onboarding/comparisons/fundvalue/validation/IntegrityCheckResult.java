package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class IntegrityCheckResult {

  private IntegrityCheckResult() {}

  public enum Severity {
    CRITICAL,
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
      String comparisonDescription,
      List<SourceValue> allSourceValues) {}

  public record SourceValue(String source, BigDecimal value) {}

  public record StaleSource(
      String fundName,
      String source,
      String storageKey,
      LocalDate lastDate,
      long workingDaysBehind) {}
}
