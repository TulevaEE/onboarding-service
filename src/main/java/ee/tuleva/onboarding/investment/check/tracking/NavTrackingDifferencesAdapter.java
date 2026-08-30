package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.fund.nav.NavTrackingDifference;
import ee.tuleva.onboarding.savings.fund.nav.NavTrackingDifferences;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavTrackingDifferencesAdapter implements NavTrackingDifferences {

  private final TrackingDifferenceQueryService trackingDifferenceQueryService;

  @Override
  public Optional<NavTrackingDifference> findLatestModelPortfolio(
      TulevaFund fund, LocalDate navDate) {
    return trackingDifferenceQueryService
        .findLatestModelPortfolio(fund, navDate)
        .map(summary -> new NavTrackingDifference(summary.trackingDifference(), summary.limit()));
  }

  @Override
  public Optional<NavTrackingDifference> findLatestBenchmarkModel(
      TulevaFund fund, LocalDate navDate) {
    return trackingDifferenceQueryService
        .findLatestBenchmarkModel(fund, navDate)
        .map(summary -> new NavTrackingDifference(summary.trackingDifference(), summary.limit()));
  }
}
