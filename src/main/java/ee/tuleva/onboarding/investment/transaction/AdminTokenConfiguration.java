package ee.tuleva.onboarding.investment.transaction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminTokenProperties.class)
class AdminTokenConfiguration {}
