package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeKey.SAVINGS_FUND;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_FEES;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_START;

import java.util.Set;

public enum NudgeContext {
  SECOND_PILLAR_MANDATE(Set.of(SECOND_PILLAR_TRANSFER)),
  SECOND_PILLAR_PAYMENT_RATE(Set.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)),
  THIRD_PILLAR_MANDATE(Set.of(THIRD_PILLAR_START, THIRD_PILLAR_FEES)),
  THIRD_PILLAR_PAYMENT(Set.of(THIRD_PILLAR_START, THIRD_PILLAR_FEES)),
  THIRD_PILLAR_PAYMENT_ARRIVED(Set.of(THIRD_PILLAR_START, THIRD_PILLAR_FEES)),
  THIRD_PILLAR_RECURRING_CONFIRMATION(
      Set.of(THIRD_PILLAR_START, THIRD_PILLAR_FEES, THIRD_PILLAR_RECURRING)),
  SAVINGS_FUND_PAYMENT(Set.of(SAVINGS_FUND)),
  MEMBERSHIP(Set.of(NudgeKey.MEMBERSHIP));

  private final Set<NudgeKey> suppressed;

  NudgeContext(Set<NudgeKey> suppressed) {
    this.suppressed = suppressed;
  }

  public boolean suppresses(NudgeKey key) {
    return suppressed.contains(key);
  }
}
