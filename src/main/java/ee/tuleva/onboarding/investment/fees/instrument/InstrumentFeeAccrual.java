package ee.tuleva.onboarding.investment.fees.instrument;

import ee.tuleva.onboarding.investment.fees.rate.AccrualRate;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

record InstrumentFeeAccrual(
    TulevaFund fund,
    LocalDate accrualDate,
    AccrualRate rate,
    ProviderAgreementVersion providerAgreement,
    Holding holding,
    InstrumentPrice price) {

  BigDecimal holdingValue() {
    return holding.units().multiply(price.price());
  }

  BigDecimal rebateAmount() {
    return providerAgreement.method().dailyAmount(holdingValue(), rate.rebateRate(), accrualDate);
  }

  BigDecimal invoicedFeeAmount() {
    return providerAgreement
        .method()
        .dailyAmount(holdingValue(), rate.invoicedFeeRate(), accrualDate);
  }
}
