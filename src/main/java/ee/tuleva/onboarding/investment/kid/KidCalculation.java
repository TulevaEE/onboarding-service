package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record KidCalculation(
    TulevaFund fund,
    YearMonth month,
    @Nullable OcfBasis ocf,
    @Nullable KidFigures figures,
    List<String> refusalReasons) {

  KidCalculation {
    refusalReasons = List.copyOf(refusalReasons);
    if ((figures == null) == refusalReasons.isEmpty()) {
      throw new IllegalArgumentException(
          "A KID calculation has either figures or refusal reasons: fund=%s, month=%s"
              .formatted(fund, month));
    }
  }

  static KidCalculation complete(
      TulevaFund fund, YearMonth month, OcfBasis ocf, KidFigures figures) {
    return new KidCalculation(fund, month, ocf, figures, List.of());
  }

  static KidCalculation refused(
      TulevaFund fund, YearMonth month, OcfBasis ocf, List<String> refusalReasons) {
    return new KidCalculation(fund, month, ocf, null, refusalReasons);
  }

  static KidCalculation refused(TulevaFund fund, YearMonth month, List<String> refusalReasons) {
    return new KidCalculation(fund, month, null, null, refusalReasons);
  }

  boolean complete() {
    return figures != null;
  }

  boolean repeats(StoredKidCalculation stored) {
    return !complete()
        && !stored.complete()
        && stored.refusalReasons().equals(refusalReasons)
        && Objects.equals(stored.ocf(), ocf);
  }
}
