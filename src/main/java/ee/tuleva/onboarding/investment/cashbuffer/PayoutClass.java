package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ENFORCEMENT_ORDER;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_SWITCH;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.INHERITANCE;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.SECOND_PILLAR_EXIT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.SWITCH_TO_PENSION_INVESTMENT_ACCOUNT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.THIRD_PILLAR_REDEMPTION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.THIRD_PILLAR_SWITCH;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.TRANSFER_TO_INSURANCE;
import static java.util.Arrays.stream;

import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import java.util.Set;

enum PayoutClass {
  RECURRING(FUND_PENSION),
  TAIL(
      ONE_OFF_WITHDRAWAL,
      INHERITANCE,
      TRANSFER_TO_INSURANCE,
      THIRD_PILLAR_REDEMPTION,
      THIRD_PILLAR_SWITCH,
      ENFORCEMENT_ORDER),
  CYCLE(FUND_SWITCH, SWITCH_TO_PENSION_INVESTMENT_ACCOUNT, SECOND_PILLAR_EXIT),
  UNRECOGNISED(RegistrarPayoutReason.UNRECOGNISED);

  private final Set<RegistrarPayoutReason> reasons;

  PayoutClass(RegistrarPayoutReason... reasons) {
    this.reasons = Set.of(reasons);
  }

  boolean isOperating() {
    return this == RECURRING || this == TAIL;
  }

  static PayoutClass of(RegistrarPayoutReason reason) {
    return stream(values())
        .filter(payoutClass -> payoutClass.reasons.contains(reason))
        .findFirst()
        .orElseThrow();
  }
}
