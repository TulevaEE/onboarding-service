package ee.tuleva.onboarding.investment.transaction;

import java.util.Optional;

enum GatewayHold {
  NONE,
  PEVA_RAVA_CYCLE_ACTIVE,
  R45_ROWS_UNAVAILABLE;

  static GatewayHold whatHoldsBack(
      Optional<?> pevaRavaInAnActiveCycle, Optional<?> r45RowsOfTheFund) {
    if (pevaRavaInAnActiveCycle.isPresent()) {
      return PEVA_RAVA_CYCLE_ACTIVE;
    }
    return r45RowsOfTheFund.isEmpty() ? R45_ROWS_UNAVAILABLE : NONE;
  }
}
