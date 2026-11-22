package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import org.jspecify.annotations.Nullable;

record KidFundRun(
    TulevaFund fund,
    List<StoredKidCalculation> stored,
    List<KidFailedMonth> failedMonths,
    @Nullable String failedWith) {

  static KidFundRun ran(TulevaFund fund, List<StoredKidCalculation> stored) {
    return ran(fund, stored, List.of());
  }

  static KidFundRun ran(
      TulevaFund fund, List<StoredKidCalculation> stored, List<KidFailedMonth> failedMonths) {
    return new KidFundRun(fund, List.copyOf(stored), List.copyOf(failedMonths), null);
  }

  static KidFundRun failed(TulevaFund fund, RuntimeException cause) {
    return new KidFundRun(fund, List.of(), List.of(), cause.getClass().getSimpleName());
  }

  boolean storedOrFailedAMonth() {
    return !stored.isEmpty() || !failedMonths.isEmpty();
  }
}
