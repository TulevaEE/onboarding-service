package ee.tuleva.onboarding.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartid")
public record SmartIdProperties(
    String relyingPartyUUID,
    String relyingPartyName,
    String hostUrl,
    String schemeName,
    String callbackUrl,
    String trustedCaCertificates) {}
