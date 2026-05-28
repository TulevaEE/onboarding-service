package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment-report-publishing.wordpress")
record WordPressProperties(String apiBase, String username, String appPassword) {}
