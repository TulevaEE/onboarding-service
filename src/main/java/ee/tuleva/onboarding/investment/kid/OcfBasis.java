package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.OcfRateSource.CALCULATION_UNROUNDED;
import static ee.tuleva.onboarding.investment.kid.OcfRateSource.PUBLISHED_FIGURE;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.investment.fees.ocf.KidFigure;
import ee.tuleva.onboarding.investment.fees.ocf.OcfSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

record OcfBasis(
    long publicationId,
    LocalDate validFrom,
    BigDecimal ongoingCharges,
    @Nullable CalculatedFrom calculatedFrom) {

  static OcfBasis given(KidFigure kid) {
    return new OcfBasis(kid.publicationId(), kid.validFrom(), kid.publishedOcf(), null);
  }

  static OcfBasis calculated(KidFigure kid, OcfSnapshot calculation) {
    return new OcfBasis(
        kid.publicationId(),
        kid.validFrom(),
        kid.publishedOcf(),
        new CalculatedFrom(
            requireNonNull(calculation.id(), "A published OCF calculation has an id"),
            YearMonth.from(calculation.snapshotMonth())));
  }

  @JsonProperty
  OcfRateSource rateSource() {
    return calculatedFrom == null ? PUBLISHED_FIGURE : CALCULATION_UNROUNDED;
  }

  record CalculatedFrom(long snapshotId, YearMonth month) {}
}
