package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.auth.authority.Authority.*;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;
import static org.springframework.security.authorization.AuthorizationManagers.not;
import static org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED;

import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
// @EnableWebSecurity(debug = true)
@EnableWebSecurity
public class SecurityConfiguration {

  private static final String[] MEMBER_PATHS = {
    "/v1/listings/**",
    "/v1/capital-transfer-contracts/**",
    "/v1/hackathon-registration/**",
    "/v1/hackathon-ideas/**"
  };

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
                        "/v1/emails/webhooks/**",
                        "/admin/**",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        POST, "/v1/payments/notifications", "/v1/payments/savings/notifications")
                    .permitAll()
                    .requestMatchers(GET, "/v1/gift-links/*")
                    .permitAll()
                    .requestMatchers(POST, "/v1/gift-links/*/payments")
                    .permitAll()
                    .requestMatchers(writesTo(MEMBER_PATHS))
                    .access(allOf(hasAuthority(MEMBER), not(hasAuthority(WARD))))
                    .requestMatchers(writesTo("/v1/**"))
                    .access(allOf(hasAuthority(USER), not(hasAuthority(WARD))))
                    .requestMatchers(
                        GET, "/v1/me/capital", "/v1/me/capital/events", "/v1/capital/total")
                    .hasAuthority(MEMBER)
                    .requestMatchers(MEMBER_PATHS)
                    .hasAuthority(MEMBER)
                    .requestMatchers(GET, "/v1/funds")
                    .permitAll()
                    .requestMatchers(GET, "/v1/funds/nav")
                    .permitAll()
                    .requestMatchers(GET, "/v1/funds/*/nav")
                    .permitAll()
                    .requestMatchers(GET, "/v1/benchmarks/world-market/returns")
                    .permitAll()
                    .requestMatchers(HEAD, "/v1/members")
                    .permitAll()
                    .requestMatchers(GET, "/v1/statistics/investor-count")
                    .permitAll()
                    .requestMatchers(GET, "/v1/members/lookup")
                    .hasAuthority(MEMBER)
                    .requestMatchers(GET, "/v1/payments/success")
                    .permitAll()
                    .requestMatchers(GET, "/v1/payments/member-success")
                    .permitAll()
                    .requestMatchers(GET, "/v1/payments/savings/callback")
                    .permitAll()
                    .requestMatchers(GET, "/v1/pension-account-statement", "/v1/me")
                    .hasAnyAuthority(USER, PARTNER)
                    .requestMatchers("/v1/savings-fund-test/**")
                    .hasAuthority(USER)
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
                    .logoutRequestMatcher(
                        PathPatternRequestMatcher.withDefaults().matcher(GET, "/v1/logout"))
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(200)))
        .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private static RequestMatcher writesTo(String... patterns) {
    return new OrRequestMatcher(
        Arrays.stream(patterns)
            .flatMap(
                pattern ->
                    Stream.of(POST, PUT, PATCH, DELETE)
                        .<RequestMatcher>map(
                            method ->
                                PathPatternRequestMatcher.withDefaults().matcher(method, pattern)))
            .toList());
  }

  @Bean
  public JwtAuthorizationFilter jwtAuthorizationFilter(
      JwtTokenUtil jwtTokenUtil, PrincipalService principalService) {
    return new JwtAuthorizationFilter(jwtTokenUtil, principalService);
  }
}
