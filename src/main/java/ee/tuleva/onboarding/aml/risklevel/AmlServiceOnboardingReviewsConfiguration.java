package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlServiceConfiguration.amlServiceRestClient;
import static ee.tuleva.onboarding.aml.risklevel.AmlServiceConfiguration.amlServiceRetryTemplate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
class AmlServiceOnboardingReviewsConfiguration {

  @Bean
  @Profile({"!dev & !staging"})
  AmlServiceOnboardingReviews amlServiceOnboardingReviews(
      RestClient.Builder builder, AmlServiceProperties properties) {
    return new AmlServiceOnboardingReviews(
        amlServiceRestClient(builder, properties), amlServiceRetryTemplate(), properties);
  }
}
