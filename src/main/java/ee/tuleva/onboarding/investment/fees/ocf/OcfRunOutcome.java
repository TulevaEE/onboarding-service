package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

sealed interface OcfRunOutcome permits OcfRunOutcome.Computed, OcfRunOutcome.Failed {

  TulevaFund fund();

  YearMonth month();

  record Computed(
      TulevaFund fund,
      YearMonth month,
      OcfSnapshot snapshot,
      List<OcfGap> gaps,
      List<OcfHolding> holdings,
      @Nullable KidFigure kidInForce)
      implements OcfRunOutcome {

    boolean incomplete() {
      return !gaps.isEmpty();
    }

    boolean differsFromTheKid() {
      return !incomplete()
          && kidInForce != null
          && kidInForce.differsAtThePublishedPrecisionFrom(snapshot.totalOcf());
    }
  }

  record Failed(TulevaFund fund, YearMonth month, String reason) implements OcfRunOutcome {}
}
