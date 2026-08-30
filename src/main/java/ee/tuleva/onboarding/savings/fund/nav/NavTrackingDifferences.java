package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;

public interface NavTrackingDifferences {

  Optional<NavTrackingDifference> findLatestModelPortfolio(TulevaFund fund, LocalDate navDate);

  Optional<NavTrackingDifference> findLatestBenchmarkModel(TulevaFund fund, LocalDate navDate);
}
