package ee.tuleva.onboarding.nudge;

import java.util.EnumSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NudgeKey {
  SECOND_PILLAR_TRANSFER("nudge_second_pillar"),
  SECOND_PILLAR_PAYMENT_RATE("nudge_payment_rate"),
  THIRD_PILLAR_START("nudge_third_pillar"),
  THIRD_PILLAR_FEES("nudge_third_pillar"),
  THIRD_PILLAR_RECURRING("nudge_third_pillar_recurring"),
  THIRD_PILLAR_RAISE("nudge_third_pillar_raise"),
  SAVINGS_FUND("nudge_savings_fund"),
  SAVINGS_FUND_RECURRING("nudge_savings_fund_recurring"),
  MEMBERSHIP("nudge_membership"),
  NONE("nudge_none");

  private final String tag;

  public boolean isPillar() {
    return EnumSet.of(
            SECOND_PILLAR_TRANSFER,
            SECOND_PILLAR_PAYMENT_RATE,
            THIRD_PILLAR_START,
            THIRD_PILLAR_FEES,
            THIRD_PILLAR_RECURRING,
            THIRD_PILLAR_RAISE)
        .contains(this);
  }
}
