package ee.tuleva.onboarding.aml;

public record OnboardingReview(String code, PartyType partyType, Outcome outcome) {

  public enum PartyType {
    PERSON,
    LEGAL_ENTITY
  }

  public enum Outcome {
    COMPLETE,
    REJECT
  }
}
