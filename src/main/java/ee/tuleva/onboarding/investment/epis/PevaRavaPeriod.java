package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;

public record PevaRavaPeriod(
    PevaRavaPhase phase, PevaRavaCycle cycle, FundCycleTimeline tuk75, FundCycleTimeline tuk00) {

  public FundCycleTimeline timelineFor(TulevaFund fund) {
    return switch (fund) {
      case TUK75 -> tuk75;
      case TUK00 -> tuk00;
      default -> throw new IllegalArgumentException("No PEVA/RAVA timeline for fund: fund=" + fund);
    };
  }
}
