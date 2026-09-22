package ee.tuleva.onboarding.nudge;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NudgeKey {
  SECOND_PILLAR_TRANSFER("nudge_second_pillar", true),
  SECOND_PILLAR_PAYMENT_RATE("nudge_payment_rate", true),
  THIRD_PILLAR_START("nudge_third_pillar", true),
  THIRD_PILLAR_FEES("nudge_third_pillar", true),
  THIRD_PILLAR_RECURRING("nudge_third_pillar_recurring", true),
  THIRD_PILLAR_RAISE("nudge_third_pillar_raise", true),
  SAVINGS_FUND("nudge_savings_fund", false),
  SAVINGS_FUND_RECURRING("nudge_savings_fund_recurring", false),
  MEMBERSHIP("nudge_membership", false),
  NONE("nudge_none", false);

  private final String tag;
  private final boolean pillar;
}
