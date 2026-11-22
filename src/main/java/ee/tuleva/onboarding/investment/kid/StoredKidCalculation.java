package ee.tuleva.onboarding.investment.kid;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

record StoredKidCalculation(
    long id,
    TulevaFund fund,
    YearMonth month,
    int version,
    List<String> refusalReasons,
    @Nullable String seriesSegments,
    @Nullable LocalDate lastPriceDate,
    @Nullable OcfBasis ocf,
    TransactionCostSource transactionCostSource,
    Instant calculatedAt) {

  @JsonProperty
  boolean complete() {
    return refusalReasons.isEmpty();
  }
}
