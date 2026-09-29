package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;

sealed interface OcfRunOutcome permits OcfRunOutcome.Computed, OcfRunOutcome.Failed {

  TulevaFund fund();

  YearMonth month();

  record Computed(TulevaFund fund, YearMonth month, OcfSnapshot snapshot, List<OcfGap> gaps)
      implements OcfRunOutcome {

    boolean incomplete() {
      return !gaps.isEmpty();
    }
  }

  record Failed(TulevaFund fund, YearMonth month, String reason) implements OcfRunOutcome {}
}
