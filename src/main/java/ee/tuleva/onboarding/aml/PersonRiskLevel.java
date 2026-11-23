package ee.tuleva.onboarding.aml;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PersonRiskLevel(Level level, @Nullable Instant tkfAssessedAt) {

  public enum Level {
    HIGH,
    NOT_HIGH,
    NOT_ASSESSED
  }

  public boolean isHigh() {
    return level == Level.HIGH;
  }

  public boolean isAssessed() {
    return level != Level.NOT_ASSESSED;
  }

  public boolean accountsForPepHitAt(Instant pepHitAt) {
    return isHigh() || tkfAssessedAt != null && tkfAssessedAt.isAfter(pepHitAt);
  }
}
