package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Reads aggregated NAV values from nav_report. Tuleva-internal source of truth for fund-level
// NAV per unit. Used by tracking-difference checks so they don't depend on index_values, which
// is the channel for external feeds (PENSIONIKESKUS, MSCI, etc.) and lags by ~1 day for pillar 2.
@Service
@RequiredArgsConstructor
public class FundNavQueryService {

  // Matches NavReportMapper.navRow which writes account_type='NAV' on every published calculation.
  private static final String NAV_ACCOUNT_TYPE = "NAV";

  private final NavReportRepository navReportRepository;

  public Optional<BigDecimal> findNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository
        .findFirstByFundCodeAndNavDateAndAccountType(fundCode, navDate, NAV_ACCOUNT_TYPE)
        .map(NavReportRow::getMarketPrice);
  }

  public Optional<LocalDate> findLatestNavDateOnOrBefore(String fundCode, LocalDate asOfDate) {
    return navReportRepository.findLatestNavDateByFundAndAccountTypeOnOrBefore(
        fundCode, NAV_ACCOUNT_TYPE, asOfDate);
  }
}
