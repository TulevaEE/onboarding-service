package ee.tuleva.onboarding.investment.risk;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RiskIndicatorProperties.class)
class RiskIndicatorConfiguration {}
