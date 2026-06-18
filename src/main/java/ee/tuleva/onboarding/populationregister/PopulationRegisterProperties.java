package ee.tuleva.onboarding.populationregister;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "population-register")
record PopulationRegisterProperties(String url, String clientId) {}
