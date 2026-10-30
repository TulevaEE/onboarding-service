package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.STANDARD_AFTER_AGREEMENT;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record ApplicableRate(
    String isin,
    long agreementId,
    @Nullable Long rateId,
    BigDecimal publishedOcf,
    BigDecimal netOcf,
    RateBasis basis,
    @Nullable String reason) {

  static ApplicableRate resolved(long agreementId, InstrumentRate rate) {
    return new ApplicableRate(
        rate.isin(),
        agreementId,
        rate.id(),
        rate.publishedOcf(),
        rate.netOcf(),
        rate.rateBasis(),
        rate.fallbackReason());
  }

  static ApplicableRate standardAfter(AgreementPeriod ended) {
    return new ApplicableRate(
        ended.isin(),
        ended.id(),
        null,
        ended.publishedOcf(),
        ended.publishedOcf(),
        STANDARD_AFTER_AGREEMENT,
        "its agreement ended on %s, so the published OCF applies after it"
            .formatted(ended.validTo()));
  }

  public boolean fellBackToThePublishedOcf() {
    return basis != AGREEMENT;
  }

  public boolean isTheStandardRateAfterAnAgreement() {
    return basis == STANDARD_AFTER_AGREEMENT;
  }
}
