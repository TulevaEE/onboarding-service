package ee.tuleva.onboarding.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

@EnableWebSecurity
@EnableOAuth2Client
@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .regexMatchers("/", "/health", "/swagger-ui.html", "/authenticate", "/idLogin", "/oauth/token",
                        "/notifications/payments").permitAll()
                .anyRequest().authenticated()
                .and()
                .csrf().ignoringAntMatchers("/v1/**", "/authenticate", "/idLogin", "/oauth/token",
                "/notifications/payments");
    }
}
