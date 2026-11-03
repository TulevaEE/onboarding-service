package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import org.jspecify.annotations.Nullable;

public record PensionRegistrySnapshot(
    boolean secondPillarActive,
    boolean secondPillarAtTuleva,
    boolean leftSecondPillar,
    boolean canIncreasePaymentRate,
    boolean thirdPillarActive,
    @Nullable TulevaFund secondPillarContributionTulevaFund) {

  public static final PensionRegistrySnapshot UNKNOWN_PERSON =
      new PensionRegistrySnapshot(false, false, false, false, false, null);
}
