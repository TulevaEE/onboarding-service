package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum OcfGap {
  MANAGEMENT_FEE_RATE_MISSING,
  DEPOT_FEE_RATE_MISSING,
  NO_PUBLISHED_NAV_CALCULATION,
  NAV_HAS_NO_POSITIVE_AUM,
  TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM
}
