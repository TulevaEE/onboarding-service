package ee.tuleva.onboarding.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
@EnableOAuth2Client
@Configuration
public class SecurityConfiguration {


}
