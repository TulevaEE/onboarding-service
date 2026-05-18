package ee.tuleva.onboarding.investment.transaction.ingest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
class AlertConfiguration {}
