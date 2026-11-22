package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties;
import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.investment.series.SplicedPriceSeries;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(SplicedPriceSeries.class)
class IndexOnlyKidSeries {

  @Bean
  RiskIndicatorProperties riskIndicatorProperties() {
    return new RiskIndicatorProperties(
        Map.of(TKF100, List.of(new SeriesSegment("MSCI_ACWI", null))));
  }
}
