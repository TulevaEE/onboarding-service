package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeKey.TaxClaim.NO_TAX_CLAIM;
import static ee.tuleva.onboarding.nudge.NudgeKey.TaxClaim.TAX_BENEFIT_AT_SOURCE;
import static ee.tuleva.onboarding.nudge.NudgeKey.TaxClaim.TAX_REFUND_ON_TAX_RETURN;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NudgeKey {
  SECOND_PILLAR_REJOIN("nudge_second_pillar_rejoin", TAX_BENEFIT_AT_SOURCE),
  SECOND_PILLAR_START("nudge_second_pillar_start", TAX_BENEFIT_AT_SOURCE),
  SECOND_PILLAR_TRANSFER("nudge_second_pillar", NO_TAX_CLAIM),
  SECOND_PILLAR_PAYMENT_RATE("nudge_payment_rate", TAX_BENEFIT_AT_SOURCE),
  THIRD_PILLAR_START("nudge_third_pillar", TAX_REFUND_ON_TAX_RETURN),
  THIRD_PILLAR_FEES("nudge_third_pillar", NO_TAX_CLAIM),
  THIRD_PILLAR_RECURRING("nudge_third_pillar_recurring", NO_TAX_CLAIM),
  THIRD_PILLAR_RAISE("nudge_third_pillar_raise", TAX_REFUND_ON_TAX_RETURN),
  SAVINGS_FUND("nudge_savings_fund", NO_TAX_CLAIM),
  SAVINGS_FUND_RECURRING("nudge_savings_fund_recurring", NO_TAX_CLAIM),
  MEMBERSHIP("nudge_membership", NO_TAX_CLAIM),
  NONE("nudge_none", NO_TAX_CLAIM);

  enum TaxClaim {
    NO_TAX_CLAIM,
    TAX_BENEFIT_AT_SOURCE,
    TAX_REFUND_ON_TAX_RETURN
  }

  @Getter private final String tag;
  private final TaxClaim taxClaim;

  public boolean claimsTaxBenefit() {
    return taxClaim != NO_TAX_CLAIM;
  }

  public boolean claimsTaxRefund() {
    return taxClaim == TAX_REFUND_ON_TAX_RETURN;
  }
}
