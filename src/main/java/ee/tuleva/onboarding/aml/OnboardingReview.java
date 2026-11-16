package ee.tuleva.onboarding.aml;

public record OnboardingReview(String personalCode, Outcome outcome) {

  public enum Outcome {
    COMPLETE,
    REJECT
  }
}
