package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeDecision.of;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_PAYMENT_RATE;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_FEES;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RAISE;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_START;

import java.math.BigDecimal;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class PensionNudges {

  private static final BigDecimal HIGH_FEE = new BigDecimal("0.003");

  private PensionNudges() {}

  static Optional<NudgeDecision> decide(NudgeInputs in, NudgeContext context) {
    return secondPillarTransfer(in, context)
        .or(() -> paymentRate(in, context))
        .or(() -> thirdPillar(in, context))
        .or(() -> thirdPillarRecurring(in, context))
        .or(() -> thirdPillarRaise(in));
  }

  static boolean secondPillarDecidable(NudgeInputs in) {
    return in.leftSecondPillar().isKnown();
  }

  static boolean thirdPillarDecidable(NudgeInputs in) {
    if (!in.thirdPillarActive()) {
      return true;
    }
    if (!in.thirdPillarRecurring().isKnown()) {
      return false;
    }
    return in.thirdPillarRecurring().isNo() || in.taxHeadroom().isKnown();
  }

  private static Optional<NudgeDecision> secondPillarTransfer(
      NudgeInputs in, NudgeContext context) {
    if (context.suppresses(SECOND_PILLAR_TRANSFER) || !secondPillarEligible(in)) {
      return Optional.empty();
    }
    if (in.pendingSecondPillarTransfer() || !secondPillarNeedsMoving(in)) {
      return Optional.empty();
    }
    boolean highFee = isHighFee(in.secondPillarFee());
    return Optional.of(NudgeDecision.secondPillarTransfer(highFee ? in.feeComparison() : null));
  }

  private static boolean secondPillarNeedsMoving(NudgeInputs in) {
    if (!in.secondPillarActive() || !in.secondPillarPartiallyConverted()) {
      return true;
    }
    return !in.secondPillarFullyConverted() && isHighFee(in.secondPillarFee());
  }

  private static Optional<NudgeDecision> paymentRate(NudgeInputs in, NudgeContext context) {
    if (context.suppresses(SECOND_PILLAR_PAYMENT_RATE) || !secondPillarEligible(in)) {
      return Optional.empty();
    }
    if (in.secondPillarActive() && in.canIncreasePaymentRate()) {
      return Optional.of(of(SECOND_PILLAR_PAYMENT_RATE));
    }
    return Optional.empty();
  }

  private static boolean secondPillarEligible(NudgeInputs in) {
    return in.adult()
        && !in.reachedRetirementAge()
        && in.leftSecondPillar().isNo()
        && !in.pendingSecondPillarWithdrawal();
  }

  private static Optional<NudgeDecision> thirdPillar(NudgeInputs in, NudgeContext context) {
    if (context.suppresses(THIRD_PILLAR_START)) {
      return Optional.empty();
    }
    if (!in.thirdPillarActive()) {
      return Optional.of(of(THIRD_PILLAR_START));
    }
    return thirdPillarFeesMatter(in) ? Optional.of(of(THIRD_PILLAR_FEES)) : Optional.empty();
  }

  private static boolean thirdPillarFeesMatter(NudgeInputs in) {
    return !in.thirdPillarPartiallyConverted()
        || (!in.thirdPillarFullyConverted() && isHighFee(in.thirdPillarFee()));
  }

  private static Optional<NudgeDecision> thirdPillarRecurring(
      NudgeInputs in, NudgeContext context) {
    if (context.suppresses(THIRD_PILLAR_RECURRING) || !thirdPillarSaver(in)) {
      return Optional.empty();
    }
    return in.thirdPillarRecurring().isNo()
        ? Optional.of(of(THIRD_PILLAR_RECURRING))
        : Optional.empty();
  }

  private static Optional<NudgeDecision> thirdPillarRaise(NudgeInputs in) {
    if (thirdPillarSaver(in) && in.thirdPillarRecurring().isYes() && in.taxHeadroom().isYes()) {
      return Optional.of(of(THIRD_PILLAR_RAISE));
    }
    return Optional.empty();
  }

  private static boolean thirdPillarSaver(NudgeInputs in) {
    return thirdPillarDecidable(in) && in.adult() && in.thirdPillarActive();
  }

  private static boolean isHighFee(@Nullable BigDecimal fee) {
    return fee != null && fee.compareTo(HIGH_FEE) > 0;
  }
}
