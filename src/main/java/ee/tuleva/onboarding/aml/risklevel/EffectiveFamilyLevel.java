package ee.tuleva.onboarding.aml.risklevel;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

record EffectiveFamilyLevel(
    @Nullable String family, EffectiveRiskLevel level, @Nullable Instant assessedAt) {

  boolean isHigh() {
    return level == EffectiveRiskLevel.HIGH;
  }
}
