package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record InstrumentFeeAgreement(
    long id,
    String isin,
    BigDecimal publishedOcf,
    RebateKind rebateKind,
    String rebateTerms,
    BigDecimal invoicedFeeRate,
    @Nullable BigDecimal publishedManagementFee,
    LocalDate validFrom,
    @Nullable LocalDate validTo) {}
