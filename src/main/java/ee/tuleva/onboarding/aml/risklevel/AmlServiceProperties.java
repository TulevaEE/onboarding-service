package ee.tuleva.onboarding.aml.risklevel;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aml-service")
record AmlServiceProperties(
    String url,
    @Nullable String apiToken,
    Duration connectTimeout,
    Duration readTimeout,
    Duration highRiskConnectTimeout,
    Duration highRiskReadTimeout,
    Duration kycOnboardingConnectTimeout,
    Duration kycOnboardingReadTimeout) {

  Optional<String> configuredApiToken() {
    return Optional.ofNullable(apiToken).filter(token -> !token.isBlank());
  }
}
