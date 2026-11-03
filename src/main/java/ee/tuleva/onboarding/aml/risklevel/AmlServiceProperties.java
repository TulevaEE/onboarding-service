package ee.tuleva.onboarding.aml.risklevel;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aml-service")
record AmlServiceProperties(
    boolean enabled,
    String url,
    @Nullable String apiToken,
    Duration connectTimeout,
    Duration readTimeout) {

  Optional<String> configuredApiToken() {
    return Optional.ofNullable(apiToken).filter(token -> !token.isBlank());
  }
}
