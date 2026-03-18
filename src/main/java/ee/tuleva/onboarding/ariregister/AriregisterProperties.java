package ee.tuleva.onboarding.ariregister;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ariregister")
record AriregisterProperties(String url, String username, String password) {}
