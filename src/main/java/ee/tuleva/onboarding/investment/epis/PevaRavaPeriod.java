package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;

import ee.tuleva.onboarding.tulevafund.TulevaFund;

public record PevaRavaPeriod(
    PevaRavaPhase phase, PevaRavaCycle cycle, FundCycleTimeline tuk75, FundCycleTimeline tuk00) {

  public boolean countsFlowsOf(TulevaFund fund) {
    return switch (fund) {
      case TUK75, TUK00 -> phase != DONE && timelineFor(fund).dActive();
      default -> false;
    };
  }

  public FundCycleTimeline timelineFor(TulevaFund fund) {
    return switch (fund) {
      case TUK75 -> tuk75;
      case TUK00 -> tuk00;
      default -> throw new IllegalArgumentException("No PEVA/RAVA timeline for fund: fund=" + fund);
    };
  }
}
