package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_REJOIN;

import java.util.Optional;

final class SecondPillarRejoinNudge {

  private SecondPillarRejoinNudge() {}

  static Optional<NudgeDecision> decide(NudgeInputs in, NudgeContext context) {
    SecondPillarRejoinWindow window = in.secondPillarRejoinWindow();
    if (context.suppresses(SECOND_PILLAR_REJOIN) || !window.open() || !mayRejoinNow(in)) {
      return Optional.empty();
    }
    return Optional.of(NudgeDecision.secondPillarRejoin(window.deadline()));
  }

  private static boolean mayRejoinNow(NudgeInputs in) {
    return in.adult()
        && !in.reachedRetirementAge()
        && in.mayRejoinSecondPillar().isYes()
        && !in.pendingSecondPillarRejoin();
  }
}
