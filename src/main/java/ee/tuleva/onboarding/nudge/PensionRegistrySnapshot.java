package ee.tuleva.onboarding.nudge;

public record PensionRegistrySnapshot(
    boolean secondPillarActive,
    boolean secondPillarAtTuleva,
    boolean leftSecondPillar,
    boolean canIncreasePaymentRate,
    boolean thirdPillarActive) {

  public static final PensionRegistrySnapshot UNKNOWN_PERSON =
      new PensionRegistrySnapshot(false, false, false, false, false);
}
