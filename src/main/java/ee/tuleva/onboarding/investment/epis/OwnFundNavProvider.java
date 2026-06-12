package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OwnFundNavProvider {

  private static final BigDecimal MIN_REASONABLE_NAV = new BigDecimal("0.01");
  private static final BigDecimal MAX_REASONABLE_NAV = new BigDecimal("10.0");

  private final FundNavQueryService fundNavQueryService;

  BigDecimal latestNav(TulevaFund fund, LocalDate asOfDate) {
    BigDecimal nav =
        findLatestNav(fund, asOfDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "NAV not found for own fund: fund="
                            + fund.getCode()
                            + ", asOfDate="
                            + asOfDate));
    if (nav.compareTo(MIN_REASONABLE_NAV) < 0 || nav.compareTo(MAX_REASONABLE_NAV) > 0) {
      throw new IllegalStateException(
          "NAV outside reasonable range: fund=" + fund.getCode() + ", nav=" + nav);
    }
    return nav;
  }

  Optional<BigDecimal> findLatestNav(TulevaFund fund, LocalDate asOfDate) {
    return fundNavQueryService
        .findLatestNavDateOnOrBefore(fund.getCode(), asOfDate)
        .flatMap(navDate -> fundNavQueryService.findNavPerUnit(fund.getCode(), navDate));
  }
}
