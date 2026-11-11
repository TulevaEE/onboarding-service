package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlServiceConfiguration.restClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
class EffectiveRiskLevelConfiguration {

  @Bean
  @Profile({"!dev & !staging"})
  EffectiveRiskLevelClient effectiveRiskLevelClient(AmlServiceProperties properties) {
    return new EffectiveRiskLevelClient(effectiveRiskLevelRestClient(properties), properties);
  }

  static RestClient effectiveRiskLevelRestClient(AmlServiceProperties properties) {
    return restClient(
        RestClient.builder(),
        properties.url(),
        properties.highRiskConnectTimeout(),
        properties.highRiskReadTimeout());
  }
}
