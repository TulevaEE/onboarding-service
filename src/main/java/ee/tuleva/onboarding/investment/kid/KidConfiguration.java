package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KidConfiguration {

  @Bean
  KidFunds kidFunds(RiskIndicatorProperties riskIndicatorSeries) {
    KidFunds.TULEVA_FUNDS
        .funds()
        .keySet()
        .forEach(fund -> OwnNavMonthEnds.of(fund, riskIndicatorSeries.sourcesFor(fund)));
    return KidFunds.TULEVA_FUNDS;
  }
}
