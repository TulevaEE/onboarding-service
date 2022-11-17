package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.ProviderContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RequiredArgsConstructor
public class PersonalCodeAuthenticationProvider implements AuthenticationProvider {

  private static final String REQUEST_AUTHORIZED_CLIENT =
      PersonalCodeAuthenticationProvider.class.getName() + ".AUTHORIZED_CLIENT";

  private final OAuth2AuthorizationService authorizationService;

  private final RegisteredClientRepository registeredClientRepository;

  private final ApplicationEventPublisher applicationEventPublisher;

  private final OAuth2AuthorizedClientRepository authorizedClientRepository;

  private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    var personalCodeAuthentication = (PersonalCodeAuthentication) authentication;

    var clientPrincipal =
        (OAuth2ClientAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

    RegisteredClient registeredClient =
        this.registeredClientRepository.findByClientId(personalCodeAuthentication.getClientId());
    if (registeredClient == null) {
      throwError(OAuth2ErrorCodes.INVALID_REQUEST);
    }

    if (!registeredClient
        .getAuthorizationGrantTypes()
        .contains(personalCodeAuthentication.getAuthorizationGrantType())) {
      throwError(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
    }

    OAuth2AccessToken accessToken =
        getOAuth2AccessToken(personalCodeAuthentication, registeredClient);

    var authorizationBuilder =
        authorizationBuilder(
                registeredClient,
                personalCodeAuthentication.getPrincipal().getPersonalCode(),
                personalCodeAuthentication.getAuthorizationGrantType())
            .attribute(
                OAuth2Authorization.AUTHORIZED_SCOPE_ATTRIBUTE_NAME, registeredClient.getScopes())
            .attribute(
                AuthenticationAttributes.AUTHENTICATION_ATTRIBUTES_KEY,
                personalCodeAuthentication.getAttributes())
            .attribute("authorities", personalCodeAuthentication.getAuthorities());

    authorizationBuilder.accessToken(accessToken);

    applicationEventPublisher.publishEvent(
        new BeforeTokenGrantedEvent(
            this,
            personalCodeAuthentication.getPrincipal(),
            personalCodeAuthentication.getAuthenticationAttributes(),
            personalCodeAuthentication.getAuthorizationGrantType()));

    var authorization = authorizationBuilder.build();

    authorizationService.save(authorization);

    personalCodeAuthentication.setAuthenticated(true);

    createAuthorizedClient(authorization, registeredClient, personalCodeAuthentication);

    applicationEventPublisher.publishEvent(
        new AfterTokenGrantedEvent(
            this,
            personalCodeAuthentication.getPrincipal(),
            personalCodeAuthentication.getAuthenticationAttributes(),
            personalCodeAuthentication.getAuthorizationGrantType()));

    return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken);
  }

  @NotNull
  private OAuth2AccessToken getOAuth2AccessToken(
      PersonalCodeAuthentication personalCodeAuthentication, RegisteredClient registeredClient) {
    DefaultOAuth2TokenContext.Builder tokenContextBuilder =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(personalCodeAuthentication)
            .providerContext(ProviderContextHolder.getProviderContext())
            .authorizedScopes(registeredClient.getScopes())
            .authorizationGrantType(personalCodeAuthentication.getAuthorizationGrantType());

    OAuth2TokenContext tokenContext =
        tokenContextBuilder
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(personalCodeAuthentication.getAuthorizationGrantType())
            .registeredClient(registeredClient)
            .providerContext(ProviderContextHolder.getProviderContext())
            .build();
    OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);

    if (generatedAccessToken == null) {
      OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR);
      throw new OAuth2AuthenticationException(
          error, "The token generator failed to generate the access token.");
    }

    return new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        generatedAccessToken.getTokenValue(),
        generatedAccessToken.getIssuedAt(),
        generatedAccessToken.getExpiresAt(),
        tokenContext.getAuthorizedScopes());
  }

  private void createAuthorizedClient(
      OAuth2Authorization authorization,
      RegisteredClient registeredClient,
      PersonalCodeAuthentication personalCodeAuthentication) {
    var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    var existingClient =
        authorizedClientRepository.loadAuthorizedClient(
            registeredClient.getId(), personalCodeAuthentication, requestAttributes.getRequest());
    if (existingClient == null) {
      ClientRegistration clientRegistration =
          ClientRegistration.withRegistrationId(registeredClient.getClientId())
              .clientId(registeredClient.getClientId())
              .clientName(registeredClient.getClientName())
              .clientSecret(registeredClient.getClientSecret())
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(personalCodeAuthentication.getAuthorizationGrantType())
              .build();

      OAuth2AuthorizedClient authorizedClient =
          new OAuth2AuthorizedClient(
              clientRegistration,
              authorization.getPrincipalName(),
              authorization.getAccessToken().getToken(),
              null);

      authorizedClientRepository.saveAuthorizedClient(
          authorizedClient,
          personalCodeAuthentication,
          requestAttributes.getRequest(),
          requestAttributes.getResponse());

      setRequestAuthorizedClient(authorizedClient);
      return;
    }
    setRequestAuthorizedClient(existingClient);
  }

  /** We only need it to call Epis in Before/After token events. */
  public static void setRequestAuthorizedClient(OAuth2AuthorizedClient oAuth2AuthorizedClient) {
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    requestAttributes.setAttribute(
        REQUEST_AUTHORIZED_CLIENT, oAuth2AuthorizedClient, RequestAttributes.SCOPE_REQUEST);
  }

  public static Optional<OAuth2AuthorizedClient> getRequestAuthorizedClient() {
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes != null) {
      return Optional.ofNullable(
          (OAuth2AuthorizedClient)
              requestAttributes.getAttribute(
                  REQUEST_AUTHORIZED_CLIENT, RequestAttributes.SCOPE_REQUEST));
    }
    return Optional.empty();
  }

  private static OAuth2Authorization.Builder authorizationBuilder(
      RegisteredClient registeredClient,
      String principalName,
      AuthorizationGrantType authorizationGrantType) {
    return OAuth2Authorization.withRegisteredClient(registeredClient)
        .principalName(principalName)
        .authorizationGrantType(authorizationGrantType);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PersonalCodeAuthentication.class.isAssignableFrom(authentication);
  }

  private static void throwError(String errorCode) {
    throw new OAuth2AuthenticationException(errorCode);
  }
}
