package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;

sealed interface OcfRunOutcome permits OcfRunOutcome.Computed, OcfRunOutcome.Failed {

  TulevaFund fund();

  YearMonth month();

  record Computed(
      TulevaFund fund,
      YearMonth month,
      OcfSnapshot snapshot,
      List<OcfGap> gaps,
      List<OcfHolding> holdings,
      KidComparison kid)
      implements OcfRunOutcome {

    boolean incomplete() {
      return !gaps.isEmpty();
    }

    boolean differsFromTheKid() {
      return !incomplete() && kid.differsFrom(snapshot.totalOcf());
    }

    boolean awaitsAKidReview() {
      return differsFromTheKid() && kid.keptOnReview() == null;
    }
  }

  record Failed(TulevaFund fund, YearMonth month, String reason) implements OcfRunOutcome {}
}
