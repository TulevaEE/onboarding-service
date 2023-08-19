package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.capital.CapitalController.CAPITAL_URI;
import static org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.to;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthorizationFilter jwtAuthorizationFilter) throws Exception {
    http.authorizeRequests()
        .requestMatchers(to("health"))
        .permitAll()
        .antMatchers(
            "/",
            "/swagger-ui/**",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/authenticate",
            "/oauth/token",
            "/idLogin",
            "/notifications/payments")
        .permitAll()
        .regexMatchers("/v1" + CAPITAL_URI)
        .hasAuthority(Authority.MEMBER)
        .regexMatchers(HttpMethod.GET, "/v1/funds.*")
        .permitAll()
        .regexMatchers(HttpMethod.HEAD, "/v1/members")
        .permitAll()
        .regexMatchers(HttpMethod.GET, "/v1/payments/success.*")
        .permitAll()
        .regexMatchers(HttpMethod.HEAD, "/v1/payments/notifications.*")
        .permitAll()
        .regexMatchers("/v1/.*")
        .hasAuthority(Authority.USER)
        .anyRequest()
        .authenticated()
        .and()
        .csrf()
        .ignoringAntMatchers(
            "/authenticate", "/oauth/token", "/idLogin", "/notifications/payments", "/v1/**")
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.NEVER)
        .and()
        .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
