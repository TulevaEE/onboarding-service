package ee.tuleva.onboarding.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ContactChangeProperties.class)
class ContactChangeConfiguration {}
