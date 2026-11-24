package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.PROVISIONAL;
import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.RESOLVED;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record AccrualRate(
    long instrumentFeeId,
    String isin,
    long providerAgreementId,
    RateStatus status,
    @Nullable Long instrumentFeeRateId,
    BigDecimal rebateRate,
    BigDecimal invoicedFeeRate) {

  static AccrualRate resolved(
      InstrumentFeeAgreement agreement, long providerAgreementId, ResolvedRate rate) {
    return new AccrualRate(
        agreement.id(),
        agreement.isin(),
        providerAgreementId,
        RESOLVED,
        rate.id(),
        rate.rebateRate(),
        rate.invoicedFeeRate());
  }

  static AccrualRate provisional(
      InstrumentFeeAgreement agreement, long providerAgreementId, ComputedRate rate) {
    return new AccrualRate(
        agreement.id(),
        agreement.isin(),
        providerAgreementId,
        PROVISIONAL,
        null,
        rate.rebateRate(),
        rate.invoicedFeeRate());
  }
}
