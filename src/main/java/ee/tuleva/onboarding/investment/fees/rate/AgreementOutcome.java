package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;

sealed interface AgreementOutcome {

  record Computed(BigDecimal rebateRate, BigDecimal invoicedFeeRate) implements AgreementOutcome {}

  record Uncomputable(String reason) implements AgreementOutcome {}
}
