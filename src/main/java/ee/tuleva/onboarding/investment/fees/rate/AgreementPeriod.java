package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record AgreementPeriod(
    long id,
    String isin,
    BigDecimal publishedOcf,
    LocalDate validFrom,
    @Nullable LocalDate validTo) {

  boolean inForceOn(LocalDate date) {
    return !validFrom.isAfter(date) && (validTo == null || !validTo.isBefore(date));
  }

  boolean endedBefore(LocalDate date) {
    return validTo != null && validTo.isBefore(date);
  }
}
