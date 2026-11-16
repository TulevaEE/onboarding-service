package ee.tuleva.onboarding.aml;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PersonRiskLevel(Level level, @Nullable Instant tkfAssessedAt) {

  public enum Level {
    HIGH,
    NOT_HIGH
  }

  public boolean isHigh() {
    return level == Level.HIGH;
  }

  public boolean tkfLevelIncludes(Instant input) {
    return tkfAssessedAt != null && tkfAssessedAt.isAfter(input);
  }
}
