package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeDecision.of;
import static ee.tuleva.onboarding.nudge.NudgeKey.MEMBERSHIP;
import static ee.tuleva.onboarding.nudge.NudgeKey.NONE;

import java.util.Optional;

final class NudgeRules {

  private NudgeRules() {}

  static NudgeDecision decide(NudgeInputs in, NudgeContext context) {
    return PensionNudges.decide(in, context)
        .or(() -> SavingsNudges.decide(in, context))
        .or(() -> membership(in, context))
        .orElse(of(NONE));
  }

  private static Optional<NudgeDecision> membership(NudgeInputs in, NudgeContext context) {
    if (!in.member() && !context.suppresses(MEMBERSHIP)) {
      return Optional.of(of(MEMBERSHIP));
    }
    return Optional.empty();
  }
}
