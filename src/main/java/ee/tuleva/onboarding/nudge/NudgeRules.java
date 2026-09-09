package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeContext.SAVINGS_FUND_PAYMENT;
import static ee.tuleva.onboarding.nudge.NudgeDecision.of;
import static ee.tuleva.onboarding.nudge.NudgeKey.ACCOUNT_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.MEMBERSHIP;
import static ee.tuleva.onboarding.nudge.NudgeKey.NONE;

import java.util.Optional;

final class NudgeRules {

  private NudgeRules() {}

  static NudgeDecision decide(NudgeInputs in, NudgeContext context) {
    return accountRecurring(in, context)
        .or(() -> nothingPersonalForBusinessRoles(in))
        .or(() -> PensionNudges.decide(in, context))
        .or(() -> SavingsNudges.decide(in, context))
        .or(() -> membership(in, context))
        .orElse(of(NONE));
  }

  private static Optional<NudgeDecision> accountRecurring(NudgeInputs in, NudgeContext context) {
    if (context == SAVINGS_FUND_PAYMENT && in.accountRecurring().isNo()) {
      return Optional.of(of(ACCOUNT_RECURRING));
    }
    return Optional.empty();
  }

  private static Optional<NudgeDecision> nothingPersonalForBusinessRoles(NudgeInputs in) {
    return in.actingAsLegalEntity() ? Optional.of(of(NONE)) : Optional.empty();
  }

  private static Optional<NudgeDecision> membership(NudgeInputs in, NudgeContext context) {
    if (!in.member() && !context.suppresses(MEMBERSHIP)) {
      return Optional.of(of(MEMBERSHIP));
    }
    return Optional.empty();
  }
}
