package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.tulevafund.TulevaFund

import java.time.LocalDate

class PublishedOngoingChargesFigureFixture {

  static PublishedOngoingChargesFigure withNoFigureInForce() {
    return new PublishedOngoingChargesFigure({ TulevaFund fund, LocalDate date -> Optional.empty() } as OngoingChargesFigures,
        TestClockHolder.clock)
  }

  static PublishedOngoingChargesFigure withFigureInForce(TulevaFund forFund, BigDecimal figure, int decimals) {
    return new PublishedOngoingChargesFigure(
        { TulevaFund fund, LocalDate date -> fund == forFund ? Optional.of(new PublishedFigure(figure, decimals)) : Optional.empty() } as OngoingChargesFigures,
        TestClockHolder.clock)
  }
}
