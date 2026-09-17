package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.NullMarked;

/**
 * Cost categories CESR/10-674 p 5 keeps out of the ongoing charges figure.
 *
 * <p>They are absent from the total because the calculation never adds them, which is
 * indistinguishable from having forgotten them. Naming them on every snapshot turns their absence
 * into a claim somebody can check.
 *
 * <p>Transaction costs are the one p 5 exclusion deliberately missing from this list: IFS § 94 lg 3
 * requires them inside the figure for the mandatory pension funds, and every fund's own terms make
 * them payable from fund assets, so they are a component rather than an exclusion.
 */
@NullMarked
public enum OcfExclusion {
  ENTRY_CHARGES,
  EXIT_CHARGES,
  PERFORMANCE_FEES,
  BORROWING_INTEREST,
  DERIVATIVE_HOLDING_COSTS,
  SOFT_COMMISSIONS
}
