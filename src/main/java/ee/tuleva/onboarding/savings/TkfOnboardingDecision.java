package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;

import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import org.jspecify.annotations.Nullable;

public final class TkfOnboardingDecision {

  public record Assessment(@Nullable SavingsFundOnboardingStatus current, RiskLevel risk) {}

  private TkfOnboardingDecision() {}

  public static SavingsFundOnboardingStatus statusFor(Assessment assessment) {
    if (assessment.current() == COMPLETED) {
      return COMPLETED;
    }
    if (assessment.current() == REJECTED) {
      return REJECTED;
    }
    if (assessment.current() == ON_HOLD) {
      return ON_HOLD;
    }
    return switch (assessment.risk()) {
      case NONE, LOW, MEDIUM -> COMPLETED;
      case HIGH -> ON_HOLD;
    };
  }
}
