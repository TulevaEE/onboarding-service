package ee.tuleva.onboarding.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

@EnableOAuth2Client
@ConditionalOnWebApplication
@Configuration
public class OAuth2ClientConfiguration {
}
