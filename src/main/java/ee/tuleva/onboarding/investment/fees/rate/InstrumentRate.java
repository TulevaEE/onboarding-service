package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED_NET;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

public record InstrumentRate(
    long id,
    String isin,
    YearMonth period,
    BigDecimal publishedOcf,
    BigDecimal netOcf,
    RateBasis rateBasis,
    @Nullable String fallbackReason,
    RebateKind rebateKind) {

  public boolean fellBackToThePublishedOcf() {
    return rateBasis == PUBLISHED_FALLBACK;
  }

  public boolean agreedNetAboveThePublishedOcf() {
    return !fellBackToThePublishedOcf()
        && rebateKind == FIXED_NET
        && netOcf.compareTo(publishedOcf) > 0;
  }
}
