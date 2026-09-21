package ee.tuleva.onboarding.nudge;

@FunctionalInterface
public interface SecondPillarLeaverStatus {

  boolean hasLeft(String personalCode);
}
