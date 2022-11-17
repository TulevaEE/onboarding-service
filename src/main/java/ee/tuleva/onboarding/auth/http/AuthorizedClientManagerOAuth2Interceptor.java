package ee.tuleva.onboarding.auth.http;

import static java.util.Objects.isNull;

import ee.tuleva.onboarding.auth.PersonalCodeAuthenticationProvider;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

@RequiredArgsConstructor
public class AuthorizedClientManagerOAuth2Interceptor implements ClientHttpRequestInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  private final OAuth2AuthorizedClientManager manager;
  private final ClientRegistration clientRegistration;

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var principal = SecurityContextHolder.getContext().getAuthentication();
    var authorizedClient = PersonalCodeAuthenticationProvider.getRequestAuthorizedClient();
    OAuth2AccessToken accessToken;
    if (authorizedClient.isPresent()) {
      accessToken =
          getTokenValue(
              OAuth2AuthorizeRequest.withAuthorizedClient(authorizedClient.get()), principal);
    } else {
      accessToken =
          getTokenValue(
              OAuth2AuthorizeRequest.withClientRegistrationId(
                  clientRegistration.getRegistrationId()),
              principal);
    }

    request
        .getHeaders()
        .add(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken.getTokenValue());
    return execution.execute(request, body);
  }

  private OAuth2AccessToken getTokenValue(
      OAuth2AuthorizeRequest.Builder builder, Authentication principal) {
    var oAuth2AuthorizeRequest = builder.principal(principal).build();

    var client = manager.authorize(oAuth2AuthorizeRequest);
    if (isNull(client)) {
      throw new IllegalStateException(
          "client credentials flow on "
              + clientRegistration.getRegistrationId()
              + " failed, client is null");
    }
    return client.getAccessToken();
  }
}
