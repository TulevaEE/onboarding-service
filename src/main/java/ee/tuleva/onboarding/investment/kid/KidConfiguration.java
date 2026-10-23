package ee.tuleva.onboarding.investment.kid;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KidProperties.class)
class KidConfiguration {}
