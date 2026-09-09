package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "investment-report-publishing.wordpress")
record WordPressProperties(
    @NotBlank String apiBase, @NotBlank String username, @NotBlank String appPassword) {}
