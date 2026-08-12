package ee.tuleva.onboarding.investment.fees;

/** Where a {@link FeeRate} row takes its annual rate from. */
public enum FeeRateSource {
  /** The rate is {@link FeeRate#annualRate()}. */
  FIXED,
  /** The rate comes from the AUM tier table, resolved at the fee month. Depot fee only. */
  TIER
}
