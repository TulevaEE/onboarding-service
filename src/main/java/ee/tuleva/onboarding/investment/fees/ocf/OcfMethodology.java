package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.NullMarked;

/**
 * The method a snapshot was produced by, stored so that a row read years later describes itself.
 *
 * <p>CESR/10-674 p 1(c) requires calculations to be retained for five years. Every input is already
 * versioned or append-only, but a reader still cannot tell which denominator and which rebate basis
 * a given row was computed under unless the row says so.
 */
@NullMarked
public enum OcfMethodology {
  /**
   * Forward-looking, rate based. Components are expressed relative to net assets: the underlying
   * fund cost weighs each holding against the published NAV calculation's assets under management,
   * so the weights sum to the invested share rather than to one, and transaction costs divide by
   * average net assets over the trailing year's NAV calculation days.
   */
  EX_ANTE_NET_ASSETS_V1
}
