package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.tulevafund.TulevaFund

import java.time.LocalDate

class PublishedManagementFeeFixture {

  static PublishedManagementFee withNoRateInForce() {
    return new PublishedManagementFee({ TulevaFund fund, LocalDate date -> Optional.empty() } as ManagementFeeRates,
        TestClockHolder.clock)
  }

  static PublishedManagementFee withRateInForce(TulevaFund forFund, BigDecimal rate) {
    return new PublishedManagementFee(
        { TulevaFund fund, LocalDate date -> fund == forFund ? Optional.of(rate) : Optional.empty() } as ManagementFeeRates,
        TestClockHolder.clock)
  }
}
