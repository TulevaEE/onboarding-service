package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

record OcfRunOutcome(
    TulevaFund fund,
    YearMonth month,
    @Nullable OcfSnapshot snapshot,
    List<OcfGap> gaps,
    @Nullable String failureReason) {

  static OcfRunOutcome computed(
      TulevaFund fund, YearMonth month, OcfSnapshot snapshot, List<OcfGap> gaps) {
    return new OcfRunOutcome(fund, month, snapshot, gaps, null);
  }

  static OcfRunOutcome failed(TulevaFund fund, YearMonth month, String failureReason) {
    return new OcfRunOutcome(fund, month, null, List.of(), failureReason);
  }

  boolean failed() {
    return snapshot == null;
  }

  boolean incomplete() {
    return snapshot != null && !gaps.isEmpty();
  }
}
