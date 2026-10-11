package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;

record InstrumentFeeAgreement(
    long id,
    String isin,
    BigDecimal publishedOcf,
    RebateKind rebateKind,
    String rebateTerms,
    BigDecimal invoicedFeeRate) {}
