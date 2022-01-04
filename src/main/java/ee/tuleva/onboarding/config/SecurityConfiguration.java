package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.config.OAuthConfiguration.ResourceServerPathConfiguration.RESOURCE_REQUEST_MATCHER_BEAN;

import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfiguration {

  @Configuration
  @Slf4j
  static class AuthenticationProviderConfiguration {

    private static final String NOOP_PASSWORD_PREFIX = "{noop}";

    private static final Pattern PASSWORD_ALGORITHM_PATTERN = Pattern.compile("^\\{.+}.*$");

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
        InMemoryUserDetailsManager userDetailsManager) {
      val provider = new DaoAuthenticationProvider();
      provider.setUserDetailsService(userDetailsManager);
      provider.setPasswordEncoder(passwordEncoder());
      return provider;
    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager(
        SecurityProperties properties, ObjectProvider<PasswordEncoder> passwordEncoder) {
      SecurityProperties.User user = properties.getUser();
      List<String> roles = user.getRoles();
      return new InMemoryUserDetailsManager(
          User.withUsername(user.getName())
              .password(getOrDeducePassword(user, passwordEncoder.getIfAvailable()))
              .roles(StringUtils.toStringArray(roles))
              .build());
    }

    private String getOrDeducePassword(SecurityProperties.User user, PasswordEncoder encoder) {
      String password = user.getPassword();
      if (user.isPasswordGenerated()) {
        log.info(String.format("%n%nUsing generated security password: %s%n", user.getPassword()));
      }
      if (encoder != null || PASSWORD_ALGORITHM_PATTERN.matcher(password).matches()) {
        return password;
      }
      return NOOP_PASSWORD_PREFIX + password;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      DelegatingPasswordEncoder encoder =
          (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
      encoder.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
      return encoder;
    }
  }

  @EnableWebSecurity
  @Configuration
  @RequiredArgsConstructor
  static class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Qualifier(RESOURCE_REQUEST_MATCHER_BEAN)
    final RequestMatcher resources;

    final DaoAuthenticationProvider authenticationProvider;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      val nonResources = new NegatedRequestMatcher(resources);
      http.requestMatcher(nonResources)
          .authorizeRequests()
          .requestMatchers(EndpointRequest.to("health"))
          .permitAll()
          .requestMatchers(EndpointRequest.toAnyEndpoint().excluding("health"))
          .authenticated()
          .antMatchers(
              "/",
              "/swagger-ui/**",
              "/webjars/**",
              "/swagger-resources/**",
              "/v3/api-docs",
              "/authenticate",
              "/idLogin",
              "/oauth/token",
              "/notifications/payments")
          .permitAll()
          .anyRequest()
          .authenticated()
          .and()
          .httpBasic()
          .and()
          .csrf()
          .ignoringAntMatchers(
              "/authenticate",
              "/idLogin",
              "/oauth/token",
              "/notifications/payments",
              "/actuator/**")
          .and()
          .authenticationProvider(authenticationProvider);
    }
  }
}
