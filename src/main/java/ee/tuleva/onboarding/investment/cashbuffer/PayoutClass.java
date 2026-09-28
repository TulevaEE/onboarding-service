package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;

enum PayoutClass {
  RECURRING,
  TAIL,
  CYCLE,
  UNRECOGNISED;

  static PayoutClass of(RegistrarPayoutReason reason) {
    return switch (reason) {
      case FUND_PENSION -> RECURRING;
      case ONE_OFF_WITHDRAWAL, INHERITANCE -> TAIL;
      case FUND_SWITCH, SWITCH_TO_PENSION_INVESTMENT_ACCOUNT, SECOND_PILLAR_EXIT -> CYCLE;
      case UNRECOGNISED -> UNRECOGNISED;
    };
  }
}
