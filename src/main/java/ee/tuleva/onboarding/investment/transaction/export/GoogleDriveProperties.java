package ee.tuleva.onboarding.investment.transaction.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google-drive")
public record GoogleDriveProperties(
    boolean enabled, String serviceAccountJson, String rootFolderId) {}
