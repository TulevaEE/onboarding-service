package ee.tuleva.onboarding.swedbank;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swedbank-gateway")
public record SwedbankGatewayProperties(
    boolean enabled, String url, String clientId, String agreementId, Keystore keystore) {
  public record Keystore(String path, String password) {}
}
