package ee.tuleva.onboarding.taxwin;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tax-headroom")
public record TaxHeadroomProperties(
    String xRoadUrl,
    String clientId,
    String taxDataService,
    String consentService,
    @Nullable String purposeDeclarationId) {}
