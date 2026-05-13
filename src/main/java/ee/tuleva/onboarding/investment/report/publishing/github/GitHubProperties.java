package ee.tuleva.onboarding.investment.report.publishing.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment-report-publishing.github")
record GitHubProperties(String token, String repo, String defaultBranch) {}
