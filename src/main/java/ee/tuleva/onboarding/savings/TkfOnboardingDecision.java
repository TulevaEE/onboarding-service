package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;

import ee.tuleva.onboarding.aml.OnboardingReview.Outcome;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import org.jspecify.annotations.Nullable;

public final class TkfOnboardingDecision {

  public record Assessment(@Nullable SavingsFundOnboardingStatus current, RiskLevel risk) {}

  public record Review(@Nullable SavingsFundOnboardingStatus current, Outcome outcome) {}

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

  public static @Nullable SavingsFundOnboardingStatus statusAfter(Review review) {
    return switch (review.current()) {
      case ON_HOLD ->
          switch (review.outcome()) {
            case COMPLETE -> COMPLETED;
            case REJECT -> REJECTED;
          };
      case REJECTED ->
          switch (review.outcome()) {
            case COMPLETE -> COMPLETED;
            case REJECT -> REJECTED;
          };
      case null, default -> review.current();
    };
  }
}
