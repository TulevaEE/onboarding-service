package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlServiceConfiguration.amlServiceRetryTemplate;
import static ee.tuleva.onboarding.aml.risklevel.AmlServiceConfiguration.restClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class AmlServiceKycCheckerConfiguration {

  @Bean
  AmlServiceKycChecker amlServiceKycChecker(
      RestClient.Builder builder, AmlServiceProperties properties) {
    return new AmlServiceKycChecker(
        kycOnboardingRestClient(builder, properties), amlServiceRetryTemplate(), properties);
  }

  static RestClient kycOnboardingRestClient(
      RestClient.Builder builder, AmlServiceProperties properties) {
    return restClient(
        builder,
        properties.url(),
        properties.kycOnboardingConnectTimeout(),
        properties.kycOnboardingReadTimeout());
  }
}
