package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.capital.CapitalController.CAPITAL_URI;
import static ee.tuleva.onboarding.config.ApiResourcesPathConfiguration.API_RESOURCES_REQUEST_MATCHER_BEAN;
import static org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.to;
import static org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint;

import ee.tuleva.onboarding.auth.AuthNotCompleteHandler;
import ee.tuleva.onboarding.auth.PersonalCodeAuthenticationProvider;
import ee.tuleva.onboarding.auth.PersonalCodeTokenIntrospector;
import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationServerConfiguration {

  @Qualifier(API_RESOURCES_REQUEST_MATCHER_BEAN)
  final RequestMatcher apiResources;

  final JdbcTemplate jdbcTemplate;

  final PrincipalService principalService;

  final ApplicationEventPublisher applicationEventPublisher;

  final OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

  final GrantedAuthorityFactory grantedAuthorityFactory;

  final List<AuthenticationConverter> authenticationConverters;

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
      throws Exception {
    OAuth2AuthorizationServerConfigurer<HttpSecurity> authorizationServerConfigurer =
        new OAuth2AuthorizationServerConfigurer<>();
    RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

    http.requestMatcher(endpointsMatcher)
        .authorizeRequests(authorizeRequests -> authorizeRequests.anyRequest().authenticated())
        .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
        .apply(authorizationServerConfigurer);

    authorizationServerConfigurer
        .authorizationService(oAuth2AuthorizationService())
        .tokenEndpoint(
            oAuth2TokenEndpointConfigurer ->
                oAuth2TokenEndpointConfigurer
                    .accessTokenRequestConverter(
                        new DelegatingAuthenticationConverter(authenticationConverters))
                    .errorResponseHandler(new AuthNotCompleteHandler())
                    .authenticationProvider(authenticationProvider()));

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    http.requestMatcher(apiResources)
        .authorizeRequests()
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
        .and()
        .csrf()
        .disable()
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.opaqueToken(
                    opaqueToken -> opaqueToken.introspector(personalCodeTokenIntrospector())))
        .oauth2Client();
    return http.build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    val nonResources = new NegatedRequestMatcher(apiResources);
    http.requestMatcher(nonResources)
        .authorizeRequests()
        .requestMatchers(to("health"))
        .permitAll()
        .requestMatchers(toAnyEndpoint().excluding("health"))
        .authenticated()
        .antMatchers(
            "/",
            "/swagger-ui/**",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/authenticate",
            "/idLogin",
            "/notifications/payments")
        .permitAll()
        .anyRequest()
        .authenticated()
        .and()
        .csrf()
        .ignoringAntMatchers("/authenticate", "/idLogin", "/notifications/payments");
    return http.build();
  }

  @Bean
  public OAuth2AuthorizationService oAuth2AuthorizationService() {
    return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository());
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    DelegatingPasswordEncoder encoder =
        (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
    encoder.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
    return encoder;
  }

  @Bean
  public RegisteredClientRepository registeredClientRepository() {
    return new JdbcRegisteredClientRepository(jdbcTemplate);
  }

  @Bean
  public PersonalCodeAuthenticationProvider authenticationProvider() {
    return new PersonalCodeAuthenticationProvider(
        oAuth2AuthorizationService(),
        registeredClientRepository(),
        applicationEventPublisher,
        oAuth2AuthorizedClientRepository,
        tokenGenerator());
  }

  @Bean
  public PersonalCodeTokenIntrospector personalCodeTokenIntrospector() {
    return new PersonalCodeTokenIntrospector(
        oAuth2AuthorizationService(),
        principalService,
        registeredClientRepository(),
        grantedAuthorityFactory);
  }

  private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator() {
    return new OAuth2AccessTokenGenerator();
  }

  @Bean
  public ProviderSettings providerSettings() {
    return ProviderSettings.builder().tokenEndpoint("/oauth/token").build();
  }
}
