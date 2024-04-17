package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.capital.CapitalController.CAPITAL_URI;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;
import static org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity(debug = true)
public class SecurityConfiguration {

  @Bean
  @SneakyThrows
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthorizationFilter jwtAuthorizationFilter) {
    http.csrf()
        .disable()
        .authorizeHttpRequests()
        .requestMatchers(
            antMatcher("/"),
            antMatcher("/actuator/health"),
            antMatcher("/swagger-ui/**"),
            antMatcher("/webjars/**"),
            antMatcher("/swagger-resources/**"),
            antMatcher("/v3/api-docs/**"),
            antMatcher("/authenticate"),
            antMatcher("/oauth/token"),
            antMatcher("/oauth/refresh-token"),
            antMatcher("/idLogin"),
            antMatcher("/notifications/payments"),
            antMatcher("/error"))
        .permitAll()
        .requestMatchers(regexMatcher("/v1" + CAPITAL_URI))
        .hasAuthority(Authority.MEMBER)
        .requestMatchers(regexMatcher("/v1/funds.*"))
        .permitAll()
        .requestMatchers(regexMatcher("/v1/members.*"))
        .permitAll()
        .requestMatchers(regexMatcher(GET, "/v1/payments/success.*"))
        .permitAll()
        .requestMatchers(regexMatcher(GET, "/v1/payments/member-success.*"))
        .permitAll()
        .requestMatchers(regexMatcher(GET, "/v1/payments/notifications.*"))
        .permitAll()
        .requestMatchers(regexMatcher("/v1/.*"))
        .hasAuthority(Authority.USER)
        .anyRequest()
        .authenticated()
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.NEVER)
        .sessionFixation()
        .newSession()
        .and()
        .logout()
        .logoutRequestMatcher(antMatcher(GET, "/v1/logout"))
        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(200))
        .and()
        .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
