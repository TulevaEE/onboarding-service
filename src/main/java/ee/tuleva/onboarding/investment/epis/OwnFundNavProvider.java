package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OwnFundNavProvider {

  private static final BigDecimal MIN_REASONABLE_NAV = new BigDecimal("0.01");
  private static final BigDecimal MAX_REASONABLE_NAV = new BigDecimal("10.0");

  private final FundNavQueryService fundNavQueryService;

  BigDecimal latestNav(TulevaFund fund, LocalDate asOfDate) {
    BigDecimal nav =
        queryNav(fund, asOfDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "NAV not found for own fund: fund="
                            + fund.getCode()
                            + ", asOfDate="
                            + asOfDate));
    if (isOutsideReasonableRange(nav)) {
      throw new IllegalStateException(
          "NAV outside reasonable range: fund=" + fund.getCode() + ", nav=" + nav);
    }
    return nav;
  }

  Optional<BigDecimal> findLatestNav(TulevaFund fund, LocalDate asOfDate) {
    return queryNav(fund, asOfDate).filter(nav -> isReasonable(fund, asOfDate, nav));
  }

  private Optional<BigDecimal> queryNav(TulevaFund fund, LocalDate asOfDate) {
    return fundNavQueryService
        .findLatestNavDateOnOrBefore(fund.getCode(), asOfDate)
        .flatMap(navDate -> fundNavQueryService.findNavPerUnit(fund.getCode(), navDate));
  }

  private boolean isReasonable(TulevaFund fund, LocalDate asOfDate, BigDecimal nav) {
    if (isOutsideReasonableRange(nav)) {
      log.warn(
          "Ignoring own fund NAV outside reasonable range: fund={}, asOfDate={}, nav={}, min={},"
              + " max={}",
          fund.getCode(),
          asOfDate,
          nav.toPlainString(),
          MIN_REASONABLE_NAV.toPlainString(),
          MAX_REASONABLE_NAV.toPlainString());
      return false;
    }
    return true;
  }

  private static boolean isOutsideReasonableRange(BigDecimal nav) {
    return nav.compareTo(MIN_REASONABLE_NAV) < 0 || nav.compareTo(MAX_REASONABLE_NAV) > 0;
  }
}
