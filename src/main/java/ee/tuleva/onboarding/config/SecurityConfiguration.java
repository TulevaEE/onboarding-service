package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.auth.authority.Authority.*;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
// @EnableWebSecurity(debug = true)
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  @SneakyThrows
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthorizationFilter jwtAuthorizationFilter) {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/",
                        "/actuator/health",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/authenticate",
                        "/oauth/token",
                        "/oauth/refresh-token",
                        "/idLogin",
                        "/notifications/payments",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        GET, "/v1/me/capital", "/v1/me/capital/events", "/v1/capital/total")
                    .hasAuthority(MEMBER)
                    .requestMatchers("/v1/listings/**")
                    .hasAuthority(MEMBER)
                    .requestMatchers("/v1/capital-transfer-contracts/**")
                    .hasAuthority(MEMBER)
                    .requestMatchers(GET, "/v1/funds")
                    .permitAll()
                    .requestMatchers(HEAD, "/v1/members")
                    .permitAll()
                    .requestMatchers(GET, "/v1/members/lookup")
                    .hasAuthority(MEMBER)
                    .requestMatchers(GET, "/v1/payments/success")
                    .permitAll()
                    .requestMatchers(GET, "/v1/payments/member-success")
                    .permitAll()
                    .requestMatchers(POST, "/v1/payments/notifications")
                    .permitAll()
                    .requestMatchers(GET, "/v1/pension-account-statement", "/v1/me")
                    .hasAnyAuthority(USER, PARTNER)
                    .requestMatchers("/v1/**")
                    .hasAuthority(USER)
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            management ->
                management.sessionCreationPolicy(IF_REQUIRED).sessionFixation().newSession())
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(antMatcher(GET, "/v1/logout"))
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(200)))
        .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public JwtAuthorizationFilter jwtAuthorizationFilter(
      JwtTokenUtil jwtTokenUtil, PrincipalService principalService) {
    return new JwtAuthorizationFilter(jwtTokenUtil, principalService);
  }
}
