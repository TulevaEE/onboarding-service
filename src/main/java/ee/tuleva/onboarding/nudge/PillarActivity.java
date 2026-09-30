package ee.tuleva.onboarding.nudge;

public record PillarActivity(
    boolean secondPillarActive, boolean thirdPillarActive, boolean secondPillarJoined) {

  boolean hasInactiveSecondPillar() {
    return secondPillarJoined && !secondPillarActive;
  }
}
