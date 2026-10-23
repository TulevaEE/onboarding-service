package ee.tuleva.onboarding.investment.kid.performance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PastPerformanceProperties.class)
class PastPerformanceConfiguration {}
