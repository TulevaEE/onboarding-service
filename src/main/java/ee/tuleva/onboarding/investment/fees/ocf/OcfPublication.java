package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record OcfPublication(
    LocalDate validFrom,
    BigDecimal publishedOcf,
    BigDecimal managementFeeRate,
    long managementFeeRateId,
    String publishedIn,
    String publishedBy,
    @Nullable String approvedBy,
    @Nullable String exceptionReason) {}
