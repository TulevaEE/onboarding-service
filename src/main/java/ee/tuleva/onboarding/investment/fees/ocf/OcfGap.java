package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.NullMarked;

/**
 * A component that fell back to zero because its input was missing, rather than because it was
 * genuinely zero. A snapshot carrying any of these is not complete.
 *
 * <p>An unrated holding is deliberately absent: it aborts the calculation outright rather than
 * landing here, so no snapshot claims a number that treated a holding as free.
 */
@NullMarked
public enum OcfGap {
  MANAGEMENT_FEE_RATE_MISSING,
  DEPOT_FEE_RATE_MISSING,
  NO_PUBLISHED_NAV_CALCULATION,
  NAV_HAS_NO_POSITIVE_AUM,
  TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM
}
