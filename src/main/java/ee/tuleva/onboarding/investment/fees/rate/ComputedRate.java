package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

record ComputedRate(
    String isin,
    YearMonth period,
    BigDecimal publishedOcf,
    BigDecimal rebateRate,
    BigDecimal invoicedFeeRate,
    RateBasis rateBasis,
    @Nullable String fallbackReason,
    RebateKind rebateKind,
    @Nullable BigDecimal volumeEur,
    @Nullable LocalDate volumeNavDate,
    @Nullable BigDecimal eurUsd,
    long instrumentFeeId) {

  BigDecimal netOcf() {
    return publishedOcf.subtract(rebateRate).add(invoicedFeeRate);
  }
}
