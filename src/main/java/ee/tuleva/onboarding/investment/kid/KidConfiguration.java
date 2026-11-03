package ee.tuleva.onboarding.investment.kid;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KidConfiguration {

  @Bean
  KidFunds kidFunds() {
    return KidFunds.TULEVA_FUNDS;
  }
}
