package ee.tuleva.onboarding.nudge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentRateRedirectProperties.class)
class PaymentRateRedirectConfiguration {}
