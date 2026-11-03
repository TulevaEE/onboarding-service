package ee.tuleva.onboarding.oauth.server;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.PRIVATE_KEY_JWT;
import static org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT;
import static org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT_AUTH_METHODS_SUPPORTED;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadata;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
class AuthorizationServerConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      ConnectEntryPoint connectEntryPoint,
      PinnedClientKeys pinnedClientKeys,
      AuthorizationServerSettings settings)
      throws Exception {
    http.getSharedObject(AuthenticationManagerBuilder.class).parentAuthenticationManager(null);
    http.oauth2AuthorizationServer(
            authorizationServer -> {
              http.securityMatcher(authorizationServer.getEndpointsMatcher());
              authorizationServer.authorizationEndpoint(
                  authorizationEndpoint ->
                      authorizationEndpoint.authenticationProviders(
                          providers ->
                              providers.stream()
                                  .filter(
                                      OAuth2AuthorizationCodeRequestAuthenticationProvider.class
                                          ::isInstance)
                                  .map(
                                      OAuth2AuthorizationCodeRequestAuthenticationProvider.class
                                          ::cast)
                                  .forEach(
                                      provider ->
                                          provider.setAuthenticationValidator(
                                              AuthorizationRequestRules.VALIDATOR))));
              authorizationServer.authorizationServerMetadataEndpoint(
                  metadata ->
                      metadata.authorizationServerMetadataCustomizer(
                          AuthorizationServerConfiguration::advertiseOnlyWhatClientsMayUse));
              authorizationServer.clientAuthentication(
                  clientAuthentication ->
                      clientAuthentication.authenticationProviders(
                          providers ->
                              providers.stream()
                                  .filter(
                                      JwtClientAssertionAuthenticationProvider.class::isInstance)
                                  .map(JwtClientAssertionAuthenticationProvider.class::cast)
                                  .forEach(
                                      provider ->
                                          provider.setJwtDecoderFactory(pinnedClientKeys))));
            })
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(settings.getTokenIntrospectionEndpoint())
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    connectEntryPoint,
                    PathPatternRequestMatcher.withDefaults()
                        .matcher(GET, settings.getAuthorizationEndpoint())));
    return http.build();
  }

  private static void advertiseOnlyWhatClientsMayUse(
      OAuth2AuthorizationServerMetadata.Builder metadata) {
    Consumer<List<String>> privateKeyJwtOnly = only(PRIVATE_KEY_JWT.getValue());
    metadata
        .grantTypes(only(AUTHORIZATION_CODE.getValue(), REFRESH_TOKEN.getValue()))
        .tokenEndpointAuthenticationMethods(privateKeyJwtOnly)
        .tokenRevocationEndpointAuthenticationMethods(privateKeyJwtOnly)
        .claims(
            claims -> {
              claims.remove(INTROSPECTION_ENDPOINT);
              claims.remove(INTROSPECTION_ENDPOINT_AUTH_METHODS_SUPPORTED);
            });
  }

  private static Consumer<List<String>> only(String... values) {
    return list -> {
      list.clear();
      list.addAll(List.of(values));
    };
  }
}
