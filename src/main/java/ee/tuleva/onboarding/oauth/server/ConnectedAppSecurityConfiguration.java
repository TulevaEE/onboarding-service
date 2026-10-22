package ee.tuleva.onboarding.oauth.server;

import static jakarta.servlet.DispatcherType.ERROR;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
class ConnectedAppSecurityConfiguration {

  private static final RequestMatcher RESOURCE_METADATA =
      PathPatternRequestMatcher.withDefaults()
          .matcher(GET, "/.well-known/oauth-protected-resource/**");

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 1)
  SecurityFilterChain connectedAppSecurityFilterChain(
      HttpSecurity http,
      SigningKeys signingKeys,
      AuthorizationServerSettings settings,
      Clock clock,
      ConnectedAppAuthenticator authenticator,
      ApplicationEventPublisher eventPublisher)
      throws Exception {
    var issuer = requireNonNull(settings.getIssuer());
    var tokens = new AppAccessTokens(signingKeys, issuer, clock);
    var tokenAuthentication = new JwtAuthenticationProvider(tokens);
    tokenAuthentication.setJwtAuthenticationConverter(authenticator::authenticate);
    http.securityMatcher(new OrRequestMatcher(AppAccessTokens::arePresentedBy, RESOURCE_METADATA))
        .authorizeHttpRequests(
            authorize -> {
              authorize.dispatcherTypeMatchers(ERROR).permitAll();
              AppEndpoint.OPEN_TO_APPS.forEach(
                  endpoint ->
                      authorize
                          .requestMatchers(endpoint.matcher())
                          .hasAuthority(endpoint.authority()));
              authorize.anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.authenticationManager(new ProviderManager(tokenAuthentication)))
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                resource ->
                                    resource
                                        .resource(AccessTokenClaims.audienceOf(issuer))
                                        .authorizationServer(issuer)
                                        .tlsClientCertificateBoundAccessTokens(false))))
        .addFilterAfter(new AppReadLog(eventPublisher), AuthorizationFilter.class)
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
