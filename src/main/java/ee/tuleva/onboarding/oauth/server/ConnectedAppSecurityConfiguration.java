package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.config.SecurityFilterChainOrder.CONNECTED_APPS;
import static jakarta.servlet.DispatcherType.ERROR;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.net.URI;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
class ConnectedAppSecurityConfiguration {

  private static final String RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";
  private static final RequestMatcher RESOURCE_METADATA =
      PathPatternRequestMatcher.withDefaults().matcher(GET, RESOURCE_METADATA_PATH + "/**");

  @Bean
  @Order(CONNECTED_APPS)
  SecurityFilterChain connectedAppSecurityFilterChain(
      HttpSecurity http,
      SigningKeys signingKeys,
      AuthorizationServerSettings settings,
      Clock clock,
      ConnectedAppAuthenticator authenticator,
      ApplicationEventPublisher eventPublisher)
      throws Exception {
    var issuer = requireNonNull(settings.getIssuer());
    var api = AccessTokenClaims.audienceOf(issuer);
    var tokens = new AppAccessTokens(signingKeys, issuer, clock);
    var tokenAuthentication = new JwtAuthenticationProvider(tokens);
    tokenAuthentication.setJwtAuthenticationConverter(authenticator::authenticate);
    http.securityMatcher(new OrRequestMatcher(AppAccessTokens::arePresentedBy, RESOURCE_METADATA))
        .authorizeHttpRequests(
            authorize -> {
              authorize.dispatcherTypeMatchers(ERROR).permitAll();
              AppEndpoint.OPEN_TO_APPS.forEach(endpoint -> endpoint.openIn(authorize));
              authorize.anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .authenticationEntryPoint(challengePointingToTheMetadataOf(api))
                    .jwt(jwt -> jwt.authenticationManager(new ProviderManager(tokenAuthentication)))
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                resource ->
                                    resource
                                        .resource(api)
                                        .authorizationServer(issuer)
                                        .tlsClientCertificateBoundAccessTokens(false))))
        .addFilterAfter(new AppReadLog(eventPublisher), AuthorizationFilter.class)
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private static AuthenticationEntryPoint challengePointingToTheMetadataOf(String resource) {
    var metadata = metadataUrlOf(URI.create(resource));
    var entryPoint = new BearerTokenAuthenticationEntryPoint();
    entryPoint.setResourceMetadataParameterResolver(request -> metadata);
    return entryPoint;
  }

  private static String metadataUrlOf(URI resource) {
    return UriComponentsBuilder.fromUri(resource)
        .replacePath(RESOURCE_METADATA_PATH + resource.getPath())
        .toUriString();
  }
}
