package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.FundBalance

class ConversionHoldingFixture {

  static List<ConversionHolding> toConversionHoldings(List<FundBalance> fundBalances) {
    fundBalances.collect { toConversionHolding(it) }
  }

  static ConversionHolding toConversionHolding(FundBalance fundBalance) {
    new ConversionHolding(
        fundBalance.pillar,
        fundBalance.isin,
        fundBalance.ownFund,
        fundBalance.exitRestricted,
        fundBalance.activeContributions,
        fundBalance.totalValue,
        fundBalance.totalUnits,
        fundBalance.fee)
  }
}
