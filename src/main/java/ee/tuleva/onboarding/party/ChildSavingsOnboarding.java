package ee.tuleva.onboarding.party;

public interface ChildSavingsOnboarding {

  boolean isCompleted(String personalCode);

  void seedIfAbsent(String personalCode);
}
