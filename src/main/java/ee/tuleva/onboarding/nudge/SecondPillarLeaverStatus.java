package ee.tuleva.onboarding.nudge;

public interface SecondPillarLeaverStatus {

  boolean hasLeft(String personalCode);

  boolean contributionsCannotRestart(String personalCode);
}
