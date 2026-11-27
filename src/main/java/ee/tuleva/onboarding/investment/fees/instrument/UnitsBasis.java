package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.AccrualMethod.DAILY_HOLDING;

enum UnitsBasis {
  TRADE_DATE,
  SETTLED;

  static UnitsBasis of(AccrualMethod method) {
    return method == DAILY_HOLDING ? TRADE_DATE : SETTLED;
  }
}
