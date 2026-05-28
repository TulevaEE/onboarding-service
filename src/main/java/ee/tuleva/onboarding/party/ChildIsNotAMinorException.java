package ee.tuleva.onboarding.party;

public class ChildIsNotAMinorException extends RuntimeException {

  public ChildIsNotAMinorException(String personalCode) {
    super("Child is not a representable minor: personalCode=" + personalCode);
  }
}
