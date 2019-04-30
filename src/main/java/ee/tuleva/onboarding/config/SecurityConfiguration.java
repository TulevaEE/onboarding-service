package ee.tuleva.onboarding.config;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static ee.tuleva.onboarding.config.OAuthConfiguration.ResourceServerPathConfiguration.RESOURCE_REQUEST_MATCHER_BEAN;

@EnableWebSecurity
@EnableOAuth2Client
@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Qualifier(RESOURCE_REQUEST_MATCHER_BEAN)
    final RequestMatcher resources;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        val nonResources = new NegatedRequestMatcher(resources);
        http.requestMatcher(nonResources)
            .authorizeRequests()
            .requestMatchers(EndpointRequest.to("health")).permitAll()
            .requestMatchers(EndpointRequest.toAnyEndpoint().excluding("health")).authenticated()
            .regexMatchers("/", "/health", "/swagger-ui.html", "/authenticate", "/idLogin", "/oauth/token",
                "/notifications/payments").permitAll()
            .anyRequest().authenticated()
            .and().httpBasic().and()
            .csrf().ignoringAntMatchers("/authenticate", "/idLogin", "/oauth/token",
            "/notifications/payments");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = (DelegatingPasswordEncoder)
            PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
        return encoder;
    }
}
