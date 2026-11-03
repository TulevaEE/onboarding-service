package ee.tuleva.onboarding.investment.kid.performance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PastPerformanceConfiguration {

  @Bean
  FundBenchmarks fundBenchmarks() {
    return FundBenchmarks.TULEVA_FUNDS;
  }
}
