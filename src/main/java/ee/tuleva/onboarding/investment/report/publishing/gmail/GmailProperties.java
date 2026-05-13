package ee.tuleva.onboarding.investment.report.publishing.gmail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment-report-publishing.gmail")
public record GmailProperties(
    String serviceAccountJson, String delegateUser, String to, String cc) {}
