package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.NullMarked;

/**
 * Which underlying fund charge enters the total: the published one, or the one left after the
 * manager's rebate.
 *
 * <p>CESR/10-674 p 8(e) permits the netting only where the rebate is not already reflected in the
 * fund's profit and loss account. Ours is booked as fund income, so the question is open. Both
 * figures are stored on every snapshot; this says which one was used.
 */
@NullMarked
public enum RebateBasis {
  GROSS,
  NET
}
