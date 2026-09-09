package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeDecision.of;
import static ee.tuleva.onboarding.nudge.NudgeKey.SAVINGS_FUND;
import static ee.tuleva.onboarding.nudge.NudgeKey.SAVINGS_FUND_RECURRING;

import java.util.Optional;

final class SavingsNudges {

  private SavingsNudges() {}

  static Optional<NudgeDecision> decide(NudgeInputs in, NudgeContext context) {
    return savingsFund(in, context).or(() -> savingsFundRecurring(in));
  }

  private static Optional<NudgeDecision> savingsFund(NudgeInputs in, NudgeContext context) {
    if (context.suppresses(SAVINGS_FUND) || !in.adult() || !savingsFundDecidable(in)) {
      return Optional.empty();
    }
    if (in.savesInSavingsFund().isNo()) {
      return Optional.of(NudgeDecision.savingsFund(in.savingsFundFeePercent()));
    }
    return Optional.empty();
  }

  private static boolean savingsFundDecidable(NudgeInputs in) {
    return PensionNudges.secondPillarDecidable(in)
        && PensionNudges.thirdPillarDecidable(in)
        && in.savesInSavingsFund().isKnown();
  }

  private static Optional<NudgeDecision> savingsFundRecurring(NudgeInputs in) {
    if (in.adult() && in.ownSavingsFundSaver().isYes() && in.ownSavingsFundRecurring().isNo()) {
      return Optional.of(of(SAVINGS_FUND_RECURRING));
    }
    return Optional.empty();
  }
}
